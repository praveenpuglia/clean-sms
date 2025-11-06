package com.praveenpuglia.cleansms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    }

    class OutgoingVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val body: TextView = itemView.findViewById(R.id.message_body)
        val time: TextView = itemView.findViewById(R.id.message_time)
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

        when (holder) {
            is IncomingVH -> {
                holder.body.text = msg.body
                holder.time.text = timeStr
                if (msg.simSlot != null || msg.subscriptionId != null) {
                    holder.simIndicator.visibility = View.VISIBLE
                    holder.simSlotText.text = (msg.simSlot ?: "?").toString()
                } else {
                    holder.simIndicator.visibility = View.GONE
                }
            }
            is OutgoingVH -> {
                holder.body.text = msg.body
                holder.time.text = timeStr
                if (msg.simSlot != null || msg.subscriptionId != null) {
                    holder.simIndicator.visibility = View.VISIBLE
                    holder.simSlotText.text = (msg.simSlot ?: "?").toString()
                } else {
                    holder.simIndicator.visibility = View.GONE
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
