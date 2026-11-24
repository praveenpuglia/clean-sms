package com.praveenpuglia.cleansms.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.praveenpuglia.cleansms.R

/** The inline SIM badge next to timestamps: small enough to sit with 11-12sp text. */
val SimIndicatorSize = 14.dp

/** Space between the badge's edge and its card outline (Material Symbols sim_card spans x=160..800 of 960). */
val SimIndicatorEndInset = SimIndicatorSize * (160f / 960f)

@Composable
fun SimIndicator(
    slot: Int?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    description: String? = null,
) {
    SimGlyph(
        slot = slot,
        color = color,
        width = SimIndicatorSize,
        height = SimIndicatorSize,
        fontSize = 7.sp,
        topPadding = 1.dp,
        modifier = modifier.clearAndSetSemantics {
            if (description != null) contentDescription = description
        },
    )
}

/**
 * The SIM to send from, as a small outlined pill in the composer's lip; tap to switch. The touch target
 * is the lip's full 48dp height, while the pill sits centred in the visible part.
 */
@Composable
fun SimSwitch(
    slot: Int,
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier
            .fillMaxHeight()
            .widthIn(min = 48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        // Outlined like an M3 chip: a tonal fill is nearly the lip's own colour in light themes, while the
        // outline keeps the 3:1 contrast a control's edge needs in both themes
        Surface(
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = CircleShape,
            modifier = Modifier
                .padding(bottom = (LIP_HEIGHT - SIM_PILL_HEIGHT) / 2)
                .height(SIM_PILL_HEIGHT)
                .clip(CircleShape)
                .indication(interaction, ripple()),
        ) {
            Crossfade(
                targetState = slot,
                animationSpec = tween(160),
                label = "sim_slot_switch",
                modifier = Modifier.padding(horizontal = 12.dp),
            ) { currentSlot ->
                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                    SimGlyph(
                        slot = currentSlot,
                        color = LocalContentColor.current,
                        width = 18.dp,
                        height = 18.dp,
                        fontSize = 9.sp,
                        topPadding = 1.5.dp,
                    )
                }
            }
        }
    }
}

private val SIM_PILL_HEIGHT = 28.dp

@Composable
private fun SimGlyph(
    slot: Int?,
    color: Color,
    width: Dp,
    height: Dp,
    fontSize: TextUnit,
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = width, height = height),
    ) {
        Icon(
            painterResource(R.drawable.ic_sim_outline),
            contentDescription = null,
            tint = color,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            (slot ?: "?").toString(),
            color = color,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = topPadding),
        )
    }
}
