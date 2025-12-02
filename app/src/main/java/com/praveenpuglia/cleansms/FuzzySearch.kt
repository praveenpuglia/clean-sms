package com.praveenpuglia.cleansms

/**
 * Simple fuzzy search implementation for message content.
 * Scores results based on various factors like exact match, word boundary match, etc.
 */
object FuzzySearch {
    
    /**
     * Calculate relevance score for a message body against a search query.
     * Higher score = more relevant.
     * Returns 0.0 if no match found.
     */
    fun score(body: String, query: String): Double {
        if (query.isBlank()) return 0.0
        
        val bodyLower = body.lowercase()
        val queryLower = query.lowercase()
        val queryWords = queryLower.split(Regex("\\s+")).filter { it.isNotBlank() }
        
        var totalScore = 0.0
        
        // Check for exact phrase match (highest priority)
        if (bodyLower.contains(queryLower)) {
            totalScore += 100.0
            
            // Bonus for match at start of message
            if (bodyLower.startsWith(queryLower)) {
                totalScore += 50.0
            }
            
            // Bonus for match at word boundary
            val wordBoundaryPattern = Regex("\\b${Regex.escape(queryLower)}")
            if (wordBoundaryPattern.containsMatchIn(bodyLower)) {
                totalScore += 30.0
            }
            
            // Count occurrences
            var count = 0
            var index = 0
            while (true) {
                index = bodyLower.indexOf(queryLower, index)
                if (index < 0) break
                count++
                index++
            }
            totalScore += count * 5.0
        } else if (queryWords.size > 1) {
            // Multi-word query: check if all words are present
            var allWordsFound = true
            var wordMatchScore = 0.0
            
            for (word in queryWords) {
                if (bodyLower.contains(word)) {
                    wordMatchScore += 20.0
                    
                    // Bonus for word boundary match
                    val wordBoundary = Regex("\\b${Regex.escape(word)}")
                    if (wordBoundary.containsMatchIn(bodyLower)) {
                        wordMatchScore += 10.0
                    }
                } else {
                    allWordsFound = false
                }
            }
            
            if (allWordsFound) {
                totalScore += wordMatchScore + 30.0 // Bonus for all words present
            } else {
                totalScore += wordMatchScore * 0.5 // Partial match
            }
        }
        
        // Fuzzy matching: check for character sequence (for typos)
        if (totalScore == 0.0 && queryLower.length >= 3) {
            val fuzzyScore = fuzzyCharacterMatch(bodyLower, queryLower)
            if (fuzzyScore > 0.6) {
                totalScore += fuzzyScore * 30.0
            }
        }
        
        return totalScore
    }
    
    /**
     * Check if query characters appear in order within the body.
     * Returns a score between 0 and 1.
     */
    private fun fuzzyCharacterMatch(body: String, query: String): Double {
        var queryIndex = 0
        var matches = 0
        var consecutiveMatches = 0
        var maxConsecutive = 0
        
        for (char in body) {
            if (queryIndex < query.length && char == query[queryIndex]) {
                matches++
                consecutiveMatches++
                maxConsecutive = maxOf(maxConsecutive, consecutiveMatches)
                queryIndex++
            } else {
                consecutiveMatches = 0
            }
        }
        
        if (matches == 0) return 0.0
        
        val matchRatio = matches.toDouble() / query.length
        val consecutiveBonus = maxConsecutive.toDouble() / query.length * 0.3
        
        return (matchRatio * 0.7 + consecutiveBonus).coerceIn(0.0, 1.0)
    }
    
    /**
     * Search and rank messages by relevance.
     */
    fun search(messages: List<SearchResultItem>, query: String): List<SearchResultItem> {
        if (query.isBlank()) return messages.sortedByDescending { it.date }
        
        return messages
            .map { msg -> 
                // Also search in sender name
                val bodyScore = score(msg.body, query)
                val senderScore = score(msg.senderDisplay ?: msg.sender, query) * 0.5
                msg.copy(relevanceScore = bodyScore + senderScore)
            }
            .filter { it.relevanceScore > 0 }
            .sortedByDescending { it.relevanceScore }
    }
}

