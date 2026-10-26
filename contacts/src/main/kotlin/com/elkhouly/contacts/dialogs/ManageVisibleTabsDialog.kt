package com.elkhouly.contacts.dialogs

import com.elkhouly.commons.activities.BaseSimpleActivity
import com.elkhouly.commons.extensions.getAlertDialogBuilder
import com.elkhouly.commons.extensions.setupDialogStuff
import com.elkhouly.commons.helpers.TAB_CONTACTS
import com.elkhouly.commons.helpers.TAB_FAVORITES
import com.elkhouly.commons.helpers.TAB_GROUPS
import com.elkhouly.commons.views.MyAppCompatCheckbox
import com.elkhouly.contacts.R
import com.elkhouly.contacts.extensions.config
import com.elkhouly.contacts.helpers.ALL_TABS_MASK

class ManageVisibleTabsDialog(val activity: BaseSimpleActivity) {
    private var view = activity.layoutInflater.inflate(R.layout.contacts_dialog_manage_visible_tabs, null)
    private val tabs = LinkedHashMap<Int, Int>()

    init {
        tabs.apply {
            put(TAB_CONTACTS, R.id.manage_visible_tabs_contacts)
            put(TAB_FAVORITES, R.id.manage_visible_tabs_favorites)
            put(TAB_GROUPS, R.id.manage_visible_tabs_groups)
        }

        val showTabs = activity.config.showTabs
        for ((key, value) in tabs) {
            view.findViewById<MyAppCompatCheckbox>(value).isChecked = showTabs and key != 0
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.elkhouly.commons.R.string.ok) { dialog, which -> dialogConfirmed() }
            .setNegativeButton(com.elkhouly.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this)
            }
    }

    private fun dialogConfirmed() {
        var result = 0
        for ((key, value) in tabs) {
            if (view.findViewById<MyAppCompatCheckbox>(value).isChecked) {
                result += key
            }
        }

        if (result == 0) {
            result = ALL_TABS_MASK
        }

        activity.config.showTabs = result
    }
}
