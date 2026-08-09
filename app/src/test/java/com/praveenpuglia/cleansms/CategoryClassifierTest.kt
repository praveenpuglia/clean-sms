package com.praveenpuglia.cleansms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryClassifierTest {
    @Test
    fun extractsEverySupportedOtpKeyword() {
        listOf(
            "Your OTP is 1001." to "1001",
            "Your one-time password is 1002." to "1002",
            "Verification code: 1003." to "1003",
            "Your security code is 1004." to "1004",
            "Login code 1005." to "1005",
            "Authorization code: 1006." to "1006",
            "Auth code is 1007." to "1007",
            "Access code: 1008." to "1008",
            "Confirmation code is 1009." to "1009",
            "Authentication code 1010." to "1010",
            "Passcode: 1011." to "1011",
            "Your PIN code is 1012." to "1012",
            "Delivery code: share with the rider 1013." to "1013",
            "Share the PIN 1014 with the rider." to "1014",
            "Secret code 1015." to "1015",
            "Temporary code is 1016." to "1016",
            "Dynamic code: 1017." to "1017"
        ).forEach { (message, expected) -> assertOtp(expected, message) }
    }

    @Test
    fun extractsEverySupportedMessageStructure() {
        listOf(
            "G-892341 is your Google verification code. Don't share it with anyone." to "892341",
            "743829 is the OTP to login to your Amazon account." to "743829",
            "478291 is the authorization code for your Medplus login." to "478291",
            "483920 is the checkout code." to "483920",
            "The code is 483921." to "483921",
            "Your checkout code: 483922." to "483922",
            "Code for your account login is 483923." to "483923",
            "The verification code for your Flipkart login is 234567." to "234567",
            "4827 — your Uber code. Use within 5 minutes." to "4827",
            "4828. Do not share with anyone." to "4828",
            "Use 384921 as the Microsoft account security code." to "384921",
            "OTP:\n  384922\nValid for 10 minutes." to "384922",
            "Your WhatsApp code: 539-281. Verify at v.whatsapp.com/539281. Don't share this code." to "539281",
            "Delivery Code: Share Pin 3693 with rider to accept delivery of your order." to "3693"
        ).forEach { (message, expected) -> assertOtp(expected, message) }
    }

    @Test
    fun supportsStandardOtpLengthsAndCase() {
        listOf("1234", "12345", "123456", "1234567", "12345678").forEach { code ->
            assertOtp(code, "otp: $code")
        }
        assertOtp("654321", "ONE-TIME PASSWORD IS 654321")
        assertOtp("765432", "verification-code: 765432")
        assertOtp("876543", "Share-Pin 876543 with the courier")
    }

    @Test
    fun choosesOtpInsteadOfFourDigitTransactionAmount() {
        listOf(1000, 1325, 2468, 4011, 5555, 9876, 9999).forEach { amount ->
            listOf(
                "For a transaction of Rs. $amount at RATNADEEP, use OTP 7391.",
                "For a transaction of INR $amount at RATNADEEP, your OTP: 7391.",
                "For a transaction of ₹$amount at RATNADEEP, use OTP 7391.",
                "The OTP for transaction amount is $amount; use 7391 at RATNADEEP.",
                "The OTP for a transaction amount of Rs. $amount at RATNADEEP is 7391.",
                "Transaction amount: $amount. OTP: 7391."
            ).forEach { message -> assertOtp("7391", message) }
        }
        assertOtp("4011", "The OTP for your transaction amount of Rs. 1325 at RATNADEEP is 4011. - Zeta")
        assertOtp("4011", "For a transaction of Rs. 9,876.00 at RATNADEEP, use OTP 4011. - Zeta")
    }

    @Test
    fun rejectsMessagesWithoutHighPrecisionOtpEvidence() {
        listOf(
            "",
            "Your OTP is 123.",
            "Your OTP is 123456789.",
            "Your order 7283910 worth Rs.1,499 will be delivered today.",
            "PNR 8472938102: Your train ticket has been confirmed.",
            "Rs.50,000 has been credited to your account XX1234.",
            "Paytm: Rs.299 paid to SWIGGY. Wallet balance Rs.450.",
            "Use promo code SUMMER70 for 50% off.",
            "Call 9999988888 for help.",
            "Your package tracking number is 7283910."
        ).forEach { message ->
            assertNull(message, CategoryClassifier.extractHighPrecisionOtp(message))
        }
        assertNull(CategoryClassifier.extractHighPrecisionOtp("OTP: 1234".padEnd(1001, 'x')))
    }

    @Test
    fun categorizesTraiHeadersPhoneNumbersAndShortCodes() {
        listOf(
            "VM-OFFER-P" to MessageCategory.PROMOTIONAL,
            "vm-hdfcbk-t" to MessageCategory.TRANSACTIONAL,
            "JX-BOLT-S" to MessageCategory.SERVICE,
            "VM-IRSMSA-G" to MessageCategory.GOVERNMENT,
            "+91 98765-43210" to MessageCategory.PERSONAL,
            "9876543210" to MessageCategory.PERSONAL,
            "620014" to MessageCategory.SERVICE,
            "VM-HDFCBK" to MessageCategory.UNKNOWN,
            "VM-HDFCBK-X" to MessageCategory.UNKNOWN,
            "MEESHO" to MessageCategory.UNKNOWN,
        ).forEach { (address, expected) ->
            assertEquals(address, expected, CategoryClassifier.categorizeAddress(address))
        }
    }

    @Test
    fun rejectsCodesWhoseContextIdentifiesAnotherPurpose() {
        listOf(
            "1234 is your promo code",
            "5678 is the order code",
        ).forEach { message ->
            assertNull(message, CategoryClassifier.extractHighPrecisionOtp(message))
        }
    }

    private fun assertOtp(expected: String, message: String) {
        assertEquals(message, expected, CategoryClassifier.extractHighPrecisionOtp(message))
    }
}
