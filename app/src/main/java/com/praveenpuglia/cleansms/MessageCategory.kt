package com.praveenpuglia.cleansms

enum class MessageCategory(val displayName: String) {
    PERSONAL("Personal"),
    PROMOTIONAL("Promotions"),
    TRANSACTIONAL("Transactions"),
    SERVICE("Service"),
    GOVERNMENT("Government"),
    UNKNOWN("Unknown");

    companion object {
        fun fromTraiSuffix(suffix: String?): MessageCategory? {
            return when (suffix?.uppercase()) {
                "P" -> PROMOTIONAL
                "T" -> TRANSACTIONAL
                "S" -> SERVICE
                "G" -> GOVERNMENT
                else -> null
            }
        }
    }
}
