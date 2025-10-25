package com.praveenpuglia.cleansms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.DateFormat
import java.util.*
import androidx.core.net.toUri

class ThreadAdapter(
    private var items: List<ThreadItem>,
    private val onItemClick: (ThreadItem) -> Unit
) : RecyclerView.Adapter<ThreadAdapter.VH>() {

    private fun formatHumanReadableDate(timestamp: Long): String {
        val now = Calendar.getInstance()
        val msgTime = Calendar.getInstance().apply { timeInMillis = timestamp }
        
        val daysDiff = ((now.timeInMillis - msgTime.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
        
        return when {
            daysDiff == 0 && now.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR) -> {
                // Today - show only time
                String.format("%02d:%02d %s", 
                    if (msgTime.get(Calendar.HOUR) == 0) 12 else msgTime.get(Calendar.HOUR),
                    msgTime.get(Calendar.MINUTE),
                    if (msgTime.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM")
            }
            daysDiff == 1 || (now.get(Calendar.DAY_OF_YEAR) - msgTime.get(Calendar.DAY_OF_YEAR) == 1) -> {
                // Yesterday
                "Yesterday"
            }
            daysDiff < 7 -> {
                // Within last week - show day name
                val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                dayNames[msgTime.get(Calendar.DAY_OF_WEEK) - 1]
            }
            else -> {
                // Older - show date like "20 Oct"
                val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", 
                                        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                val day = msgTime.get(Calendar.DAY_OF_MONTH)
                val month = monthNames[msgTime.get(Calendar.MONTH)]
                "$day $month"
            }
        }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatarImage: ImageView = itemView.findViewById(R.id.thread_avatar_image)
        val avatarText: TextView = itemView.findViewById(R.id.thread_avatar_text)
        val name: TextView = itemView.findViewById(R.id.thread_name)
        val date: TextView = itemView.findViewById(R.id.thread_date)
        val snippet: TextView = itemView.findViewById(R.id.thread_snippet)
        val divider: View = itemView.findViewById(R.id.thread_divider)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_thread, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
        holder.name.text = item.contactName ?: item.nameOrAddress
        holder.date.text = formatHumanReadableDate(item.date)
        holder.snippet.text = item.snippet
    holder.divider.visibility = if (position == itemCount - 1) View.GONE else View.VISIBLE

        // If MainActivity enriched with photoUri/name, use them
        val photo = item.contactPhotoUri
        val contactName = item.contactName

        if (!photo.isNullOrEmpty()) {
            try {
                holder.avatarImage.setImageURI(photo.toUri())
                holder.avatarImage.visibility = View.VISIBLE
                holder.avatarText.visibility = View.GONE
                return
            } catch (_: Exception) {
                // fall through to initials
            }
        }

        if (!contactName.isNullOrEmpty()) {
            val initial = contactName.trim().firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
            holder.avatarText.text = initial
            holder.avatarText.visibility = View.VISIBLE
            holder.avatarImage.visibility = View.GONE
            return
        }

        // Fallback: if the nameOrAddress contains letters (alphanumeric sender ID), show first letter
        val raw = item.nameOrAddress
        val hasLetters = raw.any { it.isLetter() }
        if (hasLetters) {
            val firstLetter = raw.trim().firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
            holder.avatarText.text = firstLetter
            holder.avatarText.visibility = View.VISIBLE
            holder.avatarImage.visibility = View.GONE
            return
        }

        // Final fallback
        holder.avatarText.text = "#"
        holder.avatarText.visibility = View.VISIBLE
        holder.avatarImage.visibility = View.GONE
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<ThreadItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
