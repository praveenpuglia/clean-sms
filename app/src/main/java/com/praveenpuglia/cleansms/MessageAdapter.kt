package com.praveenpuglia.cleansms

import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private var items: List<MessageListItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_INCOMING = 1
        const val VIEW_TYPE_OUTGOING = 2
        const val VIEW_TYPE_DAY_INDICATOR = 3
    }

    class IncomingVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val body: TextView = itemView.findViewById(R.id.message_body)
        val time: TextView = itemView.findViewById(R.id.message_time)
        val simIndicator: View = itemView.findViewById(R.id.message_sim_indicator)
        val simSlotText: TextView = itemView.findViewById(R.id.message_sim_slot)
        val spamBadge: View = itemView.findViewById(R.id.message_spam_badge)
        // Railway preview views
        val railwayPreview: View = itemView.findViewById(R.id.railway_preview_container)
        val railwayPnr: TextView = itemView.findViewById(R.id.railway_pnr)
        val railwayTrainNumber: TextView = itemView.findViewById(R.id.railway_train_number)
        val railwayDate: TextView = itemView.findViewById(R.id.railway_date)
        val railwayClass: TextView = itemView.findViewById(R.id.railway_class)
        val railwayClassContainer: View = itemView.findViewById(R.id.railway_class_container)
        val railwayFrom: TextView = itemView.findViewById(R.id.railway_from)
        val railwayTo: TextView = itemView.findViewById(R.id.railway_to)
        val railwayRouteContainer: View = itemView.findViewById(R.id.railway_route_container)
        val railwaySeats: TextView = itemView.findViewById(R.id.railway_seats)
        val railwayBoarding: TextView = itemView.findViewById(R.id.railway_boarding)
        val railwayChartStatus: TextView = itemView.findViewById(R.id.railway_chart_status)
    }

    class OutgoingVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val body: TextView = itemView.findViewById(R.id.message_body)
        val time: TextView = itemView.findViewById(R.id.message_time)
        val deliveryStatus: ImageView = itemView.findViewById(R.id.message_delivery_status)
        val simIndicator: View = itemView.findViewById(R.id.message_sim_indicator)
        val simSlotText: TextView = itemView.findViewById(R.id.message_sim_slot)
        // Railway preview views
        val railwayPreview: View = itemView.findViewById(R.id.railway_preview_container)
        val railwayPnr: TextView = itemView.findViewById(R.id.railway_pnr)
        val railwayTrainNumber: TextView = itemView.findViewById(R.id.railway_train_number)
        val railwayDate: TextView = itemView.findViewById(R.id.railway_date)
        val railwayClass: TextView = itemView.findViewById(R.id.railway_class)
        val railwayClassContainer: View = itemView.findViewById(R.id.railway_class_container)
        val railwayFrom: TextView = itemView.findViewById(R.id.railway_from)
        val railwayTo: TextView = itemView.findViewById(R.id.railway_to)
        val railwayRouteContainer: View = itemView.findViewById(R.id.railway_route_container)
        val railwaySeats: TextView = itemView.findViewById(R.id.railway_seats)
        val railwayBoarding: TextView = itemView.findViewById(R.id.railway_boarding)
        val railwayChartStatus: TextView = itemView.findViewById(R.id.railway_chart_status)
    }

    class DayIndicatorVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.day_indicator_text)
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = items[position]) {
            is MessageListItem.DayIndicator -> VIEW_TYPE_DAY_INDICATOR
            is MessageListItem.MessageItem -> {
                if (item.message.type == 1) VIEW_TYPE_INCOMING else VIEW_TYPE_OUTGOING
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_DAY_INDICATOR -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_day_indicator, parent, false)
                DayIndicatorVH(v)
            }
            VIEW_TYPE_INCOMING -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_message_incoming, parent, false)
                IncomingVH(v)
            }
            else -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_message_outgoing, parent, false)
                OutgoingVH(v)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is MessageListItem.DayIndicator -> {
                (holder as DayIndicatorVH).text.text = item.label
            }
            is MessageListItem.MessageItem -> {
                val msg = item.message
                val timeStr = formatMessageTime(msg.date)
                
                // Check for spam (but keep the full message text)
                val isSpam = SpamDetector.isSpam(msg.body)

                // Common binding logic extracted
                fun bindCommon(bodyView: TextView, timeView: TextView, simIndicator: View, simSlotText: TextView) {
                    bodyView.text = msg.body
                    // Enable long-press text selection
                    bodyView.setTextIsSelectable(true)
                    // Apply linkification after setting text (web/email/phone)
                    LinkifyUtil.linkify(bodyView)
                    timeView.text = timeStr
                    // Show SIM indicator if we have SIM slot info
                    if (msg.simSlot != null) {
                        simIndicator.visibility = View.VISIBLE
                        simSlotText.text = msg.simSlot.toString()
                    } else {
                        simIndicator.visibility = View.GONE
                    }
                }
                // Check for railway message
                val railwayInfo = RailwayMessageParser.parse(msg.body)
                
                when (holder) {
                    is IncomingVH -> {
                        bindCommon(holder.body, holder.time, holder.simIndicator, holder.simSlotText)
                        // Show spam badge for incoming spam messages
                        holder.spamBadge.visibility = if (isSpam) View.VISIBLE else View.GONE
                        // Bind railway preview
                        bindRailwayPreview(
                            railwayInfo,
                            holder.railwayPreview,
                            holder.railwayPnr,
                            holder.railwayTrainNumber,
                            holder.railwayDate,
                            holder.railwayClass,
                            holder.railwayClassContainer,
                            holder.railwayFrom,
                            holder.railwayTo,
                            holder.railwayRouteContainer,
                            holder.railwaySeats,
                            holder.railwayBoarding,
                            holder.railwayChartStatus
                        )
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
                        // Bind railway preview
                        bindRailwayPreview(
                            railwayInfo,
                            holder.railwayPreview,
                            holder.railwayPnr,
                            holder.railwayTrainNumber,
                            holder.railwayDate,
                            holder.railwayClass,
                            holder.railwayClassContainer,
                            holder.railwayFrom,
                            holder.railwayTo,
                            holder.railwayRouteContainer,
                            holder.railwaySeats,
                            holder.railwayBoarding,
                            holder.railwayChartStatus
                        )
                    }
                }
            }
        }
    }
    
    private fun bindRailwayPreview(
        info: RailwayInfo?,
        container: View,
        pnrView: TextView,
        trainNumberView: TextView,
        dateView: TextView,
        classView: TextView,
        classContainer: View,
        fromView: TextView,
        toView: TextView,
        routeContainer: View,
        seatsView: TextView,
        boardingView: TextView,
        chartStatusView: TextView
    ) {
        if (info == null) {
            container.visibility = View.GONE
            return
        }
        
        container.visibility = View.VISIBLE
        
        // PNR
        pnrView.text = "PNR: ${info.pnr}"
        
        // Train number
        if (info.trainNumber != null) {
            trainNumberView.text = info.trainNumber
            trainNumberView.visibility = View.VISIBLE
        } else {
            trainNumberView.text = "—"
        }
        
        // Date
        if (info.date != null) {
            dateView.text = info.date
            dateView.visibility = View.VISIBLE
        } else {
            dateView.text = "—"
        }
        
        // Class
        if (info.travelClass != null) {
            classView.text = info.travelClass
            classContainer.visibility = View.VISIBLE
        } else {
            classContainer.visibility = View.GONE
        }
        
        // Route
        if (info.fromStation != null && info.toStation != null) {
            fromView.text = info.fromStation
            toView.text = info.toStation
            routeContainer.visibility = View.VISIBLE
        } else {
            routeContainer.visibility = View.GONE
        }
        
        // Seats/Passengers
        if (info.passengers.isNotEmpty()) {
            val seatsText = info.passengers.joinToString(" • ") { p ->
                val statusPrefix = when (p.status) {
                    PassengerStatus.RAC -> "RAC "
                    PassengerStatus.WAITLIST -> "WL "
                    PassengerStatus.CONFIRMED -> ""
                }
                when {
                    p.coach != null && p.seat != null -> "P${p.passengerNumber}: $statusPrefix${p.coach}/${p.seat}"
                    p.seat != null -> "P${p.passengerNumber}: $statusPrefix#${p.seat}"
                    else -> "P${p.passengerNumber}: ${statusPrefix}Waiting"
                }
            }
            seatsView.text = seatsText
            seatsView.visibility = View.VISIBLE
        } else {
            seatsView.visibility = View.GONE
        }
        
        // Boarding station (if different from origin)
        if (info.boardingStation != null && info.boardingStation != info.fromStation) {
            boardingView.text = "⚠ Board from ${info.boardingStation}"
            boardingView.visibility = View.VISIBLE
        } else {
            boardingView.visibility = View.GONE
        }
        
        // Chart status
        if (info.chartStatus != null) {
            chartStatusView.text = if (info.chartStatus.contains("Prepared", ignoreCase = true)) "CONFIRMED" else "WAITING"
            chartStatusView.visibility = View.VISIBLE
        } else {
            chartStatusView.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = items.size

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
        items = createItemsWithDayIndicators(newMessages)
        notifyDataSetChanged()
    }

    private fun createItemsWithDayIndicators(messages: List<Message>): List<MessageListItem> {
        if (messages.isEmpty()) return emptyList()
        
        val result = mutableListOf<MessageListItem>()
        var lastDay: String? = null
        val now = Calendar.getInstance()
        val today = Calendar.getInstance()
        
        // Skip day indicator for the most recent day (today)
        val skipFirstIndicator = messages.isNotEmpty() && isSameDay(messages.last().date, today.timeInMillis)
        var isFirstIndicator = true
        
        for (message in messages) {
            val messageDay = getDayKey(message.date)
            
            if (lastDay != messageDay) {
                // Don't add indicator for the first day if it's today
                if (!isFirstIndicator || !skipFirstIndicator) {
                    val label = formatDayLabel(message.date, now)
                    result.add(MessageListItem.DayIndicator(message.date, label))
                }
                lastDay = messageDay
                isFirstIndicator = false
            }
            
            result.add(MessageListItem.MessageItem(message))
        }
        
        return result
    }

    private fun getDayKey(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun formatDayLabel(timestamp: Long, now: Calendar): String {
        val msgTime = Calendar.getInstance().apply { timeInMillis = timestamp }
        
        val isToday = now.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR) &&
                      now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR)
        
        if (isToday) {
            return "Today"
        }
        
        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val isYesterday = yesterday.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR) &&
                          yesterday.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR)
        
        if (isYesterday) {
            return "Yesterday"
        }
        
        // Check if within last week - show day name
        val daysAgo = ((now.timeInMillis - msgTime.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
        if (daysAgo < 7) {
            val dayNames = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
            return dayNames[msgTime.get(Calendar.DAY_OF_WEEK) - 1]
        }
        
        // Otherwise show date in "20th Sep, 2025" format
        val dayOfMonth = msgTime.get(Calendar.DAY_OF_MONTH)
        val suffix = when (dayOfMonth % 10) {
            1 -> if (dayOfMonth == 11) "th" else "st"
            2 -> if (dayOfMonth == 12) "th" else "nd"
            3 -> if (dayOfMonth == 13) "th" else "rd"
            else -> "th"
        }
        
        val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", 
                                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val month = monthNames[msgTime.get(Calendar.MONTH)]
        val year = msgTime.get(Calendar.YEAR)
        
        return "$dayOfMonth$suffix $month, $year"
    }

    fun getHeaderPositionForItem(itemPosition: Int): Int {
        // Find the most recent day indicator at or before this position
        for (i in itemPosition downTo 0) {
            if (items[i] is MessageListItem.DayIndicator) {
                return i
            }
        }
        return RecyclerView.NO_POSITION
    }

    fun isHeader(position: Int): Boolean {
        return position in items.indices && items[position] is MessageListItem.DayIndicator
    }

    /**
     * Find the adapter position for a message with the given ID.
     * Returns -1 if not found.
     */
    fun getPositionForMessageId(messageId: Long): Int {
        return items.indexOfFirst { item ->
            item is MessageListItem.MessageItem && item.message.id == messageId
        }
    }

    /**
     * Get the position of the last message (not day indicator).
     * Returns -1 if no messages exist.
     */
    fun getLastMessagePosition(): Int {
        return items.indexOfLast { it is MessageListItem.MessageItem }
    }
}

