package com.praveenpuglia.cleansms

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.provider.Telephony
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SenderBrandsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun setUp() {
        AppSettings.setShowSenderLogos(context, true)
        cleanUp()
    }

    @After
    fun cleanUp() {
        AppSettings.setShowSenderLogos(context, true)
        SENDERS.forEach { context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.ADDRESS} = ?", arrayOf(it)) }
    }

    @Test
    fun headerIsTheMiddlePartWithCasePreserved() {
        assertEquals("IndiGo", SenderBrands.headerOf("JD-IndiGo-S"))
        assertEquals("HDFCBK", SenderBrands.headerOf("VM-HDFCBK"))
        assertNull(SenderBrands.headerOf("+919876543210"))
        assertNull(SenderBrands.headerOf("HDFCBK"))
    }

    @Test
    fun onlyRegisteredOwnersGetABrandAndCaseMatters() {
        assertEquals("indigo", SenderBrands.brandFor(context, "AD-INDIGO-S"))
        assertEquals("indigo", SenderBrands.brandFor(context, "AD-IndiGo-S"))
        assertNull("Indigo is Indigo Paints, not the airline", SenderBrands.brandFor(context, "AD-Indigo-S"))
        assertNull("GOOGLE is registered to an unrelated company", SenderBrands.brandFor(context, "VM-GOOGLE-T"))
        assertNull("NETFLX is registered to an unrelated company", SenderBrands.brandFor(context, "VM-NETFLX-T"))
        assertEquals("icici_bank", SenderBrands.brandFor(context, "JM-ICICIB-T"))
    }

    @Test
    fun everyMappedBrandHasABundledLogoThatDecodes() {
        val brands = context.assets.open("sender_brands.tsv").bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.substringAfter('\t') }
            .toSet()
        assertTrue(brands.size >= 20)
        brands.forEach { brand ->
            val uri = "android.resource://${context.packageName}/drawable/brand_$brand".toUri()
            val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
            assertNotNull("logo for $brand", bitmap)
        }
    }

    @Test
    fun turningLogosOffHidesThem() {
        assertNotNull(SenderBrands.logoUri(context, "JM-ICICIB-T"))
        AppSettings.setShowSenderLogos(context, false)
        assertNull(SenderBrands.logoUri(context, "JM-ICICIB-T"))
    }

    @Test
    fun inboxShowsTheLogoForABrandButNotForALookAlike() {
        shell("cmd role add-role-holder android.app.role.SMS ${context.packageName} 0")
        AppSettings.setOnboardingCompleted(context)
        SENDERS.forEach { address ->
            context.contentResolver.insert(
                Telephony.Sms.Inbox.CONTENT_URI,
                ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, "Logo fixture")
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                },
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var threads = emptyList<ThreadItem>()
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline) {
                scenario.onActivity { threads = ViewModelProvider(it)[InboxViewModel::class.java].allThreads }
                if (SENDERS.all { s -> threads.any { it.nameOrAddress == s } }) break
                Thread.sleep(100)
            }
            assertEquals(
                "android.resource://${context.packageName}/drawable/brand_icici_bank",
                threads.single { it.nameOrAddress == BRAND_SENDER }.contactPhotoUri,
            )
            assertNull(threads.single { it.nameOrAddress == LOOKALIKE_SENDER }.contactPhotoUri)
        }
    }

    private fun shell(command: String) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private companion object {
        const val BRAND_SENDER = "JM-ICICIB-T"
        const val LOOKALIKE_SENDER = "VM-GOOGLE-T"
        val SENDERS = listOf(BRAND_SENDER, LOOKALIKE_SENDER)
    }
}
