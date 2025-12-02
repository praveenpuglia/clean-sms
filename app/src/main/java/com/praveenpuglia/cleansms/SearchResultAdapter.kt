package com.praveenpuglia.cleansms

import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class SearchResultAdapter(
    private var items: List<SearchResultItem>,
    private val searchQuery: String,
    private val onItemClick: (SearchResultItem) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatarContainer: View = itemView.findViewById(R.id.search_result_avatar_container)
        val avatarImage: ImageView = itemView.findViewById(R.id.search_result_avatar_image)
        val avatarText: TextView = itemView.findViewById(R.id.search_result_avatar_text)
        val sender: TextView = itemView.findViewById(R.id.search_result_sender)
        val date: TextView = itemView.findViewById(R.id.search_result_date)
        val snippet: TextView = itemView.findViewById(R.id.search_result_snippet)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        
        // Set sender name
        holder.sender.text = item.senderDisplay ?: item.sender
        
        // Set date
        holder.date.text = formatDate(item.date)
        
        // Set snippet with highlighted search terms
        holder.snippet.text = highlightSearchTerms(item.body, searchQuery, holder.itemView)
        
        // Set avatar
        setupAvatar(holder, item)
        
        // Click listener
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateResults(newItems: List<SearchResultItem>, query: String) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun setupAvatar(holder: VH, item: SearchResultItem) {
        val photoUri = item.contactPhotoUri
        if (!photoUri.isNullOrEmpty()) {
            try {
                holder.avatarImage.setImageURI(photoUri.toUri())
                holder.avatarImage.visibility = View.VISIBLE
                holder.avatarText.visibility = View.GONE
                return
            } catch (_: Exception) {
                // Fall through to initials
            }
        }
        
        // Show initials
        val displayName = item.senderDisplay ?: item.sender
        val initial = displayName.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
        AvatarColorResolver.applyTo(holder.avatarText, displayName)
        holder.avatarText.text = initial
        holder.avatarText.visibility = View.VISIBLE
        holder.avatarImage.visibility = View.GONE
    }

    private fun highlightSearchTerms(text: String, query: String, view: View): CharSequence {
        if (query.isBlank()) return text
        
        val spannable = SpannableString(text)
        val highlightColor = ContextCompat.getColor(view.context, R.color.md_theme_light_primaryContainer)
        
        val queryLower = query.lowercase()
        val textLower = text.lowercase()
        
        var startIndex = 0
        while (true) {
            val index = textLower.indexOf(queryLower, startIndex)
            if (index < 0) break
            spannable.setSpan(
                BackgroundColorSpan(highlightColor),
                index,
                index + query.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            startIndex = index + 1
        }
        
        return spannable
    }

    private fun formatDate(timestamp: Long): String {
        val now = Calendar.getInstance()
        val msgTime = Calendar.getInstance().apply { timeInMillis = timestamp }
        
        val hour = if (msgTime.get(Calendar.HOUR) == 0) 12 else msgTime.get(Calendar.HOUR)
        val minute = msgTime.get(Calendar.MINUTE)
        val amPm = if (msgTime.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        val timeStr = String.format("%d:%02d %s", hour, minute, amPm)
        
        return when {
            isSameDay(timestamp, now.timeInMillis) -> {
                timeStr
            }
            isSameDay(timestamp, now.apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis) -> {
                "Yesterday, $timeStr"
            }
            else -> {
                val daysAgo = ((Calendar.getInstance().timeInMillis - msgTime.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
                if (daysAgo < 7) {
                    val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                    "${dayNames[msgTime.get(Calendar.DAY_OF_WEEK) - 1]}, $timeStr"
                } else {
                    val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", 
                                            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                    val day = msgTime.get(Calendar.DAY_OF_MONTH)
                    val month = monthNames[msgTime.get(Calendar.MONTH)]
                    "$day $month, $timeStr"
                }
            }
        }
    }
    
    private fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}

