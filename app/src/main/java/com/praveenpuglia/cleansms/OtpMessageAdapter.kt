package com.praveenpuglia.cleansms

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.praveenpuglia.cleansms.R
import java.util.Calendar

class OtpMessageAdapter(
    private var items: List<OtpMessageItem>,
    private val onItemClick: (OtpMessageItem) -> Unit,
    private val onAvatarClick: (OtpMessageItem) -> Unit,
    private val onAvatarLongPress: (OtpMessageItem) -> Unit
) : RecyclerView.Adapter<OtpMessageAdapter.VH>() {

    private var selectionMode: Boolean = false
    private var selectedMessageIds: Set<Long> = emptySet()

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatarImage: ImageView = itemView.findViewById(R.id.otp_avatar_image)
        val avatarText: TextView = itemView.findViewById(R.id.otp_avatar_text)
        val unreadDot: View = itemView.findViewById(R.id.otp_unread_dot)
        val senderName: TextView = itemView.findViewById(R.id.otp_sender_name)
        val messageDate: TextView = itemView.findViewById(R.id.otp_message_date)
        val messagePreview: TextView = itemView.findViewById(R.id.otp_message_preview)
        val otpCode: TextView = itemView.findViewById(R.id.otp_code)
        val divider: View = itemView.findViewById(R.id.otp_divider)
        val selectionIcon: ImageView = itemView.findViewById(R.id.otp_avatar_selection_icon)
        val simIndicator: View = itemView.findViewById(R.id.otp_sim_indicator)
        val simSlotText: TextView = itemView.findViewById(R.id.otp_sim_slot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_otp_message, parent, false)
        return VH(view)
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
        holder.senderName.text = item.contactName ?: item.address
        holder.senderName.setTypeface(null, if (item.isUnread) Typeface.BOLD else Typeface.NORMAL)
        holder.messageDate.text = formatHumanReadableDate(item.date)
        val preview = item.body.trim()
        holder.messagePreview.text = preview
        holder.messagePreview.visibility = if (preview.isNotEmpty()) View.VISIBLE else View.GONE
        val code = item.otpCode.trim()
        holder.otpCode.text = code
        val hasCode = code.isNotBlank()
        holder.otpCode.visibility = if (hasCode) View.VISIBLE else View.GONE
        holder.unreadDot.visibility = if (item.isUnread) View.VISIBLE else View.GONE
        if (item.simSlot != null || item.subscriptionId != null) {
            holder.simIndicator.visibility = View.VISIBLE
            holder.simSlotText.text = (item.simSlot ?: "?").toString()
        } else {
            holder.simIndicator.visibility = View.GONE
        }
    val codeEnabled = hasCode && !selectionMode
    holder.otpCode.isEnabled = codeEnabled
    holder.otpCode.isClickable = codeEnabled
    holder.otpCode.isFocusable = codeEnabled
        holder.otpCode.setOnClickListener {
            if (selectionMode) {
                onItemClick(item)
                return@setOnClickListener
            }
            if (!hasCode) return@setOnClickListener
            val context = holder.itemView.context
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
            clipboard?.setPrimaryClip(ClipData.newPlainText("OTP", code))
            Toast.makeText(context, context.getString(R.string.toast_otp_copied), Toast.LENGTH_SHORT).show()
        }

        // OTP messages are service messages - only enable avatar click for saved contacts or selection mode
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
        holder.divider.visibility = if (position == itemCount - 1) View.GONE else View.VISIBLE

        bindAvatar(holder, item)

        val isSelected = selectionMode && selectedMessageIds.contains(item.messageId)
        val avatarAlpha = if (isSelected) 0.35f else 1f
        holder.avatarImage.alpha = avatarAlpha
        holder.avatarText.alpha = avatarAlpha
        holder.selectionIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.itemView.isActivated = isSelected
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<OtpMessageItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun updateSelectionState(enabled: Boolean, selectedIds: Set<Long>) {
        val changed = selectionMode != enabled || selectedMessageIds != selectedIds
        selectionMode = enabled
        selectedMessageIds = selectedIds.toSet()
        if (changed) notifyDataSetChanged()
    }

    private fun bindAvatar(holder: VH, item: OtpMessageItem) {
        val photo = item.contactPhotoUri
        val contactName = item.contactName

        if (!photo.isNullOrEmpty()) {
            try {
                holder.avatarImage.setImageURI(photo.toUri())
                holder.avatarImage.visibility = View.VISIBLE
                holder.avatarText.visibility = View.GONE
                return
            } catch (_: Exception) {
                // ignore and fall back to initials
            }
        }

        val (label, key) = resolveAvatarLabel(contactName, item.address)
        holder.avatarText.text = label
        holder.avatarText.visibility = View.VISIBLE
        holder.avatarImage.visibility = View.GONE
        AvatarColorResolver.applyTo(holder.avatarText, key)
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

    private fun formatHumanReadableDate(timestamp: Long): String {
        val now = Calendar.getInstance()
        val msgTime = Calendar.getInstance().apply { timeInMillis = timestamp }

        val daysDiff = ((now.timeInMillis - msgTime.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()

        // Format time component
        val hour = if (msgTime.get(Calendar.HOUR) == 0) 12 else msgTime.get(Calendar.HOUR)
        val minute = msgTime.get(Calendar.MINUTE)
        val amPm = if (msgTime.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        val timeStr = String.format("%d:%02d %s", hour, minute, amPm)

        return when {
            daysDiff == 0 && now.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR) -> {
                // Today - show only time
                timeStr
            }
            daysDiff == 1 || (now.get(Calendar.DAY_OF_YEAR) - msgTime.get(Calendar.DAY_OF_YEAR) == 1) -> {
                // Yesterday - show "Yesterday, 9:30 PM"
                "Yesterday, $timeStr"
            }
            daysDiff < 7 -> {
                // Within last week - show "Sun, 9:30 PM"
                val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                "${dayNames[msgTime.get(Calendar.DAY_OF_WEEK) - 1]}, $timeStr"
            }
            else -> {
                // Older - show "24 Nov, 10:10 AM"
                val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                val day = msgTime.get(Calendar.DAY_OF_MONTH)
                val month = monthNames[msgTime.get(Calendar.MONTH)]
                "$day $month, $timeStr"
            }
        }
    }
}
