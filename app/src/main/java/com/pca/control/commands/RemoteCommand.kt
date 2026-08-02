package com.pca.control.commands

enum class RemoteCommand(val wire: String, val smsKeyword: String) {
    LOCK("LOCK", "PCA LOCK"),
    UNLOCK("UNLOCK", "PCA UNLOCK");

    companion object {
        fun fromWire(value: String?): RemoteCommand? =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }

        fun fromSmsBody(body: String): RemoteCommand? {
            val normalized = body.trim().uppercase().replace(Regex("\\s+"), " ")
            return entries.firstOrNull { normalized == it.smsKeyword || normalized.endsWith(it.smsKeyword) }
                ?: when {
                    normalized.contains("UNLOCK") -> UNLOCK
                    normalized.matches(Regex(".*\\bLOCK\\b.*")) && !normalized.contains("UNLOCK") -> LOCK
                    else -> null
                }
        }
    }
}
