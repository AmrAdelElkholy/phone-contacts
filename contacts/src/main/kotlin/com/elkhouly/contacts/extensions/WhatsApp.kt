package com.elkhouly.contacts.extensions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.elkhouly.commons.extensions.isPackageInstalled
import com.elkhouly.commons.extensions.launchActivityIntent
import com.elkhouly.commons.extensions.toast
import com.elkhouly.contacts.R

fun Activity.openWhatsAppChat(phoneNumber: String, business: Boolean) {
    val packageName = if (business) "com.whatsapp.w4b" else "com.whatsapp"
    if (!isPackageInstalled(packageName)) {
        toast(if (business) R.string.whatsapp_business_not_installed else R.string.whatsapp_not_installed)
        return
    }

    val number = getWhatsAppNumber(phoneNumber)
    if (number == null) {
        toast(R.string.whatsapp_invalid_number)
        return
    }

    Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number")).apply {
        `package` = packageName
        launchActivityIntent(this)
    }
}

// WhatsApp expects the full international number, digits only
private fun Activity.getWhatsAppNumber(phoneNumber: String): String? {
    val telephonyManager = getSystemService(TelephonyManager::class.java)
    val region = (telephonyManager?.simCountryIso?.takeIf { it.isNotEmpty() }
        ?: telephonyManager?.networkCountryIso?.takeIf { it.isNotEmpty() }
        ?: resources.configuration.locales[0].country).uppercase()

    val phoneNumberUtil = PhoneNumberUtil.getInstance()
    val e164 = try {
        val parsed = phoneNumberUtil.parse(phoneNumber, region)
        if (phoneNumberUtil.isPossibleNumber(parsed)) {
            phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    val digits = (e164 ?: phoneNumber).filter { it.isDigit() }
    return digits.takeIf { it.length >= 7 }
}
