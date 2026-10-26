package com.elkhouly.contacts.extensions

import android.content.Intent
import android.net.Uri
import com.elkhouly.commons.activities.BaseSimpleActivity
import com.elkhouly.commons.extensions.isPackageInstalled
import com.elkhouly.commons.extensions.launchActivityIntent
import com.elkhouly.commons.extensions.toast
import com.elkhouly.contacts.R
import com.elkhouly.contacts.dialogs.WhatsAppCountryDialog
import com.google.i18n.phonenumbers.PhoneNumberUtil

// listed first in the country picker and tried first when guessing a local number's country
val PINNED_WHATSAPP_REGIONS = listOf("SA", "EG")

fun BaseSimpleActivity.openWhatsAppChat(phoneNumber: String, business: Boolean) {
    if (!isWhatsAppInstalled(business)) {
        return
    }

    if (isInternationalNumber(phoneNumber)) {
        launchWhatsApp(getInternationalDigits(phoneNumber), business)
        return
    }

    val savedRegion = config.getWhatsAppRegion(phoneNumber)
    if (savedRegion != null) {
        launchWhatsApp(getLocalNumberDigits(phoneNumber, savedRegion), business)
    } else {
        pickWhatsAppCountry(phoneNumber, business)
    }
}

// lets the user correct the country remembered for a local number
fun BaseSimpleActivity.changeWhatsAppCountry(phoneNumber: String, business: Boolean) {
    if (!isWhatsAppInstalled(business)) {
        return
    }

    if (isInternationalNumber(phoneNumber)) {
        launchWhatsApp(getInternationalDigits(phoneNumber), business)
    } else {
        pickWhatsAppCountry(phoneNumber, business)
    }
}

private fun BaseSimpleActivity.pickWhatsAppCountry(phoneNumber: String, business: Boolean) {
    val preselected = config.getWhatsAppRegion(phoneNumber) ?: guessRegion(phoneNumber)
    WhatsAppCountryDialog(this, phoneNumber, preselected) { region ->
        config.saveWhatsAppRegion(phoneNumber, region)
        launchWhatsApp(getLocalNumberDigits(phoneNumber, region), business)
    }
}

private fun BaseSimpleActivity.isWhatsAppInstalled(business: Boolean): Boolean {
    val installed = isPackageInstalled(getWhatsAppPackage(business))
    if (!installed) {
        toast(if (business) R.string.whatsapp_business_not_installed else R.string.whatsapp_not_installed)
    }
    return installed
}

private fun BaseSimpleActivity.launchWhatsApp(digits: String?, business: Boolean) {
    // WhatsApp expects the full international number, digits only
    if (digits == null || digits.length < 7) {
        toast(R.string.whatsapp_invalid_number)
        return
    }

    Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).apply {
        `package` = getWhatsAppPackage(business)
        launchActivityIntent(this)
    }
}

private fun getWhatsAppPackage(business: Boolean) = if (business) "com.whatsapp.w4b" else "com.whatsapp"

private fun cleanNumber(phoneNumber: String) = phoneNumber.trim().filter { it.isDigit() || it == '+' }

fun isInternationalNumber(phoneNumber: String): Boolean {
    val cleaned = cleanNumber(phoneNumber)
    return cleaned.startsWith("+") || cleaned.startsWith("00")
}

// 00201019909968 and +201019909968 both become 201019909968
private fun getInternationalDigits(phoneNumber: String): String {
    return cleanNumber(phoneNumber).removePrefix("+").removePrefix("00").filter { it.isDigit() }
}

// 01019909968 in EG becomes 201019909968, 0575884053 in SA becomes 966575884053
private fun getLocalNumberDigits(phoneNumber: String, region: String): String {
    val local = cleanNumber(phoneNumber).filter { it.isDigit() }
    val phoneNumberUtil = PhoneNumberUtil.getInstance()
    val e164 = try {
        val parsed = phoneNumberUtil.parse(local, region)
        if (phoneNumberUtil.isPossibleNumber(parsed)) {
            phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    // drop the national trunk prefix ourselves when libphonenumber can't parse the number
    return e164?.removePrefix("+")
        ?: "${phoneNumberUtil.getCountryCodeForRegion(region)}${local.trimStart('0')}"
}

private fun guessRegion(phoneNumber: String): String? {
    val local = cleanNumber(phoneNumber).filter { it.isDigit() }
    val phoneNumberUtil = PhoneNumberUtil.getInstance()
    return PINNED_WHATSAPP_REGIONS.firstOrNull { region ->
        try {
            phoneNumberUtil.isValidNumberForRegion(phoneNumberUtil.parse(local, region), region)
        } catch (_: Exception) {
            false
        }
    }
}
