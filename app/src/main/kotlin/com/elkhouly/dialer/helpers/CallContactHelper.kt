package com.elkhouly.dialer.helpers

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.PhoneLookup
import android.telecom.Call
import com.elkhouly.commons.extensions.formatPhoneNumber
import com.elkhouly.commons.extensions.getMyContactsCursor
import com.elkhouly.commons.extensions.getPhoneNumberTypeText
import com.elkhouly.commons.extensions.getIntValue
import com.elkhouly.commons.extensions.getLongValue
import com.elkhouly.commons.extensions.getStringValue
import com.elkhouly.commons.extensions.hasPermission
import com.elkhouly.commons.helpers.PERMISSION_READ_CONTACTS
import com.elkhouly.commons.helpers.MyContactsContentProvider
import com.elkhouly.commons.helpers.ensureBackgroundThread
import com.elkhouly.dialer.R
import com.elkhouly.dialer.extensions.config
import com.elkhouly.dialer.extensions.isConference
import com.elkhouly.dialer.models.CallContact
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

fun getCallContact(context: Context, call: Call?, callback: (CallContact) -> Unit) {
    if (call.isConference()) {
        callback(CallContact(context.getString(R.string.conference), "", "", ""))
        return
    }

    val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
    ensureBackgroundThread {
        val callContact = CallContact("", "", "", "")
        val handle = try {
            call?.details?.handle?.toString()
        } catch (e: NullPointerException) {
            null
        }

        if (handle == null) {
            callback(callContact)
            return@ensureBackgroundThread
        }

        val uri = Uri.decode(handle)
        if (uri.startsWith("tel:")) {
            val number = uri.substringAfter("tel:")
            callContact.number = if (context.config.formatPhoneNumbers) {
                number.formatPhoneNumber()
            } else {
                number
            }

            // a direct lookup answers in milliseconds, loading every contact took seconds and
            // left the incoming call screen without a name meanwhile
            if (!lookupContact(context, number, callContact)) {
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
                val contact = privateContacts.firstOrNull { it.doesHavePhoneNumber(number) }
                if (contact != null) {
                    callContact.name = contact.getNameToDisplay()
                    callContact.photoUri = contact.photoUri
                } else {
                    // not a contact, show a known hotline name and what kind of number it is
                    val callerId = CallerIdHelper.identify(context, number)
                    callContact.name = callerId?.name ?: callContact.number
                    callContact.numberLabel = callerId?.details.orEmpty()
                }
            }

            callback(callContact)
        }
    }
}

private fun lookupContact(context: Context, number: String, callContact: CallContact): Boolean {
    if (number.isEmpty() || !context.hasPermission(PERMISSION_READ_CONTACTS)) {
        return false
    }

    // the system match is loose already, but phones set to strict matching miss a contact saved
    // without the country code when the call comes in international form, try the local forms too
    return getLookupCandidates(number).any { lookupContactByNumber(context, it, callContact) }
}

private fun getLookupCandidates(number: String): List<String> {
    val candidates = mutableListOf(number)
    if (number.startsWith("+") || number.startsWith("00")) {
        try {
            val util = PhoneNumberUtil.getInstance()
            val parsed = util.parse(if (number.startsWith("00")) "+${number.drop(2)}" else number, null)
            val national = util.getNationalSignificantNumber(parsed)
            candidates.add("0$national")
            candidates.add(national)
        } catch (_: NumberParseException) {
        }
    }
    return candidates.distinct()
}

private fun lookupContactByNumber(context: Context, number: String, callContact: CallContact): Boolean {
    val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    val projection = arrayOf(PhoneLookup._ID, PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_URI, PhoneLookup.TYPE, PhoneLookup.LABEL)
    try {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return false
            }

            val name = cursor.getStringValue(PhoneLookup.DISPLAY_NAME).orEmpty()
            if (name.isEmpty()) {
                return false
            }

            callContact.name = name
            callContact.photoUri = cursor.getStringValue(PhoneLookup.PHOTO_URI).orEmpty()

            // like before, the number type is only shown for contacts with several numbers
            val contactId = cursor.getLongValue(PhoneLookup._ID)
            if (getPhoneNumbersCount(context, contactId) > 1) {
                callContact.numberLabel = context.getPhoneNumberTypeText(
                    cursor.getIntValue(PhoneLookup.TYPE),
                    cursor.getStringValue(PhoneLookup.LABEL).orEmpty()
                )
            }
            return true
        }
    } catch (_: Exception) {
    }
    return false
}

private fun getPhoneNumbersCount(context: Context, contactId: Long): Int {
    val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
    val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
    return context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone._ID), selection, arrayOf(contactId.toString()), null)
        ?.use { it.count } ?: 0
}
