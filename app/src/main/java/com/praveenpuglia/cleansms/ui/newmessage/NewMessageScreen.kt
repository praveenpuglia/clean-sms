package com.praveenpuglia.cleansms.ui.newmessage

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.praveenpuglia.cleansms.ContactSuggestion
import com.praveenpuglia.cleansms.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NewMessageTestTags {
    const val BODY = "new_message_body"
    const val RECIPIENT_INPUT = "new_message_recipient_input"
    const val SEND = "new_message_send"
    const val COUNTER = "new_message_counter"
    const val PARTS = "new_message_parts"
    const val RECIPIENTS = "new_message_recipients"
}

data class SmsCounter(
    val counterText: String,
    val partsText: String? = null,
    val nearLimit: Boolean = false,
)

fun smsCounter(text: String): SmsCounter {
    val unicode = text.any { it !in GSM_BASIC && it !in GSM_EXTENDED }
    val singleLimit = if (unicode) 70 else 160
    val multipartLimit = if (unicode) 67 else 153
    if (text.isEmpty()) return SmsCounter("0 / $singleLimit")
    if (text.length <= singleLimit) {
        return SmsCounter(
            counterText = "${text.length} / $singleLimit",
            nearLimit = text.length > singleLimit * 0.9,
        )
    }

    val parts = ((text.length - 1) / multipartLimit) + 1
    val usedInCurrentPart = ((text.length - 1) % multipartLimit) + 1
    return SmsCounter(
        counterText = (multipartLimit - usedInCurrentPart).toString(),
        partsText = "$parts SMS",
    )
}

private const val GSM_BASIC = "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ ÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
private const val GSM_EXTENDED = "^{}\\[~]|€"

@Composable
fun NewMessageScreen(
    recipients: List<ContactSuggestion>,
    recipientQuery: String,
    message: String,
    suggestions: List<ContactSuggestion>,
    counter: SmsCounter,
    selectedSimNumber: Int?,
    showSimSelector: Boolean,
    onBack: () -> Unit,
    onRecipientQueryChange: (String) -> Unit,
    onRecipientSelected: (ContactSuggestion) -> Unit,
    onRecipientRemoved: (ContactSuggestion) -> Unit,
    onRemoveLastRecipient: () -> Boolean,
    onMessageChange: (String) -> Unit,
    onSimToggle: () -> Unit,
    onSend: () -> Unit,
) {
    val bodyFocus = androidx.compose.runtime.remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        bodyFocus.requestFocus()
        keyboard?.show()
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Surface(shadowElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                    Text(
                        text = stringResource(R.string.new_message_title),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(bodyFocus)
                        .testTag(NewMessageTestTags.BODY),
                    decorationBox = { input ->
                        Box(Modifier.padding(16.dp)) {
                            if (message.isEmpty()) {
                                Text(
                                    stringResource(R.string.new_message_body_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            input()
                        }
                    },
                )

                if (suggestions.isNotEmpty()) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        LazyColumn {
                            items(
                                items = suggestions,
                                key = { "${it.contactId}:${it.phoneNumber}:${it.isRawNumber}" },
                            ) { contact ->
                                ContactSuggestionRow(contact) { onRecipientSelected(contact) }
                            }
                        }
                    }
                }
            }

            CounterRow(counter)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 4.dp) {
                RecipientBar(
                    recipients = recipients,
                    recipientQuery = recipientQuery,
                    selectedSimNumber = selectedSimNumber,
                    showSimSelector = showSimSelector,
                    sendEnabled = message.isNotBlank() && recipients.isNotEmpty(),
                    onRecipientQueryChange = onRecipientQueryChange,
                    onRecipientRemoved = onRecipientRemoved,
                    onRemoveLastRecipient = onRemoveLastRecipient,
                    onSimToggle = onSimToggle,
                    onSend = onSend,
                )
            }
        }
    }
}

@Composable
private fun CounterRow(counter: SmsCounter) {
    val color = when {
        counter.partsText != null -> MaterialTheme.colorScheme.tertiary
        counter.nearLimit -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        counter.partsText?.let {
            Text(it, color = color, fontSize = 12.sp, modifier = Modifier.testTag(NewMessageTestTags.PARTS))
            Spacer(Modifier.width(8.dp))
        }
        Text(counter.counterText, color = color, fontSize = 12.sp, modifier = Modifier.testTag(NewMessageTestTags.COUNTER))
    }
}

@Composable
private fun RecipientBar(
    recipients: List<ContactSuggestion>,
    recipientQuery: String,
    selectedSimNumber: Int?,
    showSimSelector: Boolean,
    sendEnabled: Boolean,
    onRecipientQueryChange: (String) -> Unit,
    onRecipientRemoved: (ContactSuggestion) -> Unit,
    onRemoveLastRecipient: () -> Boolean,
    onSimToggle: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.new_message_to),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        val scroll = rememberScrollState()
        LaunchedEffect(recipients.size) { scroll.animateScrollTo(scroll.maxValue) }
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scroll)
                .testTag(NewMessageTestTags.RECIPIENTS),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            recipients.forEach { recipient ->
                InputChip(
                    selected = false,
                    onClick = { onRecipientRemoved(recipient) },
                    label = { Text(if (recipient.isRawNumber) recipient.phoneNumber else recipient.name) },
                    avatar = { ContactAvatar(recipient, 24.dp) },
                    trailingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(
                                R.string.new_message_remove_recipient,
                                if (recipient.isRawNumber) recipient.phoneNumber else recipient.name,
                            ),
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
            BasicTextField(
                value = recipientQuery,
                onValueChange = onRecipientQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier
                    .width(112.dp)
                    .height(40.dp)
                    .onPreviewKeyEvent {
                        it.type == KeyEventType.KeyDown &&
                            it.key == Key.Backspace &&
                            onRemoveLastRecipient()
                    }
                    .testTag(NewMessageTestTags.RECIPIENT_INPUT),
                decorationBox = { input ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (recipientQuery.isEmpty()) {
                            Text(
                                stringResource(R.string.new_message_add_recipient),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                        }
                        input()
                    }
                },
            )
        }

        if (showSimSelector && selectedSimNumber != null) {
            IconButton(onClick = onSimToggle, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_sim_card),
                        contentDescription = stringResource(R.string.new_message_sim, selectedSimNumber),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        selectedSimNumber.toString(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
        }
        FilledIconButton(
            onClick = onSend,
            enabled = sendEnabled,
            modifier = Modifier
                .size(40.dp)
                .testTag(NewMessageTestTags.SEND),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_send),
                contentDescription = stringResource(R.string.new_message_send),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ContactSuggestionRow(contact: ContactSuggestion, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(contact, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = if (contact.isRawNumber) {
                    stringResource(R.string.new_message_send_to, contact.phoneNumber)
                } else {
                    contact.name
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                contact.phoneNumber,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContactAvatar(contact: ContactSuggestion, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, contact.photoUri) {
        value = contact.photoUri?.let { photo ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(photo)).use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                painter = BitmapPainter(bitmap!!),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                contact.name.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
