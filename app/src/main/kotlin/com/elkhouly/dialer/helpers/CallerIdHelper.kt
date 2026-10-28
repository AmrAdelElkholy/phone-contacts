package com.elkhouly.dialer.helpers

import android.content.Context
import android.telephony.TelephonyManager
import com.elkhouly.dialer.R
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberToCarrierMapper
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import com.google.i18n.phonenumbers.ShortNumberInfo
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import java.util.Locale

/**
 * Offline caller ID for numbers that match no contact, see docs/caller-id/PLAN.md.
 * [name] comes from the curated hotlines file, [details] describe the number itself,
 * like "Vodafone · Mobile" or "Cairo · Landline".
 */
data class CallerId(val name: String?, val details: String)

object CallerIdHelper {
    private const val KNOWN_NUMBERS_FILE = "caller_id/known_numbers.tsv"

    // the regions local numbers are tried in after the SIM's own, matching the WhatsApp country picker
    private val FALLBACK_REGIONS = listOf("EG", "SA")

    private class KnownNumber(val category: String, val nameEn: String, val nameAr: String)

    private val util = PhoneNumberUtil.getInstance()
    private var knownNumbers: Map<String, KnownNumber>? = null

    fun identify(context: Context, number: String): CallerId? {
        val homeRegion = getHomeRegion(context)
        val parsed = parse(number, homeRegion) ?: return null
        val known = findKnownNumber(context, number, homeRegion)
        val details = if (known != null) {
            getCategoryLabel(context, known.category)
        } else {
            getNumberDetails(context, number, parsed, homeRegion)
        }

        val name = known?.let { getLocalizedName(it) }
        return if (name == null && details.isEmpty()) null else CallerId(name, details)
    }

    fun getKnownName(context: Context, number: String): String? {
        return findKnownNumber(context, number, getHomeRegion(context))?.let { getLocalizedName(it) }
    }

    private fun findKnownNumber(context: Context, number: String, homeRegion: String): KnownNumber? {
        val numbers = getKnownNumbers(context)
        if (numbers.isEmpty()) {
            return null
        }

        // a local number may belong to either country of a dual SIM phone, try the home one first
        val regions = if (isInternational(number)) listOf(homeRegion) else (listOf(homeRegion) + FALLBACK_REGIONS).distinct()
        return regions.firstNotNullOfOrNull { region -> parse(number, region)?.let { numbers[getKey(it)] } }
    }

    private fun getNumberDetails(context: Context, number: String, parsed: PhoneNumber, homeRegion: String): String {
        if (ShortNumberInfo.getInstance().isEmergencyNumber(number, homeRegion)) {
            return getCategoryLabel(context, "emergency")
        }

        val locale = Locale.getDefault()
        val type = util.getNumberType(parsed)
        val place = when (type) {
            PhoneNumberType.MOBILE -> PhoneNumberToCarrierMapper.getInstance().getNameForNumber(parsed, locale)
            else -> ""
        }.ifEmpty {
            // gives the city for landlines and the country for foreign numbers
            PhoneNumberOfflineGeocoder.getInstance().getDescriptionForNumber(parsed, locale, homeRegion)
        }

        val typeLabel = when (type) {
            PhoneNumberType.MOBILE -> R.string.caller_id_mobile
            PhoneNumberType.FIXED_LINE -> R.string.caller_id_landline
            PhoneNumberType.TOLL_FREE -> R.string.caller_id_toll_free
            PhoneNumberType.PREMIUM_RATE -> R.string.caller_id_premium_rate
            PhoneNumberType.SHARED_COST -> R.string.caller_id_shared_cost
            PhoneNumberType.VOIP -> R.string.caller_id_voip
            PhoneNumberType.UAN -> R.string.caller_id_company_number
            else -> null
        }?.let { context.getString(it) }

        return listOfNotNull(place.ifEmpty { null }, typeLabel).joinToString(" · ")
    }

    private fun getCategoryLabel(context: Context, category: String): String {
        val id = when (category) {
            "emergency" -> R.string.caller_id_category_emergency
            "bank" -> R.string.caller_id_category_bank
            "telecom" -> R.string.caller_id_category_telecom
            "government" -> R.string.caller_id_category_government
            "hospital" -> R.string.caller_id_category_hospital
            "delivery" -> R.string.caller_id_category_delivery
            "airline" -> R.string.caller_id_category_airline
            else -> R.string.caller_id_category_company
        }
        return context.getString(id)
    }

    private fun getLocalizedName(known: KnownNumber): String {
        return if (Locale.getDefault().language == "ar" && known.nameAr.isNotEmpty()) known.nameAr else known.nameEn
    }

    @Synchronized
    private fun getKnownNumbers(context: Context): Map<String, KnownNumber> {
        knownNumbers?.let { return it }

        val result = HashMap<String, KnownNumber>()
        try {
            context.assets.open(KNOWN_NUMBERS_FILE).bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                    // region, number, category, English name, Arabic name
                    val columns = line.trimEnd().split('\t')
                    if (columns.size >= 4) {
                        val parsed = parse(columns[1], columns[0]) ?: return@forEach
                        result[getKey(parsed)] = KnownNumber(columns[2], columns[3], columns.getOrNull(4).orEmpty())
                    }
                }
            }
        } catch (_: Exception) {
        }

        knownNumbers = result
        return result
    }

    private fun getKey(number: PhoneNumber) = "${number.countryCode}:${util.getNationalSignificantNumber(number)}"

    private fun isInternational(number: String) = number.startsWith("+") || number.startsWith("00")

    private fun parse(number: String, region: String): PhoneNumber? {
        val international = if (number.startsWith("00")) "+${number.drop(2)}" else number
        return try {
            util.parse(international, region)
        } catch (_: NumberParseException) {
            null
        }
    }

    private fun getHomeRegion(context: Context): String {
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
        return listOf(telephonyManager?.simCountryIso, telephonyManager?.networkCountryIso, Locale.getDefault().country)
            .firstOrNull { !it.isNullOrBlank() }
            ?.uppercase(Locale.US)
            ?: FALLBACK_REGIONS.first()
    }
}
