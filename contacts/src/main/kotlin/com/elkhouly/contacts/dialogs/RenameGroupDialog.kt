package com.elkhouly.contacts.dialogs

import androidx.appcompat.app.AlertDialog
import com.elkhouly.commons.activities.BaseSimpleActivity
import com.elkhouly.commons.extensions.*
import com.elkhouly.commons.helpers.ContactsHelper
import com.elkhouly.commons.helpers.ensureBackgroundThread
import com.elkhouly.commons.models.contacts.Group
import com.elkhouly.contacts.databinding.DialogRenameGroupBinding

class RenameGroupDialog(val activity: BaseSimpleActivity, val group: Group, val callback: () -> Unit) {
    init {
        val binding = DialogRenameGroupBinding.inflate(activity.layoutInflater).apply {
            renameGroupTitle.setText(group.title)
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.elkhouly.commons.R.string.ok, null)
            .setNegativeButton(com.elkhouly.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, com.elkhouly.commons.R.string.rename) { alertDialog ->
                    alertDialog.showKeyboard(binding.renameGroupTitle)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val newTitle = binding.renameGroupTitle.value
                        if (newTitle.isEmpty()) {
                            activity.toast(com.elkhouly.commons.R.string.empty_name)
                            return@setOnClickListener
                        }

                        if (!newTitle.isAValidFilename()) {
                            activity.toast(com.elkhouly.commons.R.string.invalid_name)
                            return@setOnClickListener
                        }

                        group.title = newTitle
                        group.contactsCount = 0
                        ensureBackgroundThread {
                            if (group.isPrivateSecretGroup()) {
                                activity.groupsDB.insertOrUpdate(group)
                            } else {
                                ContactsHelper(activity).renameGroup(group)
                            }
                            activity.runOnUiThread {
                                callback()
                                alertDialog.dismiss()
                            }
                        }
                    }
                }
            }
    }
}
