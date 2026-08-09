package com.praveenpuglia.cleansms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

@Composable
fun ComposerTopShadow() {
    val shadow = MaterialTheme.colorScheme.scrim
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Brush.verticalGradient(listOf(shadow.copy(alpha = 0f), shadow.copy(alpha = 0.08f)))),
    )
}
