package com.praveenpuglia.cleansms

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.praveenpuglia.cleansms.R
import java.util.Calendar

class ThreadAdapter(
    private var items: List<ThreadItem>,
    private val onItemClick: (ThreadItem) -> Unit,
    private val onAvatarClick: (ThreadItem) -> Unit,
    private val onAvatarLongPress: (ThreadItem) -> Unit
) : RecyclerView.Adapter<ThreadAdapter.VH>() {

    private var selectionMode: Boolean = false
    private var selectedThreadIds: Set<Long> = emptySet()

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
        val unreadDot: View = itemView.findViewById(R.id.thread_unread_dot)
        val unreadBadge: TextView = itemView.findViewById(R.id.thread_unread_badge)
        val selectionIcon: ImageView = itemView.findViewById(R.id.thread_avatar_selection_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_thread, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        holder.itemView.setOnClickListener { onItemClick(item) }
        val longPressListener = View.OnLongClickListener {
            onAvatarLongPress(item)
            true
        }
        holder.itemView.setOnLongClickListener(longPressListener)
        holder.avatarText.setOnLongClickListener(longPressListener)
        holder.avatarImage.setOnLongClickListener(longPressListener)
        holder.name.text = item.contactName ?: item.nameOrAddress
        holder.date.text = formatHumanReadableDate(item.date)
        holder.snippet.text = item.snippet
        holder.divider.visibility = if (position == itemCount - 1) View.GONE else View.VISIBLE

        val unreadCount = item.unreadCount
        val hasUnread = unreadCount > 0
        holder.unreadDot.visibility = if (hasUnread) View.VISIBLE else View.GONE
        if (hasUnread) {
            holder.unreadBadge.visibility = View.VISIBLE
            holder.unreadBadge.text = unreadCount.toString()
        } else {
            holder.unreadBadge.visibility = View.GONE
        }
        holder.name.setTypeface(null, if (hasUnread) Typeface.BOLD else Typeface.NORMAL)

        val photo = item.contactPhotoUri
        var avatarApplied = false
        if (!photo.isNullOrEmpty()) {
            try {
                holder.avatarImage.setImageURI(photo.toUri())
                holder.avatarImage.visibility = View.VISIBLE
                holder.avatarText.visibility = View.GONE
                avatarApplied = true
            } catch (_: Exception) {
                avatarApplied = false
            }
        }

        if (!avatarApplied) {
            val (label, key) = resolveAvatarLabel(item.contactName, item.nameOrAddress)
            holder.avatarText.text = label
            holder.avatarText.visibility = View.VISIBLE
            holder.avatarImage.visibility = View.GONE
            AvatarColorResolver.applyTo(holder.avatarText, key)
        }

        val canOpenContact = item.hasSavedContact
        val avatarClickListener = View.OnClickListener { onAvatarClick(item) }
        if (selectionMode || canOpenContact) {
            holder.avatarText.setOnClickListener(avatarClickListener)
            holder.avatarImage.setOnClickListener(avatarClickListener)
        } else {
            holder.avatarText.setOnClickListener(null)
            holder.avatarImage.setOnClickListener(null)
        }
        holder.avatarText.isClickable = selectionMode || canOpenContact
        holder.avatarText.isFocusable = selectionMode || canOpenContact
        holder.avatarImage.isClickable = selectionMode || canOpenContact
        holder.avatarImage.isFocusable = selectionMode || canOpenContact

        val isSelected = selectionMode && selectedThreadIds.contains(item.threadId)
    val avatarAlpha = if (isSelected) 0.35f else 1f
    holder.avatarText.alpha = avatarAlpha
    holder.avatarImage.alpha = avatarAlpha
    holder.itemView.isActivated = isSelected
    holder.selectionIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<ThreadItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun updateSelectionState(enabled: Boolean, selectedIds: Set<Long>) {
        val changed = selectionMode != enabled || selectedThreadIds != selectedIds
        selectionMode = enabled
        selectedThreadIds = selectedIds.toSet()
        if (changed) notifyDataSetChanged()
    }

    private fun resolveAvatarLabel(contactName: String?, fallbackIdentifier: String): Pair<String, String> {
        val trimmedName = contactName?.trim().orEmpty()
        val letterFromName = trimmedName.firstOrNull { it.isLetter() }
        if (letterFromName != null) {
            val label = letterFromName.uppercaseChar().toString()
            return label to trimmedName.ifBlank { fallbackIdentifier }
        }

        val trimmedFallback = fallbackIdentifier.trim()
        val fallbackLetter = trimmedFallback.firstOrNull { it.isLetter() }
        if (fallbackLetter != null) {
            val label = fallbackLetter.uppercaseChar().toString()
            return label to fallbackIdentifier
        }

        return "#" to fallbackIdentifier
    }
}
