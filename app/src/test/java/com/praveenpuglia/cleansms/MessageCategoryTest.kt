package com.praveenpuglia.cleansms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageCategoryTest {
    @Test
    fun mapsOnlySupportedTraiSuffixesCaseInsensitively() {
        listOf(
            "p" to MessageCategory.PROMOTIONAL,
            "T" to MessageCategory.TRANSACTIONAL,
            "s" to MessageCategory.SERVICE,
            "G" to MessageCategory.GOVERNMENT,
        ).forEach { (suffix, expected) ->
            assertEquals(expected, MessageCategory.fromTraiSuffix(suffix))
        }
        assertNull(MessageCategory.fromTraiSuffix(null))
        assertNull(MessageCategory.fromTraiSuffix("X"))
    }
}
