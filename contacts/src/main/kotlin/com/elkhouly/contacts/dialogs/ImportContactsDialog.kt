package com.elkhouly.contacts.dialogs

import androidx.appcompat.app.AlertDialog
import com.elkhouly.commons.extensions.getAlertDialogBuilder
import com.elkhouly.commons.extensions.getPublicContactSource
import com.elkhouly.commons.extensions.setupDialogStuff
import com.elkhouly.commons.extensions.toast
import com.elkhouly.commons.helpers.ContactsHelper
import com.elkhouly.commons.helpers.SMT_PRIVATE
import com.elkhouly.commons.helpers.ensureBackgroundThread
import com.elkhouly.contacts.R
import com.elkhouly.contacts.activities.SimpleActivity
import com.elkhouly.contacts.databinding.DialogImportContactsBinding
import com.elkhouly.contacts.extensions.config
import com.elkhouly.contacts.extensions.showContactSourcePicker
import com.elkhouly.contacts.helpers.VcfImporter
import com.elkhouly.contacts.helpers.VcfImporter.ImportResult.IMPORT_FAIL

class ImportContactsDialog(val activity: SimpleActivity, val path: String, private val callback: (refreshView: Boolean) -> Unit) {
    private var targetContactSource = ""
    private var ignoreClicks = false

    init {
        val binding = DialogImportContactsBinding.inflate(activity.layoutInflater).apply {
            targetContactSource = activity.config.lastUsedContactSource
            activity.getPublicContactSource(targetContactSource) {
                importContactsTitle.setText(it)
                if (it.isEmpty()) {
                    ContactsHelper(activity).getContactSources {
                        val localSource = it.firstOrNull { it.name == SMT_PRIVATE }
                        if (localSource != null) {
                            targetContactSource = localSource.name
                            activity.runOnUiThread {
                                importContactsTitle.setText(localSource.publicName)
                            }
                        }
                    }
                }
            }

            importContactsTitle.setOnClickListener {
                activity.showContactSourcePicker(targetContactSource) {
                    targetContactSource = if (it == activity.getString(R.string.phone_storage_hidden)) SMT_PRIVATE else it
                    activity.getPublicContactSource(it) {
                        val title = if (it == "") activity.getString(R.string.phone_storage) else it
                        importContactsTitle.setText(title)
                    }
                }
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.elkhouly.commons.R.string.ok, null)
            .setNegativeButton(com.elkhouly.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.import_contacts) { alertDialog ->
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (ignoreClicks) {
                            return@setOnClickListener
                        }

                        ignoreClicks = true
                        activity.toast(com.elkhouly.commons.R.string.importing)
                        ensureBackgroundThread {
                            val result = VcfImporter(activity).importContacts(path, targetContactSource)
                            handleParseResult(result)
                            alertDialog.dismiss()
                        }
                    }
                }
            }
    }

    private fun handleParseResult(result: VcfImporter.ImportResult) {
        activity.toast(
            when (result) {
                VcfImporter.ImportResult.IMPORT_OK -> com.elkhouly.commons.R.string.importing_successful
                VcfImporter.ImportResult.IMPORT_PARTIAL -> com.elkhouly.commons.R.string.importing_some_entries_failed
                else -> com.elkhouly.commons.R.string.importing_failed
            }
        )
        callback(result != IMPORT_FAIL)
    }
}
