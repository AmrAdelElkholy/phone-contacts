package org.fossify.phone.extensions

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.helpers.ON_CLICK_CALL_CONTACT
import org.fossify.commons.helpers.ON_CLICK_VIEW_CONTACT
import org.fossify.commons.models.contacts.Contact
import org.fossify.contacts.extensions.viewContact
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
        `package` = packageName
        launchActivityIntent(this)
    }
}

fun Activity.startContactDetailsIntent(contact: Contact) {
    viewContact(contact)
}
