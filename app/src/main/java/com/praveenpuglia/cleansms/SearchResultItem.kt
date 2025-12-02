package com.praveenpuglia.cleansms

/**
 * Represents a search result item displayed in the search results list.
 */
data class SearchResultItem(
    val messageId: Long,
    val threadId: Long,
    val sender: String,
    val senderDisplay: String?,  // Contact name if available
    val body: String,
    val date: Long,
    val contactPhotoUri: String?,
    val contactLookupUri: String?,
    val category: MessageCategory,
    val relevanceScore: Double = 0.0  // For sorting by relevance
)

