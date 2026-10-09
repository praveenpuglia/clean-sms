package com.praveenpuglia.cleansms

import android.Manifest
import android.content.ContentProviderOperation
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.ContactsContract
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactDirectoryTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun setUp() {
        shell("pm grant ${context.packageName} android.permission.READ_CONTACTS")
        deleteTestContacts()
        ContactDirectory.invalidate()
    }

    @After
    fun tearDown() {
        deleteTestContacts()
        ContactDirectory.invalidate()
    }

    @Test
    fun candidateKeysGoFromMostSpecificToSuffixes() {
        assertEquals(
            listOf("+919876543210", "919876543210", "9876543210", "+9876543210", "09876543210", "876543210", "76543210", "6543210"),
            ContactDirectory.candidateKeys("+91 98765 43210"),
        )
    }

    @Test
    fun onlyPersonalLookingNumbersAreMatchedAgainstContacts() {
        assertFalse(ContactDirectory.isMobileNumberCandidate("VM-HDFCBK-T"))
        assertFalse(ContactDirectory.isMobileNumberCandidate("56767"))
        assertFalse(ContactDirectory.isMobileNumberCandidate(""))
        assertTrue(ContactDirectory.isMobileNumberCandidate("+919876543210"))
        assertTrue(ContactDirectory.isMobileNumberCandidate("9811122334"))
    }

    @Test
    fun savedContactMatchesAcrossNumberFormats() {
        insertContact(NAME_A, "+91 98111 22334")

        listOf("+919811122334", "9811122334", "+91 98111-22334").forEach { address ->
            assertEquals(address, NAME_A, ContactDirectory.resolve(context, address)?.name)
        }
        assertNotNull(ContactDirectory.resolve(context, "+919811122334")?.lookupUri)
        assertEquals(NAME_A, ContactDirectory.enrich(context, "+919811122334")?.name)
        assertNull(ContactDirectory.resolve(context, "VM-ABCDEF-S"))
        assertNull(ContactDirectory.enrich(context, "+15550109999"))
    }

    @Test
    fun contactAddedWhileRunningIsPickedUpWithoutRestart() {
        insertContact(NAME_A, "+91 98111 22334")
        assertEquals(NAME_A, ContactDirectory.resolve(context, "+919811122334")?.name)
        assertNull(ContactDirectory.resolve(context, "+919822233445"))

        insertContact(NAME_B, "+91 98222 33445")

        val deadline = SystemClock.uptimeMillis() + 5_000
        var hit: ContactInfo? = null
        while (hit == null && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            hit = ContactDirectory.resolve(context, "+919822233445")
            if (hit == null) SystemClock.sleep(100)
        }
        assertEquals(NAME_B, hit?.name)
    }

    @Test
    fun threadIntentCarriesHeaderDetails() {
        val intent = ThreadDetailActivity.intent(
            context = context,
            threadId = Long.MAX_VALUE,
            address = "+15550100077",
            contactName = NAME_A,
            photoUri = null,
            lookupUri = null,
            category = MessageCategory.PERSONAL,
        )
        ActivityScenario.launch<ThreadDetailActivity>(intent).use {
            composeRule.onNodeWithText(NAME_A).assertIsDisplayed()
            composeRule.onNodeWithText("+15550100077").assertIsDisplayed()
        }
    }

    private fun insertContact(name: String, number: String) = withWriteContacts {
        val ops = arrayListOf(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build(),
        )
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    private fun deleteTestContacts() = withWriteContacts {
        context.contentResolver.delete(
            ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build(),
            "${ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY} IN (?, ?)",
            arrayOf(NAME_A, NAME_B),
        )
    }

    private fun <T> withWriteContacts(block: () -> T): T {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS)
        return try {
            block()
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun shell(command: String) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private companion object {
        const val NAME_A = "Zz Directory Test A"
        const val NAME_B = "Zz Directory Test B"
    }
}
