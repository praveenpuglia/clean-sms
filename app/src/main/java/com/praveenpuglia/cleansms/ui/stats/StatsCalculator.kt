package com.praveenpuglia.cleansms.ui.stats

import com.praveenpuglia.cleansms.MessageCategory
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class StatsPeriod(val days: Long?) {
    SEVEN_DAYS(7),
    THIRTY_DAYS(30),
    NINETY_DAYS(90),
    ALL_TIME(null),
}

data class StatsMessageRecord(
    val threadId: Long,
    val date: Long,
    val type: Int,
    val isUnread: Boolean,
    val category: MessageCategory,
    val isOtp: Boolean,
    val isSpam: Boolean,
)

data class StatsActivityPoint(
    val label: String,
    val accessibleLabel: String,
    val count: Int,
)

data class CategoryStats(
    val category: MessageCategory,
    val messages: Int,
    val threads: Int,
)

data class StatsSummary(
    val totalMessages: Int,
    val totalThreads: Int,
    val receivedMessages: Int,
    val sentMessages: Int,
    val unreadMessages: Int,
    val otpMessages: Int,
    val spamMessages: Int,
    val averagePerDay: Double,
    val oldestMessageDate: LocalDate?,
    val busiestDay: DayOfWeek?,
    val busiestHour: Int?,
    val activity: List<StatsActivityPoint>,
    val activityIsMonthly: Boolean,
    val categories: List<CategoryStats>,
)

private val categoryOrder = listOf(
    MessageCategory.PERSONAL,
    MessageCategory.TRANSACTIONAL,
    MessageCategory.SERVICE,
    MessageCategory.PROMOTIONAL,
    MessageCategory.GOVERNMENT,
    MessageCategory.UNKNOWN,
)

fun buildStatsSummary(
    records: List<StatsMessageRecord>,
    period: StatsPeriod,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): StatsSummary {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val firstDay = period.days?.let { today.minusDays(it - 1) }
    val filtered = records.filter { record ->
        if (record.date > nowMillis) return@filter false
        firstDay == null || !dateOf(record, zoneId).isBefore(firstDay)
    }
    val categoryThreads = categoryOrder.associateWith { mutableSetOf<Long>() }
    val categoryMessages = categoryOrder.associateWith { 0 }.toMutableMap()

    filtered.forEach { record ->
        categoryMessages[record.category] = categoryMessages.getValue(record.category) + 1
        categoryThreads.getValue(record.category) += record.threadId
    }

    val oldestDate = filtered.minOfOrNull { it.date }?.let {
        Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()
    }
    val dayCount = period.days ?: oldestDate?.let {
        ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) + 1
    } ?: 1
    val byDayOfWeek = filtered.groupingBy { dateOf(it, zoneId).dayOfWeek }.eachCount()
    val byHour = filtered.groupingBy {
        Instant.ofEpochMilli(it.date).atZone(zoneId).hour
    }.eachCount()

    return StatsSummary(
        totalMessages = filtered.size,
        totalThreads = filtered.mapTo(mutableSetOf()) { it.threadId }.size,
        receivedMessages = filtered.count { it.type == 1 },
        sentMessages = filtered.count { it.type == 2 },
        unreadMessages = filtered.count { it.isUnread },
        otpMessages = filtered.count { it.isOtp },
        spamMessages = filtered.count { it.isSpam },
        averagePerDay = filtered.size.toDouble() / dayCount,
        oldestMessageDate = oldestDate,
        busiestDay = DayOfWeek.entries.maxByOrNull { byDayOfWeek[it] ?: 0 }
            ?.takeIf { filtered.isNotEmpty() },
        busiestHour = (0..23).maxByOrNull { byHour[it] ?: 0 }
            ?.takeIf { filtered.isNotEmpty() },
        activity = buildActivity(filtered, period, today, oldestDate, zoneId),
        activityIsMonthly = period == StatsPeriod.ALL_TIME,
        categories = categoryOrder.mapNotNull { category ->
            val messages = categoryMessages.getValue(category)
            if (messages == 0) null else CategoryStats(category, messages, categoryThreads.getValue(category).size)
        },
    )
}

private fun buildActivity(
    records: List<StatsMessageRecord>,
    period: StatsPeriod,
    today: LocalDate,
    oldestDate: LocalDate?,
    zoneId: ZoneId,
): List<StatsActivityPoint> {
    if (period == StatsPeriod.ALL_TIME) {
        val firstMonth = oldestDate?.let(YearMonth::from) ?: YearMonth.from(today)
        val lastMonth = YearMonth.from(today)
        val counts = records.groupingBy { YearMonth.from(dateOf(it, zoneId)) }.eachCount()
        return generateSequence(firstMonth) { month ->
            month.plusMonths(1).takeIf { !it.isAfter(lastMonth) }
        }.map { month ->
            StatsActivityPoint(
                label = month.format(DateTimeFormatter.ofPattern("MMM")),
                accessibleLabel = month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                count = counts[month] ?: 0,
            )
        }.toList()
    }

    val firstDay = today.minusDays(period.days!! - 1)
    val counts = records.groupingBy { dateOf(it, zoneId) }.eachCount()
    return generateSequence(firstDay) { day ->
        day.plusDays(1).takeIf { !it.isAfter(today) }
    }.map { day ->
        StatsActivityPoint(
            label = day.dayOfMonth.toString(),
            accessibleLabel = day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
            count = counts[day] ?: 0,
        )
    }.toList()
}

private fun dateOf(record: StatsMessageRecord, zoneId: ZoneId): LocalDate =
    Instant.ofEpochMilli(record.date).atZone(zoneId).toLocalDate()
