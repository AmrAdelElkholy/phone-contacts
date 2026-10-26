package com.elkhouly.contacts.dialogs

import android.widget.ArrayAdapter
import com.elkhouly.commons.activities.BaseSimpleActivity
import com.elkhouly.commons.extensions.getAlertDialogBuilder
import com.elkhouly.commons.extensions.getProperBackgroundColor
import com.elkhouly.commons.extensions.getProperPrimaryColor
import com.elkhouly.commons.extensions.getProperTextColor
import com.elkhouly.commons.extensions.setupDialogStuff
import com.elkhouly.contacts.R
import com.elkhouly.contacts.databinding.DialogWhatsappCountryBinding
import com.elkhouly.contacts.extensions.PINNED_WHATSAPP_REGIONS
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

class WhatsAppCountryDialog(
    val activity: BaseSimpleActivity,
    phoneNumber: String,
    preselectedRegion: String?,
    val callback: (region: String) -> Unit
) {
    init {
        val binding = DialogWhatsappCountryBinding.inflate(activity.layoutInflater)
        val regions = getRegions()
        binding.whatsappCountryMessage.text = activity.getString(R.string.whatsapp_country_message, phoneNumber)
        binding.whatsappCountrySpinner.apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, regions.map(::getRegionLabel))
            setSelection(regions.indexOf(preselectedRegion).coerceAtLeast(0))
            setColors(activity.getProperTextColor(), activity.getProperPrimaryColor(), activity.getProperBackgroundColor())
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.elkhouly.commons.R.string.ok) { _, _ ->
                callback(regions[binding.whatsappCountrySpinner.selectedItemPosition])
            }
            .setNegativeButton(com.elkhouly.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.whatsapp_country_required)
            }
    }

    private fun getRegions(): List<String> {
        val others = PhoneNumberUtil.getInstance().supportedRegions
            .filter { it !in PINNED_WHATSAPP_REGIONS }
            .sortedBy { Locale("", it).displayCountry }
        return PINNED_WHATSAPP_REGIONS + others
    }

    private fun getRegionLabel(region: String): String {
        val flag = region.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
        val countryCode = PhoneNumberUtil.getInstance().getCountryCodeForRegion(region)
        return "$flag ${Locale("", region).displayCountry} (+$countryCode)"
    }
}
