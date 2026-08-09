package com.praveenpuglia.cleansms.ui.thread

import android.text.style.URLSpan
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.praveenpuglia.cleansms.AvatarColorResolver
import com.praveenpuglia.cleansms.LinkifyUtil
import com.praveenpuglia.cleansms.Message
import com.praveenpuglia.cleansms.MessageCategory
import com.praveenpuglia.cleansms.MessageListItem
import com.praveenpuglia.cleansms.R
import com.praveenpuglia.cleansms.SpamDetector
import com.praveenpuglia.cleansms.ui.ComposerTopShadow
import com.praveenpuglia.cleansms.ui.ContactAvatar
import com.praveenpuglia.cleansms.ui.SimIndicator
import com.praveenpuglia.cleansms.ui.SimSelectorButton
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

object ThreadDetailTestTags {
    const val CONTACT_NAME = "thread_contact_name"
    const val MESSAGE_LIST = "thread_message_list"
    const val COMPOSER = "thread_composer"
    const val COMPOSER_INPUT = "thread_composer_input"
    const val SEND = "thread_send"
    fun message(id: Long) = "thread_message_$id"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThreadDetailScreen(
    contactName: String?,
    contactAddress: String?,
    contactPhotoUri: String?,
    category: MessageCategory,
    messages: List<Message>,
    messageText: String,
    showComposer: Boolean,
    focusComposer: Boolean,
    selectedSimNumber: Int?,
    showSimSelector: Boolean,
    highlightedMessageId: Long?,
    scrollRequest: Int,
    onBack: () -> Unit,
    onAvatarClick: () -> Unit,
    onCall: () -> Unit,
    onMessageChange: (String) -> Unit,
    onSimToggle: () -> Unit,
    onSend: () -> Unit,
    onHighlightFinished: () -> Unit,
) {
    val listItems = remember(messages) { createMessageListItems(messages) }
    val listState = rememberLazyListState()

    LaunchedEffect(scrollRequest) {
        if (listItems.isEmpty()) return@LaunchedEffect
        val targetIndex = highlightedMessageId?.let { id ->
            listItems.indexOfFirst { it is MessageListItem.MessageItem && it.message.id == id }
                .takeIf { it >= 0 }
        }
        listState.scrollToItem(targetIndex ?: listItems.lastIndex)
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            ThreadHeader(
                contactName = contactName,
                contactAddress = contactAddress,
                contactPhotoUri = contactPhotoUri,
                showCall = category == MessageCategory.PERSONAL && !contactAddress.isNullOrEmpty(),
                onBack = onBack,
                onAvatarClick = onAvatarClick,
                onCall = onCall,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag(ThreadDetailTestTags.MESSAGE_LIST),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 8.dp,
                    top = 4.dp,
                    end = 8.dp,
                    bottom = 8.dp,
                ),
                verticalArrangement = Arrangement.Bottom,
            ) {
                listItems.forEachIndexed { index, item ->
                    when (item) {
                        is MessageListItem.DayIndicator -> stickyHeader(key = "day:${item.timestamp}:$index") {
                            DayIndicator(item.label)
                        }
                        is MessageListItem.MessageItem -> item(key = "message:${item.message.id}") {
                            MessageBubble(
                                message = item.message,
                                highlighted = item.message.id == highlightedMessageId,
                                onHighlightFinished = onHighlightFinished,
                            )
                        }
                    }
                }
            }
            if (showComposer) {
                MessageComposer(
                    message = messageText,
                    focusComposer = focusComposer,
                    selectedSimNumber = selectedSimNumber,
                    showSimSelector = showSimSelector,
                    onMessageChange = onMessageChange,
                    onSimToggle = onSimToggle,
                    onSend = onSend,
                )
            }
        }
    }
}

@Composable
private fun ThreadHeader(
    contactName: String?,
    contactAddress: String?,
    contactPhotoUri: String?,
    showCall: Boolean,
    onBack: () -> Unit,
    onAvatarClick: () -> Unit,
    onCall: () -> Unit,
) {
    val label = contactName ?: contactAddress ?: stringResource(R.string.thread_unknown_contact)
    val context = LocalContext.current
    val (avatarBackground, avatarForeground) = remember(label) { AvatarColorResolver.resolve(context, label) }
    Surface {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.settings_back))
                }
                val avatarDescription = stringResource(R.string.thread_contact_avatar)
                ContactAvatar(
                    label = label,
                    photoUri = contactPhotoUri,
                    size = 48.dp,
                    background = Color(avatarBackground),
                    foreground = Color(avatarForeground),
                    modifier = Modifier
                        .clickable(onClick = onAvatarClick)
                        .semantics { contentDescription = avatarDescription },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        label,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(ThreadDetailTestTags.CONTACT_NAME),
                    )
                    if (contactName != null && contactAddress != null) {
                        Text(
                            contactAddress,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (showCall) {
                    IconButton(onClick = onCall) {
                        Icon(painterResource(R.drawable.ic_call), contentDescription = stringResource(R.string.thread_call))
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    highlighted: Boolean,
    onHighlightFinished: () -> Unit,
) {
    val incoming = message.type == TelephonyMessageType.INCOMING
    val spam = incoming && SpamDetector.isSpam(message.body)
    val baseColor = if (incoming) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
    val contentColor = if (incoming) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
    val highlight = remember(message.id) { Animatable(0f) }
    LaunchedEffect(highlighted) {
        if (highlighted) {
            delay(80)
            repeat(2) {
                highlight.animateTo(1f, tween(180))
                highlight.animateTo(0f, tween(180))
            }
            onHighlightFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = if (incoming) 7.dp else 2.dp)
            .testTag(ThreadDetailTestTags.message(message.id)),
        contentAlignment = if (incoming) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier
                .padding(
                    start = if (incoming) 0.dp else 60.dp,
                    end = if (incoming) 60.dp else 0.dp,
                )
                .padding(top = if (spam) 12.dp else 0.dp),
        ) {
            Surface(
                color = lerp(baseColor, MaterialTheme.colorScheme.primary, highlight.value * 0.32f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    Modifier.padding(
                        start = 12.dp,
                        top = if (spam) 16.dp else 12.dp,
                        end = 12.dp,
                        bottom = 12.dp,
                    ),
                ) {
                    MessageBody(message.body, contentColor)
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(formatMessageTime(message.date), color = contentColor, fontSize = 11.sp)
                        message.simSlot?.let {
                            Spacer(Modifier.width(4.dp))
                            SimIndicator(it, color = contentColor)
                        }
                        if (!incoming && message.status != 64) {
                            Icon(
                                painterResource(R.drawable.ic_tick_single),
                                contentDescription = stringResource(R.string.thread_sent_status),
                                tint = contentColor,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(11.dp),
                            )
                        }
                    }
                }
            }
            if (spam) {
                SpamBadge(
                    Modifier
                        .align(Alignment.TopStart)
                        .graphicsLayer { translationY = -size.height / 2f },
                )
            }
        }
    }
}

@Composable
private fun MessageBody(body: String, color: Color) {
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(body, linkColor) {
        val linked = LinkifyUtil.linkify(body)
        buildAnnotatedString {
            append(body)
            linked.getSpans(0, linked.length, URLSpan::class.java).forEach { span ->
                addLink(
                    LinkAnnotation.Url(
                        span.url,
                        TextLinkStyles(style = androidx.compose.ui.text.SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                    ),
                    linked.getSpanStart(span),
                    linked.getSpanEnd(span),
                )
            }
        }
    }
    SelectionContainer {
        Text(text, color = color, fontSize = 15.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun SpamBadge(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.error,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_warning),
                contentDescription = stringResource(R.string.thread_spam_warning),
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(12.dp),
            )
            Text(
                stringResource(R.string.thread_spam),
                color = MaterialTheme.colorScheme.onError,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 3.dp),
            )
        }
    }
}

@Composable
private fun DayIndicator(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.7f),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(
                label,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun MessageComposer(
    message: String,
    focusComposer: Boolean,
    selectedSimNumber: Int?,
    showSimSelector: Boolean,
    onMessageChange: (String) -> Unit,
    onSimToggle: () -> Unit,
    onSend: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusComposer) {
        if (focusComposer) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    Column {
        ComposerTopShadow()
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.testTag(ThreadDetailTestTags.COMPOSER),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 60.dp)
                    .padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .focusRequester(focusRequester)
                        .testTag(ThreadDetailTestTags.COMPOSER_INPUT),
                    decorationBox = { input ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (message.isEmpty()) {
                                Text(stringResource(R.string.thread_message_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            input()
                        }
                    },
                )
                Text(message.length.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                if (showSimSelector && selectedSimNumber != null) {
                    SimSelectorButton(
                        slot = selectedSimNumber,
                        onClick = onSimToggle,
                        description = stringResource(R.string.new_message_sim, selectedSimNumber),
                    )
                }
                FilledIconButton(
                    onClick = onSend,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(40.dp)
                        .testTag(ThreadDetailTestTags.SEND),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_send),
                        contentDescription = stringResource(R.string.new_message_send),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private object TelephonyMessageType {
    const val INCOMING = 1
}

fun createMessageListItems(messages: List<Message>, nowMillis: Long = System.currentTimeMillis()): List<MessageListItem> {
    if (messages.isEmpty()) return emptyList()
    val result = mutableListOf<MessageListItem>()
    var lastDay: String? = null
    val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val skipFirstIndicator = isSameDay(messages.last().date, nowMillis)
    var firstIndicator = true
    messages.forEach { message ->
        val day = dayKey(message.date)
        if (lastDay != day) {
            if (!firstIndicator || !skipFirstIndicator) {
                result += MessageListItem.DayIndicator(message.date, formatDayLabel(message.date, now))
            }
            lastDay = day
            firstIndicator = false
        }
        result += MessageListItem.MessageItem(message)
    }
    return result
}

private fun dayKey(timestamp: Long): String = Calendar.getInstance().run {
    timeInMillis = timestamp
    "${get(Calendar.YEAR)}-${get(Calendar.DAY_OF_YEAR)}"
}

private fun isSameDay(first: Long, second: Long): Boolean {
    val firstCalendar = Calendar.getInstance().apply { timeInMillis = first }
    val secondCalendar = Calendar.getInstance().apply { timeInMillis = second }
    return firstCalendar.get(Calendar.YEAR) == secondCalendar.get(Calendar.YEAR) &&
        firstCalendar.get(Calendar.DAY_OF_YEAR) == secondCalendar.get(Calendar.DAY_OF_YEAR)
}

private fun formatMessageTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val message = Calendar.getInstance().apply { timeInMillis = timestamp }
    val hour = message.get(Calendar.HOUR).takeUnless { it == 0 } ?: 12
    val minute = message.get(Calendar.MINUTE)
    val amPm = if (message.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    val time = String.format(Locale.US, "%d:%02d %s", hour, minute, amPm)
    return if (isSameDay(timestamp, now.timeInMillis)) time else {
        val month = MONTH_NAMES[message.get(Calendar.MONTH)]
        "${message.get(Calendar.DAY_OF_MONTH)} $month, $time"
    }
}

private fun formatDayLabel(timestamp: Long, now: Calendar): String {
    if (isSameDay(timestamp, now.timeInMillis)) return "Today"
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = now.timeInMillis
        add(Calendar.DAY_OF_YEAR, -1)
    }
    if (isSameDay(timestamp, yesterday.timeInMillis)) return "Yesterday"

    val message = Calendar.getInstance().apply { timeInMillis = timestamp }
    val daysAgo = ((now.timeInMillis - timestamp) / 86_400_000).toInt()
    if (daysAgo < 7) return DAY_NAMES[message.get(Calendar.DAY_OF_WEEK) - 1]

    val day = message.get(Calendar.DAY_OF_MONTH)
    val suffix = when (day % 10) {
        1 -> if (day == 11) "th" else "st"
        2 -> if (day == 12) "th" else "nd"
        3 -> if (day == 13) "th" else "rd"
        else -> "th"
    }
    return "$day$suffix ${MONTH_NAMES[message.get(Calendar.MONTH)]}, ${message.get(Calendar.YEAR)}"
}

private val MONTH_NAMES = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val DAY_NAMES = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
