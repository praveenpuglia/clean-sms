package com.praveenpuglia.cleansms.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import com.praveenpuglia.cleansms.R

data class OnboardingUiState(
    val isDefaultSmsApp: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
)

object OnboardingTestTags {
    const val SET_DEFAULT = "onboarding_set_default"
    const val ALLOW_BACKGROUND = "onboarding_allow_background"
    const val CONTINUE = "onboarding_continue"
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onSetDefault: () -> Unit,
    onAllowBackground: () -> Unit,
    onContinue: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(32.dp))
                val logoDescription = stringResource(R.string.onboarding_logo_description)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .semantics { contentDescription = logoDescription },
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_background),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.28).sp,
                    ),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.onboarding_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(32.dp))
                SetupStep(
                    number = 1,
                    title = stringResource(R.string.onboarding_default_title),
                    description = stringResource(R.string.onboarding_default_description),
                    completed = state.isDefaultSmsApp,
                ) {
                    Button(
                        onClick = onSetDefault,
                        enabled = !state.isDefaultSmsApp,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(OnboardingTestTags.SET_DEFAULT),
                    ) {
                        Text(stringResource(R.string.onboarding_set_default))
                    }
                }
                Spacer(Modifier.height(12.dp))
                SetupStep(
                    number = 2,
                    title = stringResource(R.string.onboarding_background_title),
                    description = stringResource(R.string.onboarding_background_description),
                    completed = state.isBatteryOptimizationIgnored,
                ) {
                    FilledTonalButton(
                        onClick = onAllowBackground,
                        enabled = !state.isBatteryOptimizationIgnored,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(OnboardingTestTags.ALLOW_BACKGROUND),
                    ) {
                        Text(stringResource(R.string.onboarding_allow))
                    }
                }
                Spacer(Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.onboarding_privacy_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(32.dp))
            }

            Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
                Button(
                    onClick = onContinue,
                    enabled = state.isDefaultSmsApp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp)
                        .testTag(OnboardingTestTags.CONTINUE),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_continue),
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupStep(
    number: Int,
    title: String,
    description: String,
    completed: Boolean,
    action: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Text(
                        text = number.toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (completed) {
                    val completedDescription = stringResource(R.string.onboarding_completed)
                    Image(
                        painter = painterResource(R.drawable.ic_tick_single),
                        contentDescription = completedDescription,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 40.dp, top = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}
