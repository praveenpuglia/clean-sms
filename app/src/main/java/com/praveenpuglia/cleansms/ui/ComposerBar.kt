package com.praveenpuglia.cleansms.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.praveenpuglia.cleansms.R
import com.praveenpuglia.cleansms.ui.newmessage.SmsCounter

/**
 * The bottom composer: a pill-shaped field one tonal step off the page, with Send beside it. No bar
 * and no shadow; the shape and the tonal step separate it from the conversation.
 *
 * - [lip]: shown in a small tab on the pill's top edge (the SMS counter); null hides it, sliding.
 * - [simSlot]: when set, a SIM switch leads the pill, away from Send.
 * - [content]: the field itself (message text, or recipients).
 */
@Composable
fun ComposerBar(
    lip: (@Composable () -> Unit)?,
    simSlot: Int?,
    onSimToggle: () -> Unit,
    sendEnabled: Boolean,
    onSend: () -> Unit,
    sendTestTag: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        PillField(lip, Modifier.weight(1f)) {
            if (simSlot != null) {
                SimSwitch(
                    slot = simSlot,
                    onClick = onSimToggle,
                    description = stringResource(R.string.new_message_sim, simSlot),
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = FIELD_MIN_HEIGHT)
                    .padding(start = if (simSlot != null) 0.dp else 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
        FilledIconButton(
            onClick = onSend,
            enabled = sendEnabled,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(FIELD_MIN_HEIGHT)
                .testTag(sendTestTag),
        ) {
            Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.new_message_send))
        }
    }
}

@Composable
private fun PillField(lip: (@Composable () -> Unit)?, modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    val color = MaterialTheme.colorScheme.surfaceContainerHigh
    val layoutDirection = LocalLayoutDirection.current
    var lipSize by remember { mutableStateOf(IntSize.Zero) }
    // Keep drawing the last lip while it animates out
    var shownLip by remember { mutableStateOf(lip) }
    if (lip != null) shownLip = lip
    Box(modifier.drawBehind { drawPath(pillWithLip(size, lipSize, layoutDirection), color) }) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, content = content)
            // The lip takes no layout space: it sits above the pill's top edge, over the conversation, so
            // showing or hiding it only animates the tab; the field and the messages behind stay put.
            AnimatedVisibility(
                visible = lip != null,
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = LIP_END_INSET)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minHeight = 0))
                        layout(placeable.width, 0) { placeable.place(0, -placeable.height) }
                    }
                    .onSizeChanged { lipSize = it },
            ) {
                Box(Modifier.height(LIP_HEIGHT).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    shownLip?.invoke()
                }
            }
        }
    }
}

/**
 * The count and "2 SMS" in fixed-width digits, end-aligned in a fixed minimum width, so the digits
 * change in place as you type and the lip doesn't resize with every keystroke.
 */
@Composable
fun SmsCounterLabel(counter: SmsCounter, counterTag: String, partsTag: String) {
    val style = MaterialTheme.typography.labelMedium.merge(TextStyle(fontFeatureSettings = "tnum"))
    Row(
        modifier = Modifier.widthIn(min = 64.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            counter.counterText,
            style = style,
            color = if (counter.nearLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(counterTag),
        )
        counter.partsText?.let {
            Text(it, style = style, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag(partsTag))
        }
    }
}

private val FIELD_MIN_HEIGHT = 48.dp
private val FIELD_CORNER = 24.dp
private val LIP_HEIGHT = 28.dp
private val LIP_CORNER = 12.dp
private val LIP_FILLET = 8.dp
// The lip sits on the straight part of the pill's top edge: clear of the end corner and its fillet
private val LIP_END_INSET = FIELD_CORNER + LIP_FILLET

/**
 * A rounded rectangle (a pill while it's one line tall) filling [size], with a tab of [lip]'s size
 * rising above its top edge (negative y), [LIP_END_INSET] from the end, flaring into the field with
 * concave fillets.
 */
private fun DrawScope.pillWithLip(size: Size, lip: IntSize, layoutDirection: LayoutDirection): Path {
    val radius = FIELD_CORNER.toPx().coerceAtMost(size.height / 2)
    val path = Path().apply {
        moveTo(radius, 0f)
        if (lip.width > 0 && lip.height > 0) {
            val top = -lip.height.toFloat()
            val end = size.width - LIP_END_INSET.toPx()
            val start = end - lip.width
            val r = LIP_CORNER.toPx().coerceAtMost(lip.height / 2f)
            val f = LIP_FILLET.toPx().coerceAtMost(lip.height / 2f)
            lineTo(start - f, 0f)
            arcTo(Rect(start - 2 * f, -2 * f, start, 0f), 90f, -90f, false)
            lineTo(start, top + r)
            arcTo(Rect(start, top, start + 2 * r, top + 2 * r), 180f, 90f, false)
            lineTo(end - r, top)
            arcTo(Rect(end - 2 * r, top, end, top + 2 * r), 270f, 90f, false)
            lineTo(end, -f)
            arcTo(Rect(end, -2 * f, end + 2 * f, 0f), 180f, -90f, false)
        }
        lineTo(size.width - radius, 0f)
        arcTo(Rect(size.width - 2 * radius, 0f, size.width, 2 * radius), 270f, 90f, false)
        lineTo(size.width, size.height - radius)
        arcTo(Rect(size.width - 2 * radius, size.height - 2 * radius, size.width, size.height), 0f, 90f, false)
        lineTo(radius, size.height)
        arcTo(Rect(0f, size.height - 2 * radius, 2 * radius, size.height), 90f, 90f, false)
        lineTo(0f, radius)
        arcTo(Rect(0f, 0f, 2 * radius, 2 * radius), 180f, 90f, false)
        close()
    }
    // Laid out at the end edge; mirror for right-to-left
    if (layoutDirection == LayoutDirection.Rtl) {
        path.transform(Matrix().apply { scale(-1f, 1f); translate(-size.width, 0f) })
    }
    return path
}
