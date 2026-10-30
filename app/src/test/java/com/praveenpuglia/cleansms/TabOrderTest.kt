package com.praveenpuglia.cleansms

import com.praveenpuglia.cleansms.ui.inbox.InboxPage
import com.praveenpuglia.cleansms.ui.inbox.key
import com.praveenpuglia.cleansms.ui.inbox.orderPages
import org.junit.Assert.assertEquals
import org.junit.Test

class TabOrderTest {
    private val personal = InboxPage.CategoryPage(MessageCategory.PERSONAL)
    private val transactions = InboxPage.CategoryPage(MessageCategory.TRANSACTIONAL)
    private val defaults = listOf(InboxPage.Otp, personal, transactions, InboxPage.Starred)

    @Test
    fun noSavedOrderKeepsTheDefaults() {
        assertEquals(defaults, orderPages(defaults, emptyList()))
    }

    @Test
    fun savedOrderWins() {
        val saved = listOf(transactions, InboxPage.Starred, InboxPage.Otp, personal)
        assertEquals(saved, orderPages(defaults, saved.map { it.key }))
    }

    @Test
    fun aPageTheSavedOrderDoesNotKnowKeepsItsDefaultPosition() {
        // The order was saved before the All tab was turned on; All is first by default.
        val withAll = listOf(InboxPage.All) + defaults
        val saved = listOf(transactions, InboxPage.Otp, personal, InboxPage.Starred).map { it.key }

        assertEquals(listOf(InboxPage.All, transactions, InboxPage.Otp, personal, InboxPage.Starred), orderPages(withAll, saved))
    }

    @Test
    fun unknownAndRepeatedKeysAreIgnored() {
        val saved = listOf("ALL", "STARRED", "STARRED", "SOMETHING_OLD", "OTP")

        assertEquals(listOf(InboxPage.Starred, InboxPage.Otp, personal, transactions), orderPages(defaults, saved))
    }
}
