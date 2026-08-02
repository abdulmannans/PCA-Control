package com.pca.control.util

object PhoneNumbers {
    private const val MIN_DIGITS = 10
    private const val MAX_DIGITS = 15
    private const val MATCH_SUFFIX_LEN = 10

    /** Keep optional leading '+' and digits only. */
    fun sanitize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val hasPlus = trimmed.startsWith('+')
        val digits = trimmed.filter { it.isDigit() }
        return if (hasPlus) "+$digits" else digits
    }

    fun digitsOnly(raw: String): String = sanitize(raw).filter { it.isDigit() }

    fun isValid(raw: String): Boolean {
        val digits = digitsOnly(raw)
        return digits.length in MIN_DIGITS..MAX_DIGITS
    }

    /**
     * Match numbers that differ by country code / formatting.
     * E.g. +919004875711 matches 9004875711 and 919004875711.
     */
    fun matches(a: String?, b: String?): Boolean {
        val da = digitsOnly(a.orEmpty())
        val db = digitsOnly(b.orEmpty())
        if (da.length < MATCH_SUFFIX_LEN || db.length < MATCH_SUFFIX_LEN) return false
        return da.endsWith(db) || db.endsWith(da) ||
            da.takeLast(MATCH_SUFFIX_LEN) == db.takeLast(MATCH_SUFFIX_LEN)
    }
}
