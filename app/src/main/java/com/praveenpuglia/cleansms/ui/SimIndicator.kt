package com.praveenpuglia.cleansms.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
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
        width = 18.dp,
        height = 17.dp,
        fontSize = 8.5.sp,
        topPadding = 2.dp,
        modifier = modifier.clearAndSetSemantics {
            if (description != null) contentDescription = description
        },
    )
}

@Composable
fun SimSelectorButton(
    slot: Int,
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Crossfade(
            targetState = slot,
            animationSpec = tween(160),
            label = "sim_slot_switch",
        ) { currentSlot ->
            SimGlyph(
                slot = currentSlot,
                color = LocalContentColor.current,
                width = 32.dp,
                height = 32.dp,
                fontSize = 10.sp,
                topPadding = 3.dp,
            )
        }
    }
}

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
