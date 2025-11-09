package com.praveenpuglia.cleansms

import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.*

class MessageAdapter(private var messages: List<Message>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_INCOMING = 1
        const val VIEW_TYPE_OUTGOING = 2
    }

    class IncomingVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val body: TextView = itemView.findViewById(R.id.message_body)
        val time: TextView = itemView.findViewById(R.id.message_time)
        val simIndicator: View = itemView.findViewById(R.id.message_sim_indicator)
        val simSlotText: TextView = itemView.findViewById(R.id.message_sim_slot)
        val spamBadge: View = itemView.findViewById(R.id.message_spam_badge)
    }

    class OutgoingVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val body: TextView = itemView.findViewById(R.id.message_body)
        val time: TextView = itemView.findViewById(R.id.message_time)
        val deliveryStatus: ImageView = itemView.findViewById(R.id.message_delivery_status)
        val simIndicator: View = itemView.findViewById(R.id.message_sim_indicator)
        val simSlotText: TextView = itemView.findViewById(R.id.message_sim_slot)
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].type == 1) VIEW_TYPE_INCOMING else VIEW_TYPE_OUTGOING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_INCOMING) {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_message_incoming, parent, false)
            IncomingVH(v)
        } else {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_message_outgoing, parent, false)
            OutgoingVH(v)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        val timeStr = formatMessageTime(msg.date)
        
        // Check for spam (but keep the full message text)
        val isSpam = SpamDetector.isSpam(msg.body)

        // Common binding logic extracted
        fun bindCommon(bodyView: TextView, timeView: TextView, simIndicator: View, simSlotText: TextView) {
            bodyView.text = msg.body
            // Apply linkification after setting text (web/email/phone). Using framework Linkify via movement method for accessibility.
            LinkifyUtil.linkify(bodyView)
            timeView.text = timeStr
            // Hide SIM indicator in thread detail view
            simIndicator.visibility = View.GONE
        }
        when (holder) {
            is IncomingVH -> {
                bindCommon(holder.body, holder.time, holder.simIndicator, holder.simSlotText)
                // Show spam badge for incoming spam messages
                holder.spamBadge.visibility = if (isSpam) View.VISIBLE else View.GONE
            }
            is OutgoingVH -> {
                bindCommon(holder.body, holder.time, holder.simIndicator, holder.simSlotText)
                // Show delivery status for sent messages (type 2)
                // SMS status values: -1 = no status/default, 0 = complete (sent successfully), 32 = pending, 64 = failed
                // Note: Android SMS database doesn't track "delivered" separately - that requires delivery reports
                // For sent messages (type 2), show single tick unless failed
                when (msg.status) {
                    64 -> {
                        // Message failed - hide tick
                        holder.deliveryStatus.visibility = View.GONE
                    }
                    else -> {
                        // For all other cases (sent, pending, or no status), show single tick
                        // If a message is in the sent folder, it means it was sent successfully
                        holder.deliveryStatus.setImageResource(R.drawable.ic_tick_single)
                        holder.deliveryStatus.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    private fun formatMessageTime(timestamp: Long): String {
        val now = Calendar.getInstance()
        val msgTime = Calendar.getInstance().apply { timeInMillis = timestamp }
        
        val isToday = now.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR) &&
                      now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR)
        
        val hour = if (msgTime.get(Calendar.HOUR) == 0) 12 else msgTime.get(Calendar.HOUR)
        val minute = msgTime.get(Calendar.MINUTE)
        val amPm = if (msgTime.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        val timeOnly = String.format("%d:%02d %s", hour, minute, amPm)
        
        return if (isToday) {
            timeOnly
        } else {
            val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", 
                                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val day = msgTime.get(Calendar.DAY_OF_MONTH)
            val month = monthNames[msgTime.get(Calendar.MONTH)]
            "$day $month, $timeOnly"
        }
    }

    fun updateMessages(newMessages: List<Message>) {
        messages = newMessages
        notifyDataSetChanged()
    }
}
