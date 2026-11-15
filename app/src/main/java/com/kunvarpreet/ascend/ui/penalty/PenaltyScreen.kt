package com.kunvarpreet.ascend.ui.penalty

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import com.kunvarpreet.ascend.ui.theme.CrimsonDanger
import com.kunvarpreet.ascend.ui.theme.DangerSurface
import com.kunvarpreet.ascend.ui.theme.DarkSurface
import com.kunvarpreet.ascend.ui.theme.EmeraldSuccess
import com.kunvarpreet.ascend.ui.theme.OnCrimsonDanger
import com.kunvarpreet.ascend.ui.theme.TextMuted
import com.kunvarpreet.ascend.ui.theme.TextPrimary
import com.kunvarpreet.ascend.ui.theme.VoidBlack

@Composable
fun PenaltyScreen(
    modifier: Modifier = Modifier,
    viewModel: PenaltyViewModel = viewModel(),
    onPenaltyCleared: () -> Unit = {}
) {
    val activePenalty by viewModel.activePenalty.collectAsState()
    val hunterProfile by viewModel.hunterProfile.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val burpeesDone by viewModel.burpeesDone.collectAsState()
    val timerSecondsRemaining by viewModel.timerSecondsRemaining.collectAsState()
    val isTimerRunning by viewModel.isTimerRunning.collectAsState()
    val isSurvivalComplete by viewModel.isSurvivalComplete.collectAsState()

    val haptic = LocalHapticFeedback.current
    val multiplier = hunterProfile?.difficultyMultiplier ?: 1.0f
    val targetBurpees = viewModel.getTargetBurpees(multiplier)
    val targetSeconds = viewModel.getTargetWallSitSeconds(multiplier)
    val penaltyDescription = activePenalty?.penaltyTaskDescription ?: viewModel.getPenaltyTaskLabel(multiplier)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // High-Alert Warning Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DangerSurface)
                .border(2.dp, CrimsonDanger, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "⚠️ SYSTEM ALERT : PENALTY ZONE ⚠️",
                    color = CrimsonDanger,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "DAILY QUEST REQUIREMENTS WERE NOT FULFILLED BEFORE MIDNIGHT. SURVIVE THE TRIAL TO RESTORE HUNTER STANDING.",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Penalty Assignment Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurface)
                .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "DISCIPLINARY PROTOCOL ASSIGNMENT",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = penaltyDescription,
                    color = CrimsonDanger,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Mode Selector: Burpees vs Wall Sit
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(CardSurface)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isBurpees = selectedMode == PenaltyMode.BURPEES
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isBurpees) CrimsonDanger else CardSurface)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.setMode(PenaltyMode.BURPEES)
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "BURPEES ($targetBurpees)",
                    color = if (isBurpees) OnCrimsonDanger else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            val isWallSit = selectedMode == PenaltyMode.WALL_SIT
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isWallSit) CrimsonDanger else CardSurface)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.setMode(PenaltyMode.WALL_SIT)
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                val min = targetSeconds / 60
                Text(
                    text = "WALL SIT (${min} MIN)",
                    color = if (isWallSit) OnCrimsonDanger else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Active Counter Interface
        if (selectedMode == PenaltyMode.BURPEES) {
            // Burpees Rep Counter
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "REPETITIONS LOGGED",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$burpeesDone / $targetBurpees",
                    color = if (burpeesDone >= targetBurpees) EmeraldSuccess else CrimsonDanger,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                val progress = (burpeesDone.toFloat() / targetBurpees.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (progress >= 1f) EmeraldSuccess else CrimsonDanger,
                    trackColor = DangerSurface
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(1, 5, 10).forEach { inc ->
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(CardSurface)
                                .border(1.5.dp, CrimsonDanger, CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.addBurpees(inc)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+$inc",
                                color = CrimsonDanger,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        } else {
            // Wall Sit Countdown Timer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "WALL SIT DURATION REMAINING",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))

                val minutes = timerSecondsRemaining / 60
                val seconds = timerSecondsRemaining % 60
                val timeFormatted = String.format("%02d:%02d", minutes, seconds)

                Text(
                    text = timeFormatted,
                    color = if (timerSecondsRemaining <= 0) EmeraldSuccess else CrimsonDanger,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                val progress = ((targetSeconds - timerSecondsRemaining).toFloat() / targetSeconds.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (progress >= 1f) EmeraldSuccess else CrimsonDanger,
                    trackColor = DangerSurface
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleWallSitTimer()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTimerRunning) DangerSurface else CrimsonDanger,
                            contentColor = if (isTimerRunning) CrimsonDanger else OnCrimsonDanger
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isTimerRunning) "PAUSE TIMER" else "START TIMER",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.resetTimer()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CardSurface,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "RESET",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Success Indicator Banner if complete
        AnimatedVisibility(
            visible = isSurvivalComplete || burpeesDone >= targetBurpees || timerSecondsRemaining <= 0,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(EmeraldSuccess.copy(alpha = 0.15f))
                    .border(1.dp, EmeraldSuccess, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "TRIAL CLEARED: READY TO PURGE PENALTY",
                    color = EmeraldSuccess,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // "SURVIVE & CLEAR PENALTY" CTA Button
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val cleared = viewModel.surviveAndClearPenalty()
                if (cleared) {
                    onPenaltyCleared()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CrimsonDanger,
                contentColor = OnCrimsonDanger
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = "SURVIVE & CLEAR PENALTY",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
