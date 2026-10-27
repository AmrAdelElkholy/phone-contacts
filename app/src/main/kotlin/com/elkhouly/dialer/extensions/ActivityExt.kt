package com.elkhouly.dialer.extensions

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import com.elkhouly.commons.extensions.launchActivityIntent
import com.elkhouly.commons.helpers.ON_CLICK_CALL_CONTACT
import com.elkhouly.commons.helpers.ON_CLICK_VIEW_CONTACT
import com.elkhouly.commons.models.contacts.Contact
import com.elkhouly.contacts.extensions.viewContact
import com.elkhouly.dialer.activities.SimpleActivity

fun SimpleActivity.handleGenericContactClick(contact: Contact) {
    when (config.onContactClick) {
        ON_CLICK_CALL_CONTACT -> startCallWithConfirmationCheck(contact)
        ON_CLICK_VIEW_CONTACT -> startContactDetailsIntent(contact)
    }
}

fun SimpleActivity.launchCreateNewContactIntent(phoneNumber: String = "") {
    Intent().apply {
        action = Intent.ACTION_INSERT
        data = ContactsContract.Contacts.CONTENT_URI
        `package` = packageName
        if (phoneNumber.isNotEmpty()) {
            putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
        }
        launchActivityIntent(this)
    }
}

fun Activity.startContactDetailsIntent(contact: Contact) {
    viewContact(contact)
}
