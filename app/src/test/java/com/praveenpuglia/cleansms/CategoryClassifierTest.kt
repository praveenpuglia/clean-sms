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

    // Synthetic versions of message shapes seen in a real Indian inbox (no real content).
    @Test
    fun extractsCodesFromRealWorldShapes() {
        listOf(
            "Hi there! 8213 is the delivery code for your order 554120. Share it only with the delivery partner." to "8213",
            "Your login code for the Acme app is 471920 and its valid for next 10 minutes. Team Acme" to "471920",
            "5520 24 hrs is the authorization code for your card enrolment." to "5520",
            "582913 is the OTP for INR 2,499.00 txn at CITY MART on HDFC Bank card ending 4521. Do not share it with anyone." to "582913",
            "736104 is the OTP to link your demat account with the depository. Do not share your OTP with anyone." to "736104",
            "4821 is the OTP to verify your phone number on Acme app." to "4821",
            "Use OTP 640192 to access your online application for Acme Card. OTP is valid for 10 minutes." to "640192",
            "<#> 902431 is your Acme verification code. Do not share it. AbC12dEf3G" to "902431",
            "Dear Customer,\nOTP for your transaction of Rs. 1,325.00 at CITY MART is 4011.\nValid for 3 minutes.\n- Acme Bank" to "4011",
            "OTP 7302 for login on 08/10/2026. Valid for 5 minutes. Do not share it." to "7302",
            "Ride booked: Car KA01AB1234, Driver: Ravi, Pickup: 09 Oct 2026 10:30 AM, Share OTP: 4821 with the driver to start." to "4821",
        ).forEach { (message, expected) -> assertOtp(expected, message) }
    }

    @Test
    fun ignoresMessagesThatOnlyMentionOtpOrCarryOtherNumbers() {
        listOf(
            "UPI AutoPay mandate of Rs.199 for Acme Streaming will be debited on 12-10-26. Never share your OTP or UPI PIN.",
            "Your Acme Bank A/c has been linked to Acme Pay. Do not share OTP, PIN or CVV with anyone.",
            "Shipment AB12345678 is delivered on 08/10/2026 to Customer. Share feedback: https://acme.example/f. Never share OTP for deliveries.",
            "Rs.4,500.00 debited from A/c XX7731 on 08-10-26 to VPA rahul.k@acme. Not you? Call 18001234567. Never share OTP.",
            "PNR 4729381056: Train 12951 Coach B2 Berth 34 confirmed. Charting at 20:15. Never share OTP with anyone claiming to be from Railways.",
            "Total due Rs.12,480.00, minimum due Rs.624.00 on your Acme Card XX4410. Pay by 15-10-2026 to avoid charges.",
            "Your Acme order was delivered on 8 Oct 2026. Do not share OTP with anyone asking about this order.",
            "Policy renewed on 08.10.2026. Never share OTP with callers. -Acme Insurance",
            "Appointment confirmed for 09-Oct-2026 at 10:30. Do not share your OTP with anyone. -Acme Clinic",
            "Rs.4,500 debited from A/c XX7731. Ref no 482913. Never share OTP with anyone.",
            "Your order 554120 is out for delivery. Never share OTP with anyone calling about this order.",
            "Txn ID 731942 successful for Rs.500. Do not share OTP, PIN or CVV.",
            "Complaint 662810 registered. We never ask for OTP.",
            "For help call our toll free no. 1800 266 9970. Do not share OTP with anyone.",
            "Questions? Call 98765-43210. Never share your OTP.",
        ).forEach { message -> assertNull(message, CategoryClassifier.extractHighPrecisionOtp(message)) }
    }

    private fun assertOtp(expected: String, message: String) {
        assertEquals(message, expected, CategoryClassifier.extractHighPrecisionOtp(message))
    }
}
