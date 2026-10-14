package com.tomhartill.kinzo.account

/** A country in the phone number picker. */
data class Country(val iso: String, val name: String, val dialCode: String)

/**
 * Turning what people type into an international number. The API checks it properly (with
 * libphonenumber); this only adds the country code, so "07700 900123" with the UK picked
 * becomes "+447700900123".
 */
object PhoneNumbers {
    // Where Kinzo is most likely to be used first. The server decides where codes can go.
    val countries = listOf(
        Country("GB", "United Kingdom", "44"),
        Country("IE", "Ireland", "353"),
        Country("US", "United States", "1"),
        Country("CA", "Canada", "1"),
        Country("AU", "Australia", "61"),
        Country("NZ", "New Zealand", "64"),
        Country("FR", "France", "33"),
        Country("DE", "Germany", "49"),
        Country("ES", "Spain", "34"),
        Country("IT", "Italy", "39"),
        Country("NL", "Netherlands", "31"),
        Country("PT", "Portugal", "351"),
        Country("IN", "India", "91"),
        Country("ZA", "South Africa", "27"),
    )

    val default: Country = countries.first()

    /** [typed] with [country]'s code, unless it already starts with + or 00. Null if it has no digits. */
    fun international(typed: String, country: Country): String? {
        val trimmed = typed.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return when {
            trimmed.startsWith("+") -> "+$digits"
            digits.startsWith("00") -> "+${digits.drop(2)}"
            // The leading 0 of a national number (UK, Ireland, Australia...) goes.
            else -> "+${country.dialCode}${digits.removePrefix("0")}"
        }
    }

    /** Plausibly long enough to send: E.164 numbers have 8 to 15 digits. */
    fun looksComplete(international: String?): Boolean = international != null && international.length - 1 in 8..15
}
