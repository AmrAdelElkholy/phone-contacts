package org.fossify.phone.extensions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.fossify.commons.extensions.isPackageInstalled
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.launchViewContactIntent
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.CONTACT_ID
import org.fossify.commons.helpers.FIRST_CONTACT_ID
import org.fossify.commons.helpers.IS_PRIVATE
import org.fossify.commons.helpers.ON_CLICK_CALL_CONTACT
import org.fossify.commons.helpers.ON_CLICK_VIEW_CONTACT
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity

fun SimpleActivity.handleGenericContactClick(contact: Contact) {
    when (config.onContactClick) {
        ON_CLICK_CALL_CONTACT -> startCallWithConfirmationCheck(contact)
        ON_CLICK_VIEW_CONTACT -> startContactDetailsIntent(contact)
    }
}

fun SimpleActivity.launchCreateNewContactIntent() {
    Intent().apply {
        action = Intent.ACTION_INSERT
        data = ContactsContract.Contacts.CONTENT_URI
        launchActivityIntent(this)
    }
}

// handle private contacts differently, only Simple Contacts Pro can open them
fun Activity.startContactDetailsIntent(contact: Contact) {
    val simpleContacts = "org.fossify.contacts"
    val simpleContactsDebug = "org.fossify.contacts.debug"
    val isPrivateContact = contact.rawId > FIRST_CONTACT_ID
            && contact.contactId > FIRST_CONTACT_ID
            && contact.rawId == contact.contactId
            && (isPackageInstalled(simpleContacts) || isPackageInstalled(simpleContactsDebug))
    if (isPrivateContact) {
        Intent().apply {
            action = Intent.ACTION_VIEW
            putExtra(CONTACT_ID, contact.rawId)
            putExtra(IS_PRIVATE, true)
            `package` =
                if (isPackageInstalled(simpleContacts)) simpleContacts else simpleContactsDebug
            setDataAndType(
                ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                "vnd.android.cursor.dir/person"
            )
            launchActivityIntent(this)
        }
    } else {
        ensureBackgroundThread {
            val lookupKey =
                SimpleContactsHelper(this).getContactLookupKey((contact).rawId.toString())
            val publicUri =
                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
            runOnUiThread {
                launchViewContactIntent(publicUri)
            }
        }
    }
}

fun Activity.openWhatsAppChat(phoneNumber: String, business: Boolean) {
    val packageName = if (business) "com.whatsapp.w4b" else "com.whatsapp"
    if (!isPackageInstalled(packageName)) {
        toast(if (business) R.string.whatsapp_business_not_installed else R.string.whatsapp_not_installed)
        return
    }

    val number = getWhatsAppNumber(phoneNumber)
    if (number == null) {
        toast(R.string.whatsapp_invalid_number)
        return
    }

    Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number")).apply {
        `package` = packageName
        launchActivityIntent(this)
    }
}

// WhatsApp expects the full international number, digits only
private fun Activity.getWhatsAppNumber(phoneNumber: String): String? {
    val telephonyManager = getSystemService(TelephonyManager::class.java)
    val region = (telephonyManager?.simCountryIso?.takeIf { it.isNotEmpty() }
        ?: telephonyManager?.networkCountryIso?.takeIf { it.isNotEmpty() }
        ?: resources.configuration.locales[0].country).uppercase()

    val phoneNumberUtil = PhoneNumberUtil.getInstance()
    val e164 = try {
        val parsed = phoneNumberUtil.parse(phoneNumber, region)
        if (phoneNumberUtil.isPossibleNumber(parsed)) {
            phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    val digits = (e164 ?: phoneNumber).filter { it.isDigit() }
    return digits.takeIf { it.length >= 7 }
}
