package com.elkhouly.contacts.dialogs

import androidx.appcompat.app.AlertDialog
import com.elkhouly.commons.extensions.getAlertDialogBuilder
import com.elkhouly.commons.extensions.getVisibleContactSources
import com.elkhouly.commons.extensions.setupDialogStuff
import com.elkhouly.commons.helpers.ContactsHelper
import com.elkhouly.commons.helpers.SMT_PRIVATE
import com.elkhouly.commons.models.contacts.Contact
import com.elkhouly.commons.models.contacts.ContactSource
import com.elkhouly.contacts.activities.SimpleActivity
import com.elkhouly.contacts.adapters.FilterContactSourcesAdapter
import com.elkhouly.contacts.databinding.ContactsDialogFilterContactSourcesBinding
import com.elkhouly.contacts.extensions.config

class FilterContactSourcesDialog(val activity: SimpleActivity, private val callback: () -> Unit) {
    private var dialog: AlertDialog? = null
    private val binding = ContactsDialogFilterContactSourcesBinding.inflate(activity.layoutInflater)
    private var contactSources = ArrayList<ContactSource>()
    private var contacts = ArrayList<Contact>()
    private var isContactSourcesReady = false
    private var isContactsReady = false

    init {
        ContactsHelper(activity).getContactSources { contactSources ->
            contactSources.mapTo(this@FilterContactSourcesDialog.contactSources) { it.copy() }
            isContactSourcesReady = true
            processDataIfReady()
        }

        ContactsHelper(activity).getContacts(getAll = true) { contacts ->
            contacts.mapTo(this@FilterContactSourcesDialog.contacts) { it.copy() }
            isContactsReady = true
            processDataIfReady()
        }
    }

    private fun processDataIfReady() {
        if (!isContactSourcesReady) {
            return
        }

        val contactSourcesWithCount = ArrayList<ContactSource>()
        for (contactSource in contactSources) {
            val count = if (isContactsReady) {
                contacts.filter { it.source == contactSource.name }.count()
            } else {
                -1
            }
            contactSourcesWithCount.add(contactSource.copy(count = count))
        }

        contactSources.clear()
        contactSources.addAll(contactSourcesWithCount)

        activity.runOnUiThread {
            val selectedSources = activity.getVisibleContactSources()
            binding.filterContactSourcesList.adapter = FilterContactSourcesAdapter(activity, contactSourcesWithCount, selectedSources)

            if (dialog == null) {
                activity.getAlertDialogBuilder()
                    .setPositiveButton(com.elkhouly.commons.R.string.ok) { dialogInterface, i -> confirmContactSources() }
                    .setNegativeButton(com.elkhouly.commons.R.string.cancel, null)
                    .apply {
                        activity.setupDialogStuff(binding.root, this) { alertDialog ->
                            dialog = alertDialog
                        }
                    }
            }
        }
    }

    private fun confirmContactSources() {
        val selectedContactSources = (binding.filterContactSourcesList.adapter as FilterContactSourcesAdapter).getSelectedContactSources()
        val ignoredContactSources = contactSources.filter { !selectedContactSources.contains(it) }.map {
            if (it.type == SMT_PRIVATE) SMT_PRIVATE else it.getFullIdentifier()
        }.toHashSet()

        if (activity.getVisibleContactSources() != ignoredContactSources) {
            activity.config.ignoredContactSources = ignoredContactSources
            callback()
        }
        dialog?.dismiss()
    }
}
