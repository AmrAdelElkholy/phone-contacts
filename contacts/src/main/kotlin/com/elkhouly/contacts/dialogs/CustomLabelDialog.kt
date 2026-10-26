package com.elkhouly.contacts.dialogs

import androidx.appcompat.app.AlertDialog
import com.elkhouly.commons.activities.BaseSimpleActivity
import com.elkhouly.commons.extensions.*
import com.elkhouly.contacts.databinding.DialogCustomLabelBinding

class CustomLabelDialog(val activity: BaseSimpleActivity, val callback: (label: String) -> Unit) {
    init {
        val binding = DialogCustomLabelBinding.inflate(activity.layoutInflater)

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.elkhouly.commons.R.string.ok, null)
            .setNegativeButton(com.elkhouly.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, com.elkhouly.commons.R.string.label) { alertDialog ->
                    alertDialog.showKeyboard(binding.customLabelEdittext)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val label = binding.customLabelEdittext.value
                        if (label.isEmpty()) {
                            activity.toast(com.elkhouly.commons.R.string.empty_name)
                            return@setOnClickListener
                        }

                        callback(label)
                        alertDialog.dismiss()
                    }
                }
            }
    }
}
