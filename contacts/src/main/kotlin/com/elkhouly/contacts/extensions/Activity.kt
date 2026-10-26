package com.elkhouly.contacts.extensions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.elkhouly.commons.activities.BaseSimpleActivity
import com.elkhouly.commons.dialogs.RadioGroupDialog
import com.elkhouly.commons.extensions.getFileOutputStream
import com.elkhouly.commons.extensions.getPublicContactSourceSync
import com.elkhouly.commons.extensions.getTempFile
import com.elkhouly.commons.extensions.getVisibleContactSources
import com.elkhouly.commons.extensions.hideKeyboard
import com.elkhouly.commons.extensions.launchActivityIntent
import com.elkhouly.commons.extensions.sharePathIntent
import com.elkhouly.commons.extensions.showErrorToast
import com.elkhouly.commons.extensions.toFileDirItem
import com.elkhouly.commons.extensions.toast
import com.elkhouly.commons.extensions.tryInitiateCall
import com.elkhouly.commons.helpers.CONTACT_ID
import com.elkhouly.commons.helpers.ContactsHelper
import com.elkhouly.commons.helpers.IS_PRIVATE
import com.elkhouly.commons.helpers.ON_CLICK_CALL_CONTACT
import com.elkhouly.commons.helpers.ON_CLICK_EDIT_CONTACT
import com.elkhouly.commons.helpers.ON_CLICK_VIEW_CONTACT
import com.elkhouly.commons.helpers.PERMISSION_CALL_PHONE
import com.elkhouly.commons.helpers.SMT_PRIVATE
import com.elkhouly.commons.helpers.ensureBackgroundThread
import com.elkhouly.commons.models.RadioItem
import com.elkhouly.commons.models.contacts.Contact
import com.elkhouly.contacts.BuildConfig
import com.elkhouly.contacts.R
import com.elkhouly.contacts.activities.EditContactActivity
import com.elkhouly.contacts.activities.SimpleActivity
import com.elkhouly.contacts.activities.ViewContactActivity
import com.elkhouly.contacts.dialogs.ImportContactsDialog
import com.elkhouly.contacts.helpers.DEFAULT_FILE_NAME
import com.elkhouly.contacts.helpers.VcfExporter

fun SimpleActivity.startCallIntent(recipient: String) {
    handlePermission(PERMISSION_CALL_PHONE) {
        val action = if (it) Intent.ACTION_CALL else Intent.ACTION_DIAL
        Intent(action).apply {
            data = Uri.fromParts("tel", recipient, null)
            launchActivityIntent(this)
        }
    }
}

fun SimpleActivity.showContactSourcePicker(currentSource: String, callback: (newSource: String) -> Unit) {
    ContactsHelper(this).getSaveableContactSources { sources ->
        val items = ArrayList<RadioItem>()
        var sourceNames = sources.map { it.name }
        var currentSourceIndex = sourceNames.indexOfFirst { it == currentSource }
        sourceNames = sources.map { it.publicName }

        sourceNames.forEachIndexed { index, account ->
            items.add(RadioItem(index, account))
            if (currentSource == SMT_PRIVATE && account == getString(R.string.phone_storage_hidden)) {
                currentSourceIndex = index
            }
        }

        runOnUiThread {
            RadioGroupDialog(this, items, currentSourceIndex) {
                callback(sources[it as Int].name)
            }
        }
    }
}

fun BaseSimpleActivity.shareContacts(contacts: ArrayList<Contact>) {
    val filename = if (contacts.size == 1) {
        "${contacts.first().getNameToDisplay()}.vcf"
    } else {
        DEFAULT_FILE_NAME
    }

    val file = getTempFile(filename)
    if (file == null) {
        toast(com.elkhouly.commons.R.string.unknown_error_occurred)
        return
    }

    getFileOutputStream(file.toFileDirItem(this), true) {
        VcfExporter().exportContacts(
            context = this,
            outputStream = it,
            contacts = contacts,
            showExportingToast = false
        ) {
            if (it == VcfExporter.ExportResult.EXPORT_OK) {
                sharePathIntent(file.absolutePath, packageName)
            } else {
                showErrorToast("$it")
            }
        }
    }
}

fun SimpleActivity.handleGenericContactClick(contact: Contact) {
    when (config.onContactClick) {
        ON_CLICK_CALL_CONTACT -> callContact(contact)
        ON_CLICK_VIEW_CONTACT -> viewContact(contact)
        ON_CLICK_EDIT_CONTACT -> editContact(contact, config.mergeDuplicateContacts)
    }
}

fun SimpleActivity.callContact(contact: Contact) {
    hideKeyboard()
    if (contact.phoneNumbers.isNotEmpty()) {
        tryInitiateCall(contact) { startCallIntent(it) }
    } else {
        toast(com.elkhouly.commons.R.string.no_phone_number_found)
    }
}

fun Activity.viewContact(contact: Contact) {
    hideKeyboard()
    Intent(applicationContext, ViewContactActivity::class.java).apply {
        putExtra(CONTACT_ID, contact.id)
        putExtra(IS_PRIVATE, contact.isPrivate())
        startActivity(this)
    }
}

fun Activity.editContact(contact: Contact, isMergedDuplicate: Boolean) {
    if (!isMergedDuplicate) {
        editContact(contact)
    } else {
        ContactsHelper(this).getContactSources { contactSources ->
            getDuplicateContacts(contact, true) { contacts ->
                if (contacts.size == 1) {
                    runOnUiThread {
                        editContact(contacts.first())
                    }
                } else {
                    val items = ArrayList(contacts.mapIndexed { index, contact ->
                        var source = getPublicContactSourceSync(contact.source, contactSources)
                        if (source == "") {
                            source = getString(R.string.phone_storage)
                        }
                        RadioItem(index, source, contact)
                    }.sortedBy { it.title })

                    runOnUiThread {
                        RadioGroupDialog(
                            activity = this,
                            items = items,
                            titleId = R.string.select_account,
                        ) {
                            editContact(it as Contact)
                        }
                    }
                }
            }
        }
    }
}

fun Activity.getDuplicateContacts(contact: Contact, includeCurrent: Boolean, callback: (duplicateContacts: ArrayList<Contact>) -> Unit) {
    val duplicateContacts = ArrayList<Contact>()
    if (includeCurrent) {
        duplicateContacts.add(contact)
    }
    ContactsHelper(this).getDuplicatesOfContact(contact, false) { contacts ->
        ensureBackgroundThread {
            val displayContactSources = getVisibleContactSources()
            contacts.filter { displayContactSources.contains(it.source) }.forEach {
                val duplicate = ContactsHelper(this).getContactWithId(it.id, it.isPrivate())
                if (duplicate != null) {
                    duplicateContacts.add(duplicate)
                }
            }
            callback(duplicateContacts)
        }
    }
}

fun Activity.editContact(contact: Contact) {
    hideKeyboard()
    Intent(applicationContext, EditContactActivity::class.java).apply {
        putExtra(CONTACT_ID, contact.id)
        putExtra(IS_PRIVATE, contact.isPrivate())
        startActivity(this)
    }
}

fun SimpleActivity.tryImportContactsFromFile(uri: Uri, callback: (Boolean) -> Unit) {
    when (uri.scheme) {
        "file" -> showImportContactsDialog(uri.path!!, callback)
        "content" -> {
            try {
                val tempFile = copyUriToTempFile(uri, "import-${System.currentTimeMillis()}-$DEFAULT_FILE_NAME")
                if (tempFile == null) {
                    toast(com.elkhouly.commons.R.string.unknown_error_occurred)
                    return
                }

                showImportContactsDialog(tempFile.absolutePath, callback)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }

        else -> toast(com.elkhouly.commons.R.string.invalid_file_format)
    }
}

fun SimpleActivity.showImportContactsDialog(path: String, callback: (Boolean) -> Unit) {
    ImportContactsDialog(this, path, callback)
}
