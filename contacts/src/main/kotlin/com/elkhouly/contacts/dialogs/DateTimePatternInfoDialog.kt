package com.elkhouly.contacts.dialogs

import com.elkhouly.commons.activities.BaseSimpleActivity
import com.elkhouly.commons.extensions.getAlertDialogBuilder
import com.elkhouly.commons.extensions.setupDialogStuff
import com.elkhouly.contacts.R

class DateTimePatternInfoDialog(activity: BaseSimpleActivity) {

    init {
        val view = activity.layoutInflater.inflate(R.layout.datetime_pattern_info_layout, null)
        activity.getAlertDialogBuilder()
            .setPositiveButton(com.elkhouly.commons.R.string.ok) { _, _ -> { } }
            .apply {
                activity.setupDialogStuff(view, this)
            }
    }
}
