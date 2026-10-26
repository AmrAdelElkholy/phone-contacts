package com.elkhouly.contacts.helpers

import android.content.Context
import com.elkhouly.commons.helpers.BaseConfig

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    var showTabs: Int
        get() = prefs.getInt(CONTACTS_SHOW_TABS, ALL_TABS_MASK)
        set(showTabs) = prefs.edit().putInt(CONTACTS_SHOW_TABS, showTabs).apply()

    var autoBackupContactSources: Set<String>
        get() = prefs.getStringSet(AUTO_BACKUP_CONTACT_SOURCES, setOf())!!
        set(autoBackupContactSources) = prefs.edit().remove(AUTO_BACKUP_CONTACT_SOURCES).putStringSet(AUTO_BACKUP_CONTACT_SOURCES, autoBackupContactSources)
            .apply()

    // country chosen for a local number (no + or 00) when opening it in WhatsApp
    fun getWhatsAppRegion(number: String): String? = prefs.getString(getWhatsAppRegionKey(number), null)

    fun saveWhatsAppRegion(number: String, region: String) = prefs.edit().putString(getWhatsAppRegionKey(number), region).apply()

    private fun getWhatsAppRegionKey(number: String) = "$WHATSAPP_REGION_PREFIX${number.filter { it.isDigit() }}"
}
