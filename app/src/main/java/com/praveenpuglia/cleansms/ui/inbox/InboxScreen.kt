package com.praveenpuglia.cleansms.ui.inbox

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.sin
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.absoluteValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.praveenpuglia.cleansms.AvatarColorResolver
import com.praveenpuglia.cleansms.MessageCategory
import com.praveenpuglia.cleansms.OtpMessageItem
import com.praveenpuglia.cleansms.R
import com.praveenpuglia.cleansms.SearchResultItem
import com.praveenpuglia.cleansms.ThreadItem
import com.praveenpuglia.cleansms.ui.ContactAvatar
import com.praveenpuglia.cleansms.ui.SimIndicator
import java.util.Calendar
import java.util.Locale

sealed interface InboxPage {
    data object All : InboxPage
    data object Otp : InboxPage
    data object Starred : InboxPage
    data class CategoryPage(val category: MessageCategory) : InboxPage
}

/** Stable name for a page, used to save the user's tab order. */
val InboxPage.key: String
    get() = when (this) {
        InboxPage.All -> "ALL"
        InboxPage.Otp -> "OTP"
        InboxPage.Starred -> "STARRED"
        is InboxPage.CategoryPage -> category.name
    }

/**
 * [defaults] in the user's saved order. A page the saved order doesn't know (say the All tab was turned
 * on later) goes right after the page it follows by default, or first if it is first by default; saved
 * keys for pages that no longer exist are dropped.
 */
fun orderPages(defaults: List<InboxPage>, savedKeys: List<String>): List<InboxPage> {
    val ordered = savedKeys.mapNotNull { key -> defaults.firstOrNull { it.key == key } }.distinct().toMutableList()
    defaults.forEachIndexed { index, page ->
        if (page !in ordered) ordered.add(defaults.getOrNull(index - 1)?.let { ordered.indexOf(it) + 1 } ?: 0, page)
    }
    return ordered
}

object MainInboxTestTags {
    const val HEADER = "main_header"
    const val TABS = "main_tabs"
    const val SEARCH = "main_search"
    const val SEARCH_INPUT = "main_search_input"
    const val SEARCH_RESULTS = "main_search_results"
    const val CLEAR_SEARCH = "main_clear_search"
    const val MORE = "main_more"
    const val STATS = "main_stats"
    const val EMPTY = "main_empty"
    const val UNREAD_CHIP = "main_unread_chip"
    const val FAB = "main_fab"
    const val DELETE_DIALOG = "main_delete_dialog"
    fun tab(index: Int) = "main_tab_$index"
    fun tabUnread(index: Int) = "main_tab_unread_$index"
    const val TAB_REORDER_HINT = "main_tab_reorder_hint"
    fun thread(id: Long) = "main_thread_$id"
    fun unreadBadge(threadId: Long) = "main_unread_badge_$threadId"
    fun otp(id: Long) = "main_otp_$id"
    fun otpCode(id: Long) = "main_otp_code_$id"
    fun message(id: Long) = "main_message_$id"
}

@Composable
fun InboxScreen(
    pages: List<InboxPage>,
    selectedPageIndex: Int,
    scrollToTopRequest: Int,
    allThreads: List<ThreadItem>,
    otpMessages: List<OtpMessageItem>,
    allItems: List<SearchResultItem>,
    starredItems: List<SearchResultItem>,
    searchResults: List<SearchResultItem>,
    searchMode: Boolean,
    searchQuery: String,
    unreadOnly: Boolean,
    selectionMode: Boolean,
    selectedThreadIds: Set<Long>,
    selectedMessageIds: Set<Long>,
    promoMuted: Boolean,
    permissionRequired: Boolean,
    showDeleteDialog: Boolean,
    onPageSelected: (Int) -> Unit,
    onSearchModeChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onUnreadOnlyChange: (Boolean) -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewMessage: () -> Unit,
    onThreadClick: (ThreadItem) -> Unit,
    onThreadAvatarClick: (ThreadItem) -> Unit,
    onThreadLongClick: (ThreadItem) -> Unit,
    onOtpClick: (OtpMessageItem) -> Unit,
    onOtpAvatarClick: (OtpMessageItem) -> Unit,
    onOtpLongClick: (OtpMessageItem) -> Unit,
    onCopyOtp: (String) -> Unit,
    onMessageClick: (SearchResultItem) -> Unit,
    onSelectAll: () -> Unit,
    onMarkAsRead: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onReorderPages: (List<InboxPage>) -> Unit = {},
    showTabReorderHint: Boolean = false,
    onTabReorderHintSeen: () -> Unit = {},
) {
    if (showDeleteDialog) {
        val count = selectedThreadIds.size + selectedMessageIds.size
        AlertDialog(
            modifier = Modifier.testTag(MainInboxTestTags.DELETE_DIALOG),
            onDismissRequest = onDeleteDismiss,
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = {
                Text(
                    if (count == 1) stringResource(R.string.dialog_delete_message_single)
                    else pluralStringResource(R.plurals.dialog_delete_message_multiple, count, count),
                )
            },
            confirmButton = { TextButton(onClick = onDeleteConfirm) { Text(stringResource(R.string.dialog_delete_positive)) } },
            dismissButton = { TextButton(onClick = onDeleteDismiss) { Text(stringResource(R.string.dialog_delete_negative)) } },
        )
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            if (searchMode) {
                SearchHeader(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClose = { onSearchModeChange(false) },
                )
            } else {
                InboxHeader(
                    pages = pages,
                    selectedPageIndex = selectedPageIndex,
                    allThreads = allThreads,
                    otpMessages = otpMessages,
                    unreadOnly = unreadOnly,
                    selectionMode = selectionMode,
                    selectionCount = selectedThreadIds.size + selectedMessageIds.size,
                    promoMuted = promoMuted,
                    showTabReorderHint = showTabReorderHint,
                    onPageSelected = onPageSelected,
                    onReorderPages = onReorderPages,
                    onTabReorderHintSeen = onTabReorderHintSeen,
                    onSearch = { onSearchModeChange(true) },
                    onUnreadOnlyChange = onUnreadOnlyChange,
                    onOpenStats = onOpenStats,
                    onOpenSettings = onOpenSettings,
                    onSelectAll = onSelectAll,
                    onMarkAsRead = onMarkAsRead,
                    onDelete = onDeleteRequest,
                )
            }

            if (permissionRequired) {
                Text(
                    stringResource(R.string.main_permission_required),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else if (searchMode) {
                MessageList(searchResults, searchQuery, onMessageClick, Modifier.weight(1f).testTag(MainInboxTestTags.SEARCH_RESULTS))
            } else {
                InboxPager(
                    pages = pages,
                    selectedPageIndex = selectedPageIndex,
                    scrollToTopRequest = scrollToTopRequest,
                    threads = allThreads,
                    otpMessages = otpMessages,
                    allItems = allItems,
                    starredItems = starredItems,
                    unreadOnly = unreadOnly,
                    selectionMode = selectionMode,
                    selectedThreadIds = selectedThreadIds,
                    selectedMessageIds = selectedMessageIds,
                    onPageSelected = onPageSelected,
                    onThreadClick = onThreadClick,
                    onThreadAvatarClick = onThreadAvatarClick,
                    onThreadLongClick = onThreadLongClick,
                    onOtpClick = onOtpClick,
                    onOtpAvatarClick = onOtpAvatarClick,
                    onOtpLongClick = onOtpLongClick,
                    onCopyOtp = onCopyOtp,
                    onMessageClick = onMessageClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (!permissionRequired && !searchMode) {
            Box(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), contentAlignment = Alignment.BottomEnd) {
                FloatingActionButton(onClick = onNewMessage, modifier = Modifier.testTag(MainInboxTestTags.FAB)) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.main_new_message))
                }
            }
        }
    }
}

@Composable
private fun SearchHeader(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Surface {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.main_close_search))
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                }),
                modifier = Modifier.weight(1f).height(48.dp).focusRequester(focusRequester).testTag(MainInboxTestTags.SEARCH_INPUT),
                decorationBox = { input ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text(stringResource(R.string.main_search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        input()
                    }
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.testTag(MainInboxTestTags.CLEAR_SEARCH)) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.main_clear_search))
                }
            }
        }
    }
}

@Composable
private fun InboxHeader(
    pages: List<InboxPage>,
    selectedPageIndex: Int,
    allThreads: List<ThreadItem>,
    otpMessages: List<OtpMessageItem>,
    unreadOnly: Boolean,
    selectionMode: Boolean,
    selectionCount: Int,
    promoMuted: Boolean,
    showTabReorderHint: Boolean,
    onPageSelected: (Int) -> Unit,
    onReorderPages: (List<InboxPage>) -> Unit,
    onTabReorderHintSeen: () -> Unit,
    onSearch: () -> Unit,
    onUnreadOnlyChange: (Boolean) -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectAll: () -> Unit,
    onMarkAsRead: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.testTag(MainInboxTestTags.HEADER)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (selectionMode) pluralStringResource(R.plurals.selection_count, selectionCount, selectionCount) else stringResource(R.string.header_messages),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.42.sp,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                if (selectionMode) {
                    IconButton(onClick = onSelectAll) {
                        Icon(painterResource(R.drawable.ic_select_all), contentDescription = stringResource(R.string.select_all_content_description))
                    }
                    IconButton(onClick = onMarkAsRead, enabled = selectionCount > 0) {
                        Icon(
                            painterResource(R.drawable.ic_mark_read),
                            contentDescription = stringResource(R.string.mark_selected_read_content_description),
                        )
                    }
                    IconButton(onClick = onDelete, enabled = selectionCount > 0) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.delete_selected_content_description))
                    }
                } else {
                    IconButton(onClick = onSearch, modifier = Modifier.testTag(MainInboxTestTags.SEARCH)) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.main_search))
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.testTag(MainInboxTestTags.MORE)) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.main_more_options))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.main_unread_only)) },
                                onClick = {
                                    menuExpanded = false
                                    onUnreadOnlyChange(!unreadOnly)
                                },
                                trailingIcon = {
                                    if (unreadOnly) Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.stats_title)) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenStats()
                                },
                                modifier = Modifier.testTag(MainInboxTestTags.STATS),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_title)) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenSettings()
                                },
                            )
                        }
                    }
                }
            }
            if (unreadOnly) {
                InputChip(
                    selected = true,
                    onClick = { onUnreadOnlyChange(false) },
                    label = { Text(stringResource(R.string.main_unread), fontSize = 12.sp) },
                    trailingIcon = { Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.main_clear_unread), modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.padding(start = 28.dp, top = 6.dp).height(32.dp).testTag(MainInboxTestTags.UNREAD_CHIP),
                )
            }
            TabReorderHint(show = showTabReorderHint && !selectionMode, onSeen = onTabReorderHintSeen) {
                InboxTabs(pages, selectedPageIndex, allThreads, otpMessages, promoMuted, onPageSelected) { newOrder ->
                    onReorderPages(newOrder)
                    onTabReorderHintSeen()
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** One-time tip, anchored under the tabs, that they can be rearranged. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabReorderHint(show: Boolean, onSeen: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        delay(800) // let the inbox settle first
        try {
            state.show()
        } finally {
            onSeen() // dismissing ends show() by cancelling it, so this can't follow it normally
        }
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = {
            RichTooltip(
                modifier = Modifier.testTag(MainInboxTestTags.TAB_REORDER_HINT),
                title = { Text(stringResource(R.string.tab_reorder_hint_title)) },
                action = { TextButton(onClick = { scope.launch { state.dismiss() } }) { Text(stringResource(R.string.tab_reorder_hint_ok)) } },
            ) { Text(stringResource(R.string.tab_reorder_hint_body)) }
        },
        state = state,
        enableUserInput = false,
    ) { content() }
}

/**
 * The held tab's pill: shorter than the 48dp tab and centred, so it stays inside the tab row (which clips)
 * even when scaled up and casting a shadow.
 */
private val LiftedTabShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val height = with(density) { 36.dp.toPx() }.coerceAtMost(size.height)
        val top = (size.height - height) / 2
        return Outline.Rounded(RoundRect(0f, top, size.width, top + height, CornerRadius(height / 2)))
    }
}

@Composable
private fun InboxTabs(
    pages: List<InboxPage>,
    selectedPageIndex: Int,
    threads: List<ThreadItem>,
    otpMessages: List<OtpMessageItem>,
    promoMuted: Boolean,
    onPageSelected: (Int) -> Unit,
    onReorder: (List<InboxPage>) -> Unit,
) {
    // Tabs move in this local order while one is dragged; the new order is handed over on release,
    // so the pages and lists don't reshuffle under the finger.
    var order by remember(pages) { mutableStateOf(pages) }
    var dragged by remember { mutableStateOf<InboxPage?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragTravel by remember { mutableFloatStateOf(0f) } // how far the finger has moved since pickup
    val widths = remember { mutableStateMapOf<InboxPage, Int>() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Tabs that just changed slot start from where they were drawn and spring into place:
    // neighbours sliding into the gap, and the dropped tab settling.
    val slides = remember { mutableStateMapOf<InboxPage, Animatable<Float, AnimationVector1D>>() }
    fun slide(page: InboxPage, from: Float) {
        val offset = slides.getOrPut(page) { Animatable(0f) }
        scope.launch {
            offset.snapTo(offset.value + from)
            offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
        }
    }
    // The held tab leans into the direction it's moving and wobbles level when it stops
    val tilt = remember { Animatable(0f) }
    val selectedPage = pages.getOrNull(selectedPageIndex)
    val moveLeft = stringResource(R.string.tab_move_left)
    val moveRight = stringResource(R.string.tab_move_right)
    fun moved(from: Int, to: Int) = order.toMutableList().apply { add(to, removeAt(from)) }

    // Swap once the held tab is halfway over a neighbour, then shift its offset by that neighbour's
    // width so it stays under the finger.
    fun swapIfPast(page: InboxPage) {
        val at = order.indexOf(page)
        val next = order.getOrNull(at + 1)
        val previous = order.getOrNull(at - 1)
        if (next != null && dragOffset > (widths[next] ?: 0) / 2f) {
            order = moved(at, at + 1)
            dragOffset -= widths[next] ?: 0
            slide(next, (widths[page] ?: 0).toFloat())
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        } else if (previous != null && dragOffset < -(widths[previous] ?: 0) / 2f) {
            order = moved(at, at - 1)
            dragOffset += widths[previous] ?: 0
            slide(previous, -(widths[page] ?: 0).toFloat())
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    // Holding a tab near either edge scrolls the row, faster the closer it is, so off-screen slots are reachable.
    // The held tab rides along with the scroll (staying under the finger) and keeps swapping as tabs pass under it.
    val rowScroll = rememberScrollState()
    var rowWidth by remember { mutableIntStateOf(0) }
    val tabX = remember { mutableStateMapOf<InboxPage, Float>() }
    val density = LocalDensity.current
    LaunchedEffect(dragged) {
        val page = dragged ?: return@LaunchedEffect
        val edge = with(density) { 56.dp.toPx() }
        val fastest = with(density) { 14.dp.toPx() } // per frame, right at the edge
        val travelBeforeScroll = with(density) { 24.dp.toPx() }
        while (true) {
            withFrameNanos { }
            val left = (tabX[page] ?: continue) + dragOffset - rowScroll.value
            val right = left + (widths[page] ?: 0)
            val push = when {
                left < edge -> -((edge - left) / edge).coerceIn(0f, 1f)
                right > rowWidth - edge -> ((right - (rowWidth - edge)) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            // Only once the finger has moved: a tab picked up near an edge shouldn't send the row off on its own
            if (push == 0f || dragTravel < travelBeforeScroll) continue
            dragOffset += rowScroll.scrollBy(push * push.absoluteValue * fastest)
            swapIfPast(page)
        }
    }

    // The row scrolls to keep the selected tab's slot in view whenever that slot changes. Hold the slot
    // still while dragging, or the row slides under the finger each time the held tab passes the selected one.
    var heldSelectedIndex by remember { mutableIntStateOf(0) }
    val selectedSlot = if (dragged != null) heldSelectedIndex else order.indexOf(selectedPage).coerceAtLeast(0)
    // That held slot may belong to another tab mid-drag, so the underline fades out until the drop.
    val indicatorAlpha by animateFloatAsState(if (dragged != null) 0f else 1f, label = "tabIndicator")
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedSlot,
        scrollState = rowScroll,
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                Modifier.tabIndicatorOffset(selectedSlot, matchContentSize = true).alpha(indicatorAlpha),
                width = Dp.Unspecified,
            )
        },
        edgePadding = 20.dp,
        containerColor = Color.Transparent,
        divider = {},
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).onSizeChanged { rowWidth = it.width }.testTag(MainInboxTestTags.TABS),
    ) {
        order.forEachIndexed { index, page ->
            key(page) {
                val isDragged = page == dragged
                // Holding: 0 -> 1 over the long-press time as the pill rises out of the surface; sinks back if let go early
                val press = remember { Animatable(0f) }
                var tabCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                fun windowX(change: PointerInputChange) = tabCoords?.localToWindow(change.position)?.x ?: change.position.x
                // Broken free: pops off the surface with an overshoot once the hold completes
                val lift by animateFloatAsState(
                    targetValue = if (isDragged) 1f else 0f,
                    // Pops up with an overshoot; settles without one (bouncing past zero shrank the tab and blinked the pill)
                    animationSpec = if (isDragged) spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
                    else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                    label = "tabLift",
                )
                val liftedColor = MaterialTheme.colorScheme.secondaryContainer
                val reliefLight = MaterialTheme.colorScheme.surfaceBright
                val reliefShade = MaterialTheme.colorScheme.onSurface
                val surfaceColor = MaterialTheme.colorScheme.background
                val unread = when (page) {
                    InboxPage.All -> threads.any(ThreadItem::hasUnread) || otpMessages.any(OtpMessageItem::isUnread)
                    InboxPage.Otp -> otpMessages.any(OtpMessageItem::isUnread)
                    InboxPage.Starred -> false
                    is InboxPage.CategoryPage -> threads.any { it.category == page.category && it.hasUnread }
                }
                fun onDrag(amount: Float) {
                    dragOffset += amount
                    dragTravel += amount.absoluteValue
                    scope.launch {
                        tilt.animateTo((amount * 0.4f).coerceIn(-5f, 5f), spring(stiffness = Spring.StiffnessMedium))
                        tilt.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                    }
                    swapIfPast(page)
                }
                fun onDrop(completed: Boolean) {
                    slide(page, dragOffset)
                    dragged = null
                    dragOffset = 0f
                    scope.launch { tilt.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                    // Clear the rise at once so the relief (the larger of rise and lift) fades out with the lift alone
                    scope.launch { press.snapTo(0f) }
                    if (completed) {
                        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        if (order != pages) onReorder(order)
                        // The tab you just moved is the one you're working with
                        if (page != selectedPage) onPageSelected(order.indexOf(page))
                    } else {
                        order = pages
                    }
                }
                // The rising relief is the press feedback; the standard ripple would flash a flat grey pill over it
                CompositionLocalProvider(LocalRippleConfiguration provides null) {
                    Tab(
                        selected = page == selectedPage,
                        onClick = { onPageSelected(pages.indexOf(page)) },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag(MainInboxTestTags.tab(index))
                            .onSizeChanged { widths[page] = it.width }
                            .onPlaced { tabX[page] = it.positionInParent().x }
                            // Before the graphicsLayer, and measured in window coordinates: the tab itself moves under the
                            // finger (following it, sliding when it swaps, riding the row's scroll), so its own local
                            // coordinates would count that motion as finger movement.
                            .onGloballyPositioned { tabCoords = it }
                            // Restarted (between gestures) when these change, so the handler never works on a stale
                            // order, page list or selection; with just `page`, every drag after the first one would.
                            .pointerInput(page, pages, selectedPage) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val rise = scope.launch {
                                        press.animateTo(1f, tween(viewConfiguration.longPressTimeoutMillis.toInt(), easing = FastOutLinearInEasing))
                                    }
                                    val held = awaitLongPressOrCancellation(down.id)
                                    if (held == null) {
                                        // Let go (a tap) or moved away (a scroll) before breaking free
                                        rise.cancel()
                                        scope.launch { press.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                        return@awaitEachGesture
                                    }
                                    heldSelectedIndex = order.indexOf(selectedPage).coerceAtLeast(0)
                                    dragged = page
                                    dragOffset = 0f
                                    dragTravel = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    var lastX = windowX(held)
                                    // Follow the finger until it lifts. Not drag(): that gives up as soon as anything else
                                    // has handled the event, which the row's own scrolling can when it moves under the finger.
                                    var completed = false
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == held.id } ?: break
                                        if (!change.pressed) {
                                            completed = true
                                            change.consume() // the release shouldn't also count as a tap on the tab
                                            break
                                        }
                                        val x = windowX(change)
                                        onDrag(x - lastX)
                                        lastX = x
                                        change.consume()
                                    }
                                    onDrop(completed)
                                }
                            }
                            .zIndex(if (isDragged || lift > 0.01f || press.value > 0.01f) 1f else 0f)
                            .graphicsLayer {
                                val rising = press.value
                                // A faint shiver near the end of the hold, as if straining against the surface
                                val strain = ((rising - 0.6f) / 0.4f).coerceIn(0f, 1f) * (1f - lift.coerceIn(0f, 1f))
                                translationX = (if (isDragged) dragOffset else 0f) + (slides[page]?.value ?: 0f) +
                                    sin(rising * 70f) * 0.8.dp.toPx() * strain
                                val scale = 1f + 0.03f * rising + 0.07f * lift
                                scaleX = scale
                                scaleY = scale
                                rotationZ = if (isDragged) tilt.value else 0f
                                // A soft real shadow only once it has broken free (the rising relief has its drawn contact shadow)
                                shadowElevation = 4.dp.toPx() * lift.coerceAtLeast(0f)
                                shape = LiftedTabShape
                            }
                            .drawBehind {
                                val solid = lift.coerceIn(0f, 1f)
                                // One raised relief from the first touch to the drop: it rises while held, then keeps the
                                // same lit face once it breaks free, just lifted higher with a deeper contact shadow.
                                val relief = maxOf(press.value, solid)
                                if (relief > 0.01f) {
                                    val outline = LiftedTabShape.createOutline(size, layoutDirection, this)
                                    translate(top = (1.5f + 1.5f * solid).dp.toPx()) {
                                        drawOutline(outline, reliefShade.copy(alpha = (0.18f + 0.06f * solid) * relief))
                                    }
                                    drawOutline(outline, surfaceColor) // opaque, so no shadow shows through the tint
                                    drawOutline(outline, liftedColor.copy(alpha = 0.45f * relief))
                                    drawOutline(outline, Brush.verticalGradient(listOf(reliefLight.copy(alpha = 0.55f * relief), reliefShade.copy(alpha = 0.10f * relief))))
                                    drawOutline(outline, reliefLight.copy(alpha = 0.8f * relief), style = Stroke(1.dp.toPx()))
                                }
                            }
                            // Dragging is hard with a screen reader, so offer the same moves as actions
                            .semantics {
                                customActions = buildList {
                                    if (index > 0) add(CustomAccessibilityAction(moveLeft) { onReorder(moved(index, index - 1)); true })
                                    if (index < order.lastIndex) add(CustomAccessibilityAction(moveRight) { onReorder(moved(index, index + 1)); true })
                                }
                            },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (promoMuted && page is InboxPage.CategoryPage && page.category == MessageCategory.PROMOTIONAL) {
                                    Icon(
                                        painterResource(R.drawable.ic_notifications_off),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 6.dp).size(16.dp),
                                    )
                                }
                                Box {
                                    Text(pageLabel(page), modifier = Modifier.padding(end = 8.dp))
                                    if (unread) Box(Modifier.align(Alignment.TopEnd).offset(y = (-2).dp).size(6.dp).background(MaterialTheme.colorScheme.error, CircleShape).testTag(MainInboxTestTags.tabUnread(index)))
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun pageLabel(page: InboxPage): String = when (page) {
    InboxPage.All -> stringResource(R.string.tab_all)
    InboxPage.Otp -> stringResource(R.string.tab_otp)
    InboxPage.Starred -> stringResource(R.string.tab_starred)
    is InboxPage.CategoryPage -> when (page.category) {
        MessageCategory.PERSONAL -> stringResource(R.string.category_personal)
        MessageCategory.TRANSACTIONAL -> stringResource(R.string.category_transactions)
        MessageCategory.SERVICE -> stringResource(R.string.category_service)
        MessageCategory.PROMOTIONAL -> stringResource(R.string.category_promotions)
        MessageCategory.GOVERNMENT -> stringResource(R.string.category_government)
        MessageCategory.UNKNOWN -> stringResource(R.string.category_unknown)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InboxPager(
    pages: List<InboxPage>,
    selectedPageIndex: Int,
    scrollToTopRequest: Int,
    threads: List<ThreadItem>,
    otpMessages: List<OtpMessageItem>,
    allItems: List<SearchResultItem>,
    starredItems: List<SearchResultItem>,
    unreadOnly: Boolean,
    selectionMode: Boolean,
    selectedThreadIds: Set<Long>,
    selectedMessageIds: Set<Long>,
    onPageSelected: (Int) -> Unit,
    onThreadClick: (ThreadItem) -> Unit,
    onThreadAvatarClick: (ThreadItem) -> Unit,
    onThreadLongClick: (ThreadItem) -> Unit,
    onOtpClick: (OtpMessageItem) -> Unit,
    onOtpAvatarClick: (OtpMessageItem) -> Unit,
    onOtpLongClick: (OtpMessageItem) -> Unit,
    onCopyOtp: (String) -> Unit,
    onMessageClick: (SearchResultItem) -> Unit,
    modifier: Modifier,
) {
    val pagerState = rememberPagerState(initialPage = selectedPageIndex, pageCount = { pages.size })
    val listStates = pages.map { page -> key(page) { rememberLazyListState() } } // follow the page when tabs move
    LaunchedEffect(selectedPageIndex, pages.size) {
        if (selectedPageIndex in pages.indices && pagerState.currentPage != selectedPageIndex) pagerState.scrollToPage(selectedPageIndex)
    }
    LaunchedEffect(pagerState.currentPage) { onPageSelected(pagerState.currentPage) }
    LaunchedEffect(scrollToTopRequest) {
        listStates.getOrNull(selectedPageIndex)?.let { state ->
            if (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0) state.animateScrollToItem(0)
        }
    }

    HorizontalPager(state = pagerState, modifier = modifier) { pageIndex ->
        val page = pages[pageIndex]
        val state = listStates[pageIndex]
        when (page) {
            InboxPage.All -> MessageList(allItems, "", onMessageClick, Modifier.fillMaxSize(), state)
            InboxPage.Starred -> {
                val items = if (unreadOnly) starredItems.filter(SearchResultItem::isUnread) else starredItems
                if (items.isEmpty()) EmptyPage(stringResource(R.string.starred_empty))
                else MessageList(items, "", onMessageClick, Modifier.fillMaxSize(), state)
            }
            InboxPage.Otp -> OtpList(
                if (unreadOnly) otpMessages.filter(OtpMessageItem::isUnread) else otpMessages,
                selectionMode,
                selectedMessageIds,
                onOtpClick,
                onOtpAvatarClick,
                onOtpLongClick,
                onCopyOtp,
                state,
            )
            is InboxPage.CategoryPage -> ThreadList(
                threads.filter { it.category == page.category && (!unreadOnly || it.hasUnread) },
                selectionMode,
                selectedThreadIds,
                onThreadClick,
                onThreadAvatarClick,
                onThreadLongClick,
                state,
            )
        }
    }
}

/**
 * Lazy lists keep their scroll anchor on the old first row when rows are inserted above it, which
 * hides newly arrived messages. If the user was viewing the old first row, follow the new head;
 * if they had scrolled down, leave them where they are.
 */
@Composable
private fun FollowNewItemsAtTop(state: LazyListState, firstKey: Any?) {
    var previousFirstKey by remember { mutableStateOf(firstKey) }
    LaunchedEffect(firstKey) {
        val previous = previousFirstKey
        previousFirstKey = firstKey
        if (previous == null || previous == firstKey) return@LaunchedEffect
        // Rows peeking into the top content padding also count as "visible"; match the first visible index.
        val firstVisible = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == state.firstVisibleItemIndex }
        if (firstVisible?.key == previous) state.scrollToItem(0)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadList(
    items: List<ThreadItem>,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onClick: (ThreadItem) -> Unit,
    onAvatarClick: (ThreadItem) -> Unit,
    onLongClick: (ThreadItem) -> Unit,
    state: LazyListState,
) {
    FollowNewItemsAtTop(state, items.firstOrNull()?.threadId)
    LazyColumn(state = state, contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        itemsIndexed(items, key = { _, item -> item.threadId }) { index, item ->
            ThreadRow(item, selectionMode, item.threadId in selectedIds, onClick, onAvatarClick, onLongClick)
            if (index != items.lastIndex) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadRow(
    item: ThreadItem,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: (ThreadItem) -> Unit,
    onAvatarClick: (ThreadItem) -> Unit,
    onLongClick: (ThreadItem) -> Unit,
) {
    Surface(color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onClick(item) }, onLongClick = { onLongClick(item) })
                .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 22.dp).testTag(MainInboxTestTags.thread(item.threadId)),
        ) {
            InboxAvatar(
                label = item.contactName ?: item.nameOrAddress,
                photoUri = item.contactPhotoUri,
                unread = item.hasUnread,
                selected = selected,
                spam = item.hasSpam,
                enabled = selectionMode || item.hasSavedContact || item.category == MessageCategory.PERSONAL,
                onClick = { onAvatarClick(item) },
                onLongClick = { onLongClick(item) },
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Name shrinks (ellipsis) before the badge does, so the count stays visible.
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.contactName ?: item.nameOrAddress,
                            fontSize = 16.sp,
                            fontWeight = if (item.hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (item.hasUnread) {
                            // M3 badge: a circle for one digit, growing into a pill for more.
                            Badge(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(start = 8.dp).testTag(MainInboxTestTags.unreadBadge(item.threadId)),
                            ) {
                                Text(item.unreadCount.toString(), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(formatInboxDate(item.date), fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Text(
                    item.snippet,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OtpList(
    items: List<OtpMessageItem>,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onClick: (OtpMessageItem) -> Unit,
    onAvatarClick: (OtpMessageItem) -> Unit,
    onLongClick: (OtpMessageItem) -> Unit,
    onCopyOtp: (String) -> Unit,
    state: LazyListState,
) {
    FollowNewItemsAtTop(state, items.firstOrNull()?.messageId)
    LazyColumn(state = state, contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        itemsIndexed(items, key = { _, item -> item.messageId }) { index, item ->
            val selected = item.messageId in selectedIds
            Surface(color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
                Row(
                    modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onClick(item) }, onLongClick = { onLongClick(item) })
                        .padding(12.dp).testTag(MainInboxTestTags.otp(item.messageId)),
                ) {
                    InboxAvatar(
                        item.contactName ?: item.address,
                        item.contactPhotoUri,
                        item.isUnread,
                        selected,
                        false,
                        selectionMode || item.hasSavedContact,
                        { onAvatarClick(item) },
                        { onLongClick(item) },
                    )
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.contactName ?: item.address,
                                fontSize = 16.sp,
                                fontWeight = if (item.isUnread) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(formatInboxDate(item.date), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                            if (item.simSlot != null || item.subscriptionId != null) {
                                SimIndicator(item.simSlot, Modifier.padding(start = 4.dp))
                            }
                        }
                        Text(
                            item.body.trim(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.otpCode.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(top = 6.dp).clickable(enabled = !selectionMode) { onCopyOtp(item.otpCode.trim()) }
                                    .testTag(MainInboxTestTags.otpCode(item.messageId)),
                            ) {
                                Text(
                                    item.otpCode.trim(),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (index != items.lastIndex) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun MessageList(
    items: List<SearchResultItem>,
    query: String,
    onClick: (SearchResultItem) -> Unit,
    modifier: Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    FollowNewItemsAtTop(state, items.firstOrNull()?.messageId)
    LazyColumn(state = state, contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp), modifier = modifier.padding(horizontal = 16.dp)) {
        itemsIndexed(items, key = { _, item -> item.messageId }) { _, item ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onClick(item) }.padding(12.dp).testTag(MainInboxTestTags.message(item.messageId)),
            ) {
                SearchAvatar(item)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.senderDisplay ?: item.sender,
                            fontSize = 16.sp,
                            fontWeight = if (item.isUnread) FontWeight.SemiBold else FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatInboxDate(item.date), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                        if (item.simSlot != null || item.subscriptionId != null) {
                            SimIndicator(item.simSlot, Modifier.padding(start = 4.dp))
                        }
                    }
                    Text(
                        highlightedText(item.body, query, MaterialTheme.colorScheme.primaryContainer),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun InboxAvatar(
    label: String,
    photoUri: String?,
    unread: Boolean,
    selected: Boolean,
    spam: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val (background, foreground) = remember(label) { AvatarColorResolver.resolve(context, label) }
    val avatarDescription = stringResource(R.string.main_avatar)
    val spamDescription = stringResource(R.string.main_contains_spam)
    Box(Modifier.padding(end = 12.dp).size(48.dp)) {
        ContactAvatar(
            label,
            photoUri,
            48.dp,
            Color(background),
            Color(foreground),
            modifier = Modifier.alpha(if (selected) 0.35f else 1f)
                .combinedClickable(onClick = { if (enabled) onClick() }, onLongClick = onLongClick)
                .semantics { contentDescription = avatarDescription },
        )
        if (unread) Box(Modifier.align(Alignment.TopEnd).padding(2.dp).size(10.dp).background(MaterialTheme.colorScheme.error, CircleShape))
        if (spam) {
            Surface(
                color = MaterialTheme.colorScheme.error,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 8.dp)
                    .size(16.dp)
                    .semantics { contentDescription = spamDescription },
            ) {
                Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.padding(2.5.dp))
            }
        }
        if (selected) {
            Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.align(Alignment.Center).size(28.dp)) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(6.dp))
            }
        }
    }
}

@Composable
private fun SearchAvatar(item: SearchResultItem) {
    val label = item.senderDisplay ?: item.sender
    val context = LocalContext.current
    val (background, foreground) = remember(label) { AvatarColorResolver.resolve(context, label) }
    Box(Modifier.padding(end = 12.dp).size(48.dp)) {
        ContactAvatar(label, item.contactPhotoUri, 48.dp, Color(background), Color(foreground))
        if (item.isUnread) Box(Modifier.align(Alignment.TopEnd).padding(2.dp).size(10.dp).background(MaterialTheme.colorScheme.error, CircleShape))
    }
}

fun highlightedText(text: String, query: String, color: Color): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        var start = 0
        while (true) {
            val index = text.indexOf(query, start, ignoreCase = true)
            if (index < 0) break
            addStyle(SpanStyle(background = color), index, index + query.length)
            start = index + 1
        }
    }
}

fun formatInboxDate(timestamp: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val message = Calendar.getInstance().apply { timeInMillis = timestamp }
    val hour = message.get(Calendar.HOUR).takeUnless { it == 0 } ?: 12
    val time = String.format(Locale.US, "%d:%02d %s", hour, message.get(Calendar.MINUTE), if (message.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM")
    if (sameDay(message, now)) return time
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_YEAR, -1)
    }
    if (sameDay(message, yesterday)) return "Yesterday, $time"
    val daysAgo = ((nowMillis - timestamp) / 86_400_000).toInt()
    if (daysAgo < 7) return "${DAY_NAMES[message.get(Calendar.DAY_OF_WEEK) - 1]}, $time"
    return "${message.get(Calendar.DAY_OF_MONTH)} ${MONTH_NAMES[message.get(Calendar.MONTH)]}, $time"
}

private fun sameDay(first: Calendar, second: Calendar) =
    first.get(Calendar.YEAR) == second.get(Calendar.YEAR) && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)

private val DAY_NAMES = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val MONTH_NAMES = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

@Composable
private fun EmptyPage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(MainInboxTestTags.EMPTY),
        )
    }
}
