package com.praveenpuglia.cleansms

/**
 * Parses Indian Railway (IRCTC) booking confirmation SMS messages
 * and extracts structured information for rich preview display.
 */
object RailwayMessageParser {
    
    // Pattern to detect PNR number (10 digits, often prefixed with "PNR" or "PNR-")
    private val pnrPattern = Regex("""(?:PNR[:\-\s]?)(\d{10})""", RegexOption.IGNORE_CASE)
    
    // Pattern to detect train number (4-5 digits, prefixed with "Trn:" or "Train")
    private val trainPattern = Regex("""(?:Trn|Train)[:\-\s]?(\d{4,5})""", RegexOption.IGNORE_CASE)
    
    // Pattern to detect date (DD-MM-YY or DD-MM-YYYY format)
    private val datePattern = Regex("""(?:Dt|Date)[:\-\s]?(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})""", RegexOption.IGNORE_CASE)
    
    // Pattern to detect route (From X to Y)
    private val routePattern = Regex("""(?:Frm|From)\s+(\S+)\s+to\s+(\S+)""", RegexOption.IGNORE_CASE)
    
    // Pattern to detect class (2A, 3A, SL, 1A, CC, 2S, etc.)
    private val classPattern = Regex("""(?:Cls|Class)[:\-\s]?(\d?[A-Z]{1,2})""", RegexOption.IGNORE_CASE)
    
    // Pattern to detect passenger/seat info
    // Matches: P1-A1,34 (confirmed), P2-RC,A1,35 (RAC), P3-WL,45 (waiting list)
    private val passengerPatternConfirmed = Regex("""P(\d+)[:\-]([A-Z]+\d+)[,\-](\d+)""", RegexOption.IGNORE_CASE)
    private val passengerPatternRAC = Regex("""P(\d+)[:\-](RC|RAC)[,\-]([A-Z]+\d+)[,\-](\d+)""", RegexOption.IGNORE_CASE)
    private val passengerPatternWL = Regex("""P(\d+)[:\-](WL|GNWL|RLWL|PQWL)[,/]?(\d+)?""", RegexOption.IGNORE_CASE)
    
    // Pattern to detect boarding station
    private val boardingPattern = Regex("""[Bb]oarding\s+(?:allowed\s+)?(?:from\s+)?(\S+)""", RegexOption.IGNORE_CASE)
    
    // Pattern to detect chart status
    private val chartPattern = Regex("""(Chart\s+(?:Prepared|Not\s+Prepared))""", RegexOption.IGNORE_CASE)
    
    // Pattern to detect coach position URL
    private val coachUrlPattern = Regex("""(https?://\S*enquiry\.indianrail\S+)""", RegexOption.IGNORE_CASE)
    
    /**
     * Check if a message appears to be an IRCTC railway booking message
     */
    fun isRailwayMessage(body: String): Boolean {
        // Must have PNR number and at least train number or route
        val hasPnr = pnrPattern.containsMatchIn(body)
        val hasTrain = trainPattern.containsMatchIn(body)
        val hasRoute = routePattern.containsMatchIn(body)
        val hasIrctcIndicator = body.contains("IRCTC", ignoreCase = true) || 
                                body.contains("IR-CRIS", ignoreCase = true) ||
                                body.contains("indianrail", ignoreCase = true)
        
        return hasPnr && (hasTrain || hasRoute || hasIrctcIndicator)
    }
    
    /**
     * Parse railway message and extract structured data
     */
    fun parse(body: String): RailwayInfo? {
        if (!isRailwayMessage(body)) return null
        
        val pnr = pnrPattern.find(body)?.groupValues?.get(1)
        if (pnr == null) return null
        
        val trainNumber = trainPattern.find(body)?.groupValues?.get(1)
        val date = datePattern.find(body)?.groupValues?.get(1)
        
        val routeMatch = routePattern.find(body)
        val fromStation = routeMatch?.groupValues?.get(1)
        val toStation = routeMatch?.groupValues?.get(2)
        
        val travelClass = classPattern.find(body)?.groupValues?.get(1)?.uppercase()
        
        // Extract all passenger/seat info
        val passengers = mutableListOf<PassengerInfo>()
        
        // First try RAC pattern (P2-RC,A1,35)
        passengerPatternRAC.findAll(body).forEach { match ->
            passengers.add(PassengerInfo(
                passengerNumber = match.groupValues[1].toIntOrNull() ?: 0,
                coach = match.groupValues[3].uppercase(),
                seat = match.groupValues[4],
                status = PassengerStatus.RAC
            ))
        }
        
        // Then try WL pattern (P3-WL,45 or P3-GNWL/45)
        passengerPatternWL.findAll(body).forEach { match ->
            val pNum = match.groupValues[1].toIntOrNull() ?: 0
            // Avoid duplicates
            if (passengers.none { it.passengerNumber == pNum }) {
                passengers.add(PassengerInfo(
                    passengerNumber = pNum,
                    coach = null,
                    seat = match.groupValues[3].ifEmpty { null },
                    status = PassengerStatus.WAITLIST
                ))
            }
        }
        
        // Finally try confirmed pattern (P1-A1,34)
        passengerPatternConfirmed.findAll(body).forEach { match ->
            val pNum = match.groupValues[1].toIntOrNull() ?: 0
            // Avoid duplicates (RAC matches might also match this pattern partially)
            if (passengers.none { it.passengerNumber == pNum }) {
                passengers.add(PassengerInfo(
                    passengerNumber = pNum,
                    coach = match.groupValues[2].uppercase(),
                    seat = match.groupValues[3],
                    status = PassengerStatus.CONFIRMED
                ))
            }
        }
        
        // Sort by passenger number
        passengers.sortBy { it.passengerNumber }
        
        val boardingStation = boardingPattern.find(body)?.groupValues?.get(1)
        val chartStatus = chartPattern.find(body)?.groupValues?.get(1)
        val coachPositionUrl = coachUrlPattern.find(body)?.groupValues?.get(1)
        
        return RailwayInfo(
            pnr = pnr,
            trainNumber = trainNumber,
            date = date,
            fromStation = fromStation,
            toStation = toStation,
            travelClass = travelClass,
            passengers = passengers,
            boardingStation = boardingStation,
            chartStatus = chartStatus,
            coachPositionUrl = coachPositionUrl
        )
    }
    
    /**
     * Format the travel class for display
     */
    fun formatClass(cls: String?): String? {
        return when (cls?.uppercase()) {
            "1A" -> "First AC"
            "2A" -> "2-Tier AC"
            "3A" -> "3-Tier AC"
            "SL" -> "Sleeper"
            "CC" -> "Chair Car"
            "2S" -> "Second Sitting"
            "FC" -> "First Class"
            "3E" -> "3-Tier Economy"
            else -> cls
        }
    }
    
    /**
     * Decode coach type from coach code
     * H = 1st AC, A = 2nd AC, HA = 1st/2nd AC, B = 3rd AC, M = 3AC Economy
     * S = Sleeper, C = Chair Car AC, D = Second Sitting
     */
    fun getCoachType(coach: String?): String? {
        if (coach == null) return null
        val prefix = coach.takeWhile { it.isLetter() }.uppercase()
        return when {
            prefix == "H" -> "1A"
            prefix == "HA" -> "1A/2A"
            prefix == "A" -> "2A"
            prefix == "B" -> "3A"
            prefix == "M" -> "3E"
            prefix == "S" -> "SL"
            prefix == "C" -> "CC"
            prefix == "D" -> "2S"
            else -> null
        }
    }
    
    /**
     * Format coach type for display
     */
    fun formatCoachType(coach: String?): String? {
        val type = getCoachType(coach) ?: return null
        return formatClass(type)
    }
}

enum class PassengerStatus {
    CONFIRMED,
    RAC,
    WAITLIST
}

data class RailwayInfo(
    val pnr: String,
    val trainNumber: String?,
    val date: String?,
    val fromStation: String?,
    val toStation: String?,
    val travelClass: String?,
    val passengers: List<PassengerInfo>,
    val boardingStation: String?,
    val chartStatus: String?,
    val coachPositionUrl: String?
)

data class PassengerInfo(
    val passengerNumber: Int,
    val coach: String?,
    val seat: String?,
    val status: PassengerStatus = PassengerStatus.CONFIRMED
)

