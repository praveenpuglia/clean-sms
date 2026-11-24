package com.praveenpuglia.cleansms.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
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
    val density = LocalDensity.current
    var lipSize by remember { mutableStateOf(IntSize.Zero) }
    // Keep drawing the last lip while it animates out
    var shownLip by remember { mutableStateOf(lip) }
    if (lip != null) shownLip = lip
    val shape = remember(lipSize, density) { PillWithLipShape(lipSize, density) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        modifier = modifier,
    ) {
        Column {
            AnimatedVisibility(
                visible = lip != null,
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = LIP_END_INSET)
                    .onSizeChanged { lipSize = it },
            ) {
                Box(Modifier.height(LIP_HEIGHT).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    shownLip?.invoke()
                }
            }
            Row(verticalAlignment = Alignment.Bottom, content = content)
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
 * A rounded rectangle (a pill while it's one line tall) with a tab on its top edge, [LIP_END_INSET]
 * from the end, flaring into the field with concave fillets. [lip] is the tab's laid-out size.
 */
private class PillWithLipShape(private val lip: IntSize, private val density: Density) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val px = this.density
        val bodyTop = lip.height.toFloat()
        val radius = with(px) { FIELD_CORNER.toPx() }.coerceAtMost((size.height - bodyTop) / 2)
        val path = Path().apply {
            moveTo(radius, bodyTop)
            if (lip.width > 0 && lip.height > 0) {
                val end = size.width - with(px) { LIP_END_INSET.toPx() }
                val start = end - lip.width
                val r = with(px) { LIP_CORNER.toPx() }.coerceAtMost(bodyTop / 2)
                val f = with(px) { LIP_FILLET.toPx() }.coerceAtMost(bodyTop / 2)
                lineTo(start - f, bodyTop)
                arcTo(Rect(start - 2 * f, bodyTop - 2 * f, start, bodyTop), 90f, -90f, false)
                lineTo(start, r)
                arcTo(Rect(start, 0f, start + 2 * r, 2 * r), 180f, 90f, false)
                lineTo(end - r, 0f)
                arcTo(Rect(end - 2 * r, 0f, end, 2 * r), 270f, 90f, false)
                lineTo(end, bodyTop - f)
                arcTo(Rect(end, bodyTop - 2 * f, end + 2 * f, bodyTop), 180f, -90f, false)
            }
            lineTo(size.width - radius, bodyTop)
            arcTo(Rect(size.width - 2 * radius, bodyTop, size.width, bodyTop + 2 * radius), 270f, 90f, false)
            lineTo(size.width, size.height - radius)
            arcTo(Rect(size.width - 2 * radius, size.height - 2 * radius, size.width, size.height), 0f, 90f, false)
            lineTo(radius, size.height)
            arcTo(Rect(0f, size.height - 2 * radius, 2 * radius, size.height), 90f, 90f, false)
            lineTo(0f, bodyTop + radius)
            arcTo(Rect(0f, bodyTop, 2 * radius, bodyTop + 2 * radius), 180f, 90f, false)
            close()
        }
        // Laid out at the end edge; mirror for right-to-left
        if (layoutDirection == LayoutDirection.Rtl) {
            path.transform(Matrix().apply { scale(-1f, 1f); translate(-size.width, 0f) })
        }
        return Outline.Generic(path)
    }
}
