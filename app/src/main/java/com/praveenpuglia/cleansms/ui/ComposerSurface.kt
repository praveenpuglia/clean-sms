package com.praveenpuglia.cleansms.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.praveenpuglia.cleansms.ui.newmessage.SmsCounter

/**
 * The bottom composer's surface. [lip] content (SMS counter, SIM switch) sits in a tab rising from the
 * top-end edge, so the text row keeps its full width. Pass null to hide the tab; it slides in and out.
 */
@Composable
fun ComposerSurface(
    lip: (@Composable RowScope.() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var lipSize by remember { mutableStateOf(IntSize.Zero) }
    // Keep drawing the last lip while it animates out
    var shownLip by remember { mutableStateOf(lip) }
    if (lip != null) shownLip = lip
    val shape = remember(lipSize, density) { LipShape(lipSize, density) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = shape,
        modifier = modifier.dropShadow(
            shape,
            Shadow(radius = 6.dp, color = MaterialTheme.colorScheme.scrim, alpha = 0.10f, offset = DpOffset(0.dp, (-1).dp)),
        ),
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
                Row(
                    modifier = Modifier
                        .height(LIP_TOUCH_HEIGHT)
                        .padding(start = 12.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) { shownLip?.invoke(this) }
            }
            content()
        }
    }
}

/**
 * The counter first and "2 SMS" after it, in fixed-width digits: as the count changes it only grows
 * to the start, so nothing to its end moves.
 */
@Composable
fun SmsCounterLabel(counter: SmsCounter, counterTag: String, partsTag: String) {
    val style = MaterialTheme.typography.labelMedium.merge(TextStyle(fontFeatureSettings = "tnum"))
    Row(
        modifier = Modifier.height(LIP_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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

/** Visible height of the tab. Its layout is [LIP_TOUCH_HEIGHT] tall so the SIM switch gets a 48dp target. */
val LIP_HEIGHT = 40.dp
private val LIP_TOUCH_HEIGHT = 48.dp
private val LIP_END_INSET = 12.dp
private val LIP_CORNER = 12.dp
private val LIP_FILLET = 8.dp

/**
 * A rectangle with a tab on its top edge, [LIP_END_INSET] from the end, flaring into the body with
 * concave fillets. [lip] is the tab's laid-out size; only its bottom [LIP_HEIGHT] is drawn.
 */
private class LipShape(private val lip: IntSize, private val density: Density) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        // The tab's layout is LIP_TOUCH_HEIGHT tall; only its bottom LIP_HEIGHT is drawn (all of it while it animates in)
        val top = with(this.density) { (lip.height - LIP_HEIGHT.toPx()).coerceAtLeast(0f) }
        val bodyTop = lip.height.toFloat()
        if (lip.width == 0 || top >= bodyTop) {
            return Outline.Rectangle(Rect(0f, bodyTop, size.width, size.height))
        }
        val inset = with(this.density) { LIP_END_INSET.toPx() }
        val lipHeight = bodyTop - top
        val r = with(this.density) { LIP_CORNER.toPx() }.coerceAtMost(lipHeight / 2)
        val f = with(this.density) { LIP_FILLET.toPx() }.coerceAtMost(lipHeight / 2).coerceAtMost(inset)
        // Laid out at the end edge; mirror for right-to-left
        val end = size.width - inset
        val start = end - lip.width
        val path = Path().apply {
            moveTo(0f, bodyTop)
            lineTo(start - f, bodyTop)
            arcTo(Rect(start - 2 * f, bodyTop - 2 * f, start, bodyTop), 90f, -90f, false)
            lineTo(start, top + r)
            arcTo(Rect(start, top, start + 2 * r, top + 2 * r), 180f, 90f, false)
            lineTo(end - r, top)
            arcTo(Rect(end - 2 * r, top, end, top + 2 * r), 270f, 90f, false)
            lineTo(end, bodyTop - f)
            arcTo(Rect(end, bodyTop - 2 * f, end + 2 * f, bodyTop), 180f, -90f, false)
            lineTo(size.width, bodyTop)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        if (layoutDirection == LayoutDirection.Rtl) {
            path.transform(androidx.compose.ui.graphics.Matrix().apply { scale(-1f, 1f); translate(-size.width, 0f) })
        }
        return Outline.Generic(path)
    }
}
