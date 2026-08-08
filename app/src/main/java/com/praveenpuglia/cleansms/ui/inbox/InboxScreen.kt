package com.praveenpuglia.cleansms.ui.inbox

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.praveenpuglia.cleansms.AvatarColorResolver
import com.praveenpuglia.cleansms.MessageCategory
import com.praveenpuglia.cleansms.OtpMessageItem
import com.praveenpuglia.cleansms.R
import com.praveenpuglia.cleansms.SearchResultItem
import com.praveenpuglia.cleansms.ThreadItem
import com.praveenpuglia.cleansms.ui.ContactAvatar
import java.util.Calendar

sealed interface InboxPage {
    data object All : InboxPage
    data object Otp : InboxPage
    data class CategoryPage(val category: MessageCategory) : InboxPage
}

object MainInboxTestTags {
    const val HEADER = "main_header"
    const val TABS = "main_tabs"
    const val SEARCH = "main_search"
    const val SEARCH_INPUT = "main_search_input"
    const val CLEAR_SEARCH = "main_clear_search"
    const val UNREAD_CHIP = "main_unread_chip"
    const val FAB = "main_fab"
    const val DELETE_DIALOG = "main_delete_dialog"
    fun tab(index: Int) = "main_tab_$index"
    fun tabUnread(index: Int) = "main_tab_unread_$index"
    fun thread(id: Long) = "main_thread_$id"
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
    onDeleteRequest: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
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
                    else stringResource(R.string.dialog_delete_message_multiple, count),
                )
            },
            confirmButton = { TextButton(onClick = onDeleteConfirm) { Text(stringResource(R.string.dialog_delete_positive)) } },
            dismissButton = { TextButton(onClick = onDeleteDismiss) { Text(stringResource(R.string.dialog_delete_negative)) } },
        )
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
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
                    onPageSelected = onPageSelected,
                    onSearch = { onSearchModeChange(true) },
                    onUnreadOnlyChange = onUnreadOnlyChange,
                    onOpenSettings = onOpenSettings,
                    onSelectAll = onSelectAll,
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
                MessageList(searchResults, searchQuery, onMessageClick, Modifier.weight(1f))
            } else {
                InboxPager(
                    pages = pages,
                    selectedPageIndex = selectedPageIndex,
                    scrollToTopRequest = scrollToTopRequest,
                    threads = allThreads,
                    otpMessages = otpMessages,
                    allItems = allItems,
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
    Surface(shadowElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
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
    onPageSelected: (Int) -> Unit,
    onSearch: () -> Unit,
    onUnreadOnlyChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(shadowElevation = 3.dp, modifier = Modifier.testTag(MainInboxTestTags.HEADER)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (selectionMode) stringResource(R.string.selection_count, selectionCount) else stringResource(R.string.header_messages),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.42.sp,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                if (selectionMode) {
                    IconButton(onClick = onSelectAll) {
                        Icon(painterResource(R.drawable.ic_select_all), contentDescription = stringResource(R.string.select_all_content_description))
                    }
                    IconButton(onClick = onDelete, enabled = selectionCount > 0) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.delete_selected_content_description))
                    }
                } else {
                    IconButton(onClick = onSearch, modifier = Modifier.testTag(MainInboxTestTags.SEARCH)) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.main_search))
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
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
            InboxTabs(pages, selectedPageIndex, allThreads, otpMessages, promoMuted, onPageSelected)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
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
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedPageIndex,
        edgePadding = 20.dp,
        containerColor = Color.Transparent,
        divider = {},
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag(MainInboxTestTags.TABS),
    ) {
        pages.forEachIndexed { index, page ->
            val selected = index == selectedPageIndex
            val unread = when (page) {
                InboxPage.All -> threads.any(ThreadItem::hasUnread) || otpMessages.any(OtpMessageItem::isUnread)
                InboxPage.Otp -> otpMessages.any(OtpMessageItem::isUnread)
                is InboxPage.CategoryPage -> threads.any { it.category == page.category && it.hasUnread }
            }
            Tab(
                selected = selected,
                onClick = { onPageSelected(index) },
                modifier = Modifier.height(48.dp).testTag(MainInboxTestTags.tab(index)),
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
                            Text(pageLabel(page))
                            if (unread) Box(Modifier.align(Alignment.TopEnd).padding(start = 4.dp).size(6.dp).background(MaterialTheme.colorScheme.error, CircleShape).testTag(MainInboxTestTags.tabUnread(index)))
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun pageLabel(page: InboxPage): String = when (page) {
    InboxPage.All -> stringResource(R.string.tab_all)
    InboxPage.Otp -> stringResource(R.string.tab_otp)
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
    val listStates = pages.map { rememberLazyListState() }
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
                    Text(
                        item.contactName ?: item.nameOrAddress,
                        fontSize = 16.sp,
                        fontWeight = if (item.hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(formatInboxDate(item.date), fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.snippet,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.hasUnread) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                item.unreadCount.toString(),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
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
                            if (item.simSlot != null || item.subscriptionId != null) SimIndicator(item.simSlot)
                        }
                        Text(item.body.trim(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    LazyColumn(state = state, contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp), modifier = modifier.padding(horizontal = 16.dp)) {
        itemsIndexed(items, key = { _, item -> item.messageId }) { _, item ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onClick(item) }.padding(12.dp).testTag(MainInboxTestTags.message(item.messageId)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchAvatar(item)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.senderDisplay ?: item.sender,
                            fontSize = 15.sp,
                            fontWeight = if (item.isUnread) FontWeight.SemiBold else FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatInboxDate(item.date), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                        if (item.simSlot != null || item.subscriptionId != null) SimIndicator(item.simSlot)
                    }
                    Text(
                        highlightedText(item.body, query, MaterialTheme.colorScheme.primaryContainer),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
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
                modifier = Modifier.align(Alignment.BottomCenter).size(20.dp).semantics { contentDescription = spamDescription },
            ) {
                Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.padding(3.dp))
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
    Box(Modifier.padding(end = 12.dp).size(44.dp)) {
        ContactAvatar(label, item.contactPhotoUri, 44.dp, Color(background), Color(foreground))
        if (item.isUnread) Box(Modifier.align(Alignment.TopEnd).padding(2.dp).size(10.dp).background(MaterialTheme.colorScheme.error, CircleShape))
    }
}

@Composable
private fun SimIndicator(slot: Int?) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(start = 4.dp).size(width = 10.dp, height = 13.dp)) {
        Icon(painterResource(R.drawable.ic_sim_card), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text((slot ?: "?").toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 7.sp, fontWeight = FontWeight.Bold)
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
    val time = String.format("%d:%02d %s", hour, message.get(Calendar.MINUTE), if (message.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM")
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
