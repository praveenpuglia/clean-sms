package com.praveenpuglia.cleansms.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.praveenpuglia.cleansms.MessageCategory
import com.praveenpuglia.cleansms.R
import java.text.DateFormat
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

object StatsTestTags {
    const val TOTAL_MESSAGES = "stats_total_messages"
    const val TOTAL_THREADS = "stats_total_threads"
    const val ACTIVITY = "stats_activity"
    const val CATEGORIES = "stats_categories"
    fun period(period: StatsPeriod) = "stats_period_${period.name.lowercase()}"
    fun category(category: MessageCategory) = "stats_category_${category.name.lowercase()}"
}

@Composable
fun StatsScreen(
    records: List<StatsMessageRecord>?,
    loadFailed: Boolean,
    onBack: () -> Unit,
    nowMillis: Long = System.currentTimeMillis(),
) {
    var period by remember { mutableStateOf(StatsPeriod.THIRTY_DAYS) }
    val summary = remember(records, period, nowMillis) {
        records?.let { buildStatsSummary(it, period, nowMillis) }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            StatsHeader(onBack)
            when {
                loadFailed -> StatsMessage(stringResource(R.string.stats_load_failed))
                summary == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> StatsContent(
                    summary = summary,
                    period = period,
                    onPeriodChange = { period = it },
                )
            }
        }
    }
}

@Composable
private fun StatsHeader(onBack: () -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.settings_back),
                )
            }
            Column(Modifier.padding(start = 8.dp)) {
                Text(
                    text = stringResource(R.string.stats_title),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.42.sp,
                )
                Text(
                    text = stringResource(R.string.stats_private_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun StatsContent(
    summary: StatsSummary,
    period: StatsPeriod,
    onPeriodChange: (StatsPeriod) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item { PeriodSelector(period, onPeriodChange) }
        item { Totals(summary) }
        item { ActivitySection(summary) }
        item { CategorySection(summary) }
        item { NumberSection(summary) }
        if (summary.totalMessages > 0) item { InsightSection(summary) }
    }
}

@Composable
private fun PeriodSelector(selected: StatsPeriod, onSelected: (StatsPeriod) -> Unit) {
    val labels = listOf(
        StatsPeriod.SEVEN_DAYS to stringResource(R.string.stats_period_7_days),
        StatsPeriod.THIRTY_DAYS to stringResource(R.string.stats_period_30_days),
        StatsPeriod.NINETY_DAYS to stringResource(R.string.stats_period_90_days),
        StatsPeriod.ALL_TIME to stringResource(R.string.stats_period_all),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, (period, label) ->
            SegmentedButton(
                selected = selected == period,
                onClick = { onSelected(period) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                modifier = Modifier.weight(1f).testTag(StatsTestTags.period(period)),
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun Totals(summary: StatsSummary) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Total(
                value = summary.totalMessages,
                label = stringResource(R.string.stats_messages),
                modifier = Modifier.weight(1f),
                valueModifier = Modifier.testTag(StatsTestTags.TOTAL_MESSAGES),
            )
            Total(
                value = summary.totalThreads,
                label = stringResource(R.string.stats_threads),
                modifier = Modifier.weight(1f),
                valueModifier = Modifier.testTag(StatsTestTags.TOTAL_THREADS),
            )
        }
        summary.oldestMessageDate?.let { oldest ->
            Text(
                text = stringResource(
                    R.string.stats_since,
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(
                        Date.from(oldest.atStartOfDay(ZoneId.systemDefault()).toInstant())
                    ),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Total(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    valueModifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = NumberFormat.getIntegerInstance().format(value),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = valueModifier,
        )
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActivitySection(summary: StatsSummary) {
    Column(Modifier.testTag(StatsTestTags.ACTIVITY)) {
        SectionTitle(
            if (summary.activityIsMonthly) stringResource(R.string.stats_activity_monthly)
            else stringResource(R.string.stats_activity_daily)
        )
        Spacer(Modifier.height(12.dp))
        ActivityChart(summary.activity)
    }
}

@Composable
private fun ActivityChart(points: List<StatsActivityPoint>) {
    val state = rememberLazyListState()
    var selectedIndex by remember(points) { mutableIntStateOf(points.lastIndex.coerceAtLeast(0)) }
    val maxCount = points.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val selected = points.getOrNull(selectedIndex)

    LaunchedEffect(points) {
        if (points.isNotEmpty()) state.scrollToItem(points.lastIndex)
    }

    selected?.let {
        Text(
            text = stringResource(R.string.stats_activity_selection, it.accessibleLabel, NumberFormat.getIntegerInstance().format(it.count)),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth().height(132.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(points, key = { _, point -> point.accessibleLabel }) { index, point ->
            val selectedBar = index == selectedIndex
            val description = stringResource(R.string.stats_activity_selection, point.accessibleLabel, point.count)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { selectedIndex = index }
                    .semantics { contentDescription = description }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .width(18.dp)
                            .height((84.dp * (point.count.toFloat() / maxCount)).coerceAtLeast(3.dp))
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(
                                if (selectedBar) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primaryContainer
                            )
                    )
                }
                Text(
                    point.label,
                    color = if (selectedBar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selectedBar) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun CategorySection(summary: StatsSummary) {
    Column(Modifier.testTag(StatsTestTags.CATEGORIES)) {
        SectionTitle(stringResource(R.string.stats_categories))
        Spacer(Modifier.height(12.dp))
        summary.categories.forEachIndexed { index, stats ->
            val fraction = stats.messages.toFloat() / summary.totalMessages.coerceAtLeast(1)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    categoryLabel(stats.category),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    NumberFormat.getPercentInstance().apply { maximumFractionDigits = 0 }.format(fraction),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                stringResource(R.string.stats_category_counts, stats.messages, stats.threads),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .testTag(StatsTestTags.category(stats.category)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            if (index != summary.categories.lastIndex) Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NumberSection(summary: StatsSummary) {
    Column {
        SectionTitle(stringResource(R.string.stats_more_numbers))
        Spacer(Modifier.height(8.dp))
        MetricRow(stringResource(R.string.stats_received), summary.receivedMessages)
        MetricRow(stringResource(R.string.stats_sent), summary.sentMessages)
        MetricRow(stringResource(R.string.stats_unread), summary.unreadMessages)
        MetricRow(stringResource(R.string.stats_otps), summary.otpMessages)
        MetricRow(stringResource(R.string.stats_spam), summary.spamMessages)
    }
}

@Composable
private fun MetricRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(NumberFormat.getIntegerInstance().format(value), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InsightSection(summary: StatsSummary) {
    Column {
        SectionTitle(stringResource(R.string.stats_habits))
        Spacer(Modifier.height(8.dp))
        summary.busiestDay?.let {
            Insight(stringResource(R.string.stats_busiest_day, dayName(it)))
        }
        summary.busiestHour?.let {
            Insight(stringResource(R.string.stats_busiest_hour, hourRange(it)))
        }
        Insight(
            stringResource(
                R.string.stats_average_per_day,
                NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(summary.averagePerDay),
            )
        )
    }
}

@Composable
private fun Insight(text: String) {
    Text(
        text = "•  $text",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(vertical = 5.dp),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun StatsMessage(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun categoryLabel(category: MessageCategory): String = stringResource(
    when (category) {
        MessageCategory.PERSONAL -> R.string.category_personal
        MessageCategory.TRANSACTIONAL -> R.string.category_transactions
        MessageCategory.SERVICE -> R.string.category_service
        MessageCategory.PROMOTIONAL -> R.string.category_promotions
        MessageCategory.GOVERNMENT -> R.string.category_government
        MessageCategory.UNKNOWN -> R.string.category_unknown
    }
)

private fun dayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, Locale.getDefault())

private fun hourRange(hour: Int): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.now().atZone(zone).toLocalDate()
    val formatter = DateFormat.getTimeInstance(DateFormat.SHORT)
    fun formatted(value: Int): String = formatter.format(
        Date.from(date.atTime(LocalTime.of(value % 24, 0)).atZone(zone).toInstant())
    )
    return "${formatted(hour)}–${formatted(hour + 1)}"
}
