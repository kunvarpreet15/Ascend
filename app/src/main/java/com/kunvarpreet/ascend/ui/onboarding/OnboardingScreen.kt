package com.kunvarpreet.ascend.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kunvarpreet.ascend.ui.theme.BorderSubtle
import com.kunvarpreet.ascend.ui.theme.CardSurface
import com.kunvarpreet.ascend.ui.theme.DarkSurface
import com.kunvarpreet.ascend.ui.theme.ElectricCyan
import com.kunvarpreet.ascend.ui.theme.EmeraldSuccess
import com.kunvarpreet.ascend.ui.theme.MonarchGold
import com.kunvarpreet.ascend.ui.theme.OnElectricCyan
import com.kunvarpreet.ascend.ui.theme.OnEmeraldSuccess
import com.kunvarpreet.ascend.ui.theme.OnMonarchGold
import com.kunvarpreet.ascend.ui.theme.TextMuted
import com.kunvarpreet.ascend.ui.theme.TextPrimary
import com.kunvarpreet.ascend.ui.theme.VoidBlack
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = viewModel(),
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(16.dp)
    ) {
        when (uiState.step) {
            OnboardingStep.ASSESSMENT -> {
                AssessmentStep(
                    uiState = uiState,
                    onAliasChange = viewModel::updateAlias,
                    onHeightChange = viewModel::updateHeight,
                    onWeightChange = viewModel::updateWeight,
                    onDifficultyChange = viewModel::updateDifficulty,
                    onProceed = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.proceedToAwakening()
                    }
                )
            }
            OnboardingStep.AWAKENING_CEREMONY -> {
                AwakeningCeremonyStep(
                    uiState = uiState,
                    onAccept = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.completeAwakening(onOnboardingComplete)
                    }
                )
            }
        }
    }
}

@Composable
private fun AssessmentStep(
    uiState: OnboardingUiState,
    onAliasChange: (String) -> Unit,
    onHeightChange: (Float) -> Unit,
    onWeightChange: (Float) -> Unit,
    onDifficultyChange: (Float) -> Unit,
    onProceed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "SYSTEM AWAKENING PROTOCOL",
            color = ElectricCyan,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
        Text(
            text = "INITIAL CANDIDATE REGISTRATION & ASSESSMENT",
            color = TextMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 1. Codename Input
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "1. CODENAME DESIGNATION",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.alias,
                onValueChange = onAliasChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = ElectricCyan
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Height Slider
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "2. STATURE (HEIGHT)",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${uiState.heightCm.roundToInt()} cm",
                    color = ElectricCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Slider(
                value = uiState.heightCm,
                onValueChange = onHeightChange,
                valueRange = 140f..220f,
                steps = 79,
                colors = SliderDefaults.colors(
                    thumbColor = ElectricCyan,
                    activeTrackColor = ElectricCyan,
                    inactiveTrackColor = CardSurface
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Weight Slider
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "3. MASS (WEIGHT)",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = String.format(Locale.getDefault(), "%.1f kg", uiState.weightKg),
                    color = ElectricCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Slider(
                value = uiState.weightKg,
                onValueChange = onWeightChange,
                valueRange = 40f..150f,
                steps = 109,
                colors = SliderDefaults.colors(
                    thumbColor = ElectricCyan,
                    activeTrackColor = ElectricCyan,
                    inactiveTrackColor = CardSurface
                )
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. Regimen Difficulty Selection
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "4. TRAINING REGIMEN CAPACITY",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(10.dp))

            listOf(
                Triple(0.5f, "RECRUIT (0.5x)", "Entry Protocol: 50 reps / 1.5 km daily"),
                Triple(1.0f, "OPERATIVE (1.0x)", "Standard Protocol: 100 reps / 3.0 km daily [RECOMMENDED]"),
                Triple(1.5f, "MONARCH (1.5x)", "Extreme Protocol: 150 reps / 4.5 km daily")
            ).forEach { (mult, label, desc) ->
                val isSelected = uiState.difficultyMultiplier == mult
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) ElectricCyan.copy(alpha = 0.12f) else CardSurface)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) ElectricCyan else BorderSubtle,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onDifficultyChange(mult) }
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = label,
                            color = if (isSelected) ElectricCyan else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = desc,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Continue Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ElectricCyan)
                .clickable { onProceed() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "INITIALIZE AWAKENING",
                color = OnElectricCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AwakeningCeremonyStep(
    uiState: OnboardingUiState,
    onAccept: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SYSTEM AWAKENING CEREMONY",
            color = MonarchGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Holographic Rank Badge
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(DarkSurface)
                .border(2.dp, MonarchGold, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "E",
                    color = MonarchGold,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "RANK",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "HUNTER AWAKENED",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "DESIGNATION: ${uiState.alias.uppercase()}",
            color = ElectricCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Attribute Matrix Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CardSurface)
                .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "BASE ATTRIBUTE CAPACITY ALLOCATED",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    listOf("STR", "AGI", "SEN", "VIT", "INT").forEach { stat ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stat,
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "10",
                                color = ElectricCyan,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Assigned Quest Deck Notice
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurface)
                .border(1.dp, MonarchGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = "NOTICE: DAILY QUEST INITIATED",
                    color = MonarchGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                val multiplier = uiState.difficultyMultiplier
                val pushups = (100 * multiplier).roundToInt()
                val km = String.format(Locale.getDefault(), "%.1f", 3.0f * multiplier)
                Text(
                    text = "Complete $pushups Push-ups, $pushups Sit-ups, $pushups Squats, and a ${km}km Run before midnight to prevent system penalty activation.",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Accept CTA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(EmeraldSuccess)
                .clickable { onAccept() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ACCEPT QUEST & ENTER SYSTEM",
                color = OnEmeraldSuccess,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
    }
}
