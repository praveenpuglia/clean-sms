package com.praveenpuglia.cleansms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimSlotResolverTest {
    @Test
    fun systemSlotsWinAndAreCached() {
        var lookups = 0
        val resolver = SimSlotResolver { id -> lookups++; if (id == 7) 2 else null }

        assertEquals(2, resolver.slotFor(7))
        assertEquals(2, resolver.slotFor(7))
        assertEquals(1, lookups)
    }

    @Test
    fun unknownIdsGetStableFallbackSlotsInFirstSeenOrderUpToTwo() {
        val resolver = SimSlotResolver { null }

        assertEquals(1, resolver.slotFor(40))
        assertEquals(2, resolver.slotFor(10))
        assertNull(resolver.slotFor(99))
        assertEquals(1, resolver.slotFor(40))
        assertEquals(2, resolver.slotFor(10))
    }
}
