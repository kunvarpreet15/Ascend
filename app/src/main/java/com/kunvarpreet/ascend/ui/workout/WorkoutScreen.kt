package com.kunvarpreet.ascend.ui.workout

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunvarpreet.ascend.ui.theme.BorderSubtle
import com.kunvarpreet.ascend.ui.theme.CardSurface
import com.kunvarpreet.ascend.ui.theme.DarkSurface
import com.kunvarpreet.ascend.ui.theme.ElectricCyan
import com.kunvarpreet.ascend.ui.theme.EmeraldSuccess
import com.kunvarpreet.ascend.ui.theme.OnElectricCyan
import com.kunvarpreet.ascend.ui.theme.OnEmeraldSuccess
import com.kunvarpreet.ascend.ui.theme.TextMuted
import com.kunvarpreet.ascend.ui.theme.TextPrimary
import com.kunvarpreet.ascend.ui.theme.VoidBlack
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun WorkoutScreen(
    targetId: String,
    viewModel: WorkoutViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current

    var restSecondsRemaining by remember { mutableIntStateOf(0) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var selectedChipSeconds by remember { mutableIntStateOf(60) }

    LaunchedEffect(targetId) {
        viewModel.loadTarget(targetId)
    }

    LaunchedEffect(isTimerRunning, restSecondsRemaining) {
        if (isTimerRunning && restSecondsRemaining > 0) {
            delay(1000L)
            val next = restSecondsRemaining - 1
            restSecondsRemaining = next
            if (next == 0) {
                isTimerRunning = false
                try {
                    val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 350)
                } catch (e: Throwable) {
                    // Audio stream fallback
                }
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    if (uiState.isLoading || uiState.target == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(VoidBlack),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = ElectricCyan)
        }
        return
    }

    val target = uiState.target!!
    val animatedProgress by animateFloatAsState(
        targetValue = target.progress,
        animationSpec = tween(300),
        label = "circular_gauge"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top App Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Dashboard",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = target.exerciseType.displayName.uppercase(),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "TARGET: ${target.formattedTarget}",
                    color = ElectricCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Circular Rep Gauge
        Box(
            modifier = Modifier.size(220.dp),
            contentAlignment = Alignment.Center
        ) {
            // Track
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                color = CardSurface,
                strokeWidth = 14.dp,
            )
            // Active Arc
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxSize(),
                color = if (target.isCompleted) EmeraldSuccess else ElectricCyan,
                strokeWidth = 14.dp,
            )

            // Centered metrics
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = target.formattedCompleted,
                    color = if (target.isCompleted) EmeraldSuccess else TextPrimary,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "OF ${target.formattedTarget}",
                    color = TextMuted,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (target.isCompleted) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(EmeraldSuccess)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "COMPLETED",
                            color = OnEmeraldSuccess,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Rest Interval Timer Card
        val formattedRestTime = String.format(
            Locale.getDefault(),
            "%02d:%02d",
            restSecondsRemaining / 60,
            restSecondsRemaining % 60
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardSurface)
                .border(
                    width = 1.dp,
                    color = if (isTimerRunning) ElectricCyan else BorderSubtle,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "REST INTERVAL TIMER",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = formattedRestTime,
                        color = if (isTimerRunning) ElectricCyan else TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(30, 60, 90).forEach { seconds ->
                        val isSelected = isTimerRunning && selectedChipSeconds == seconds
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) ElectricCyan.copy(alpha = 0.2f) else DarkSurface)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) ElectricCyan else BorderSubtle,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedChipSeconds = seconds
                                    restSecondsRemaining = seconds
                                    isTimerRunning = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${seconds}s",
                                color = if (isSelected) ElectricCyan else TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    if (isTimerRunning) {
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurface)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isTimerRunning = false
                                    restSecondsRemaining = 0
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "STOP",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Rep Increment Chips (+5, +10, +20)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(5f, 10f, 20f).forEach { reps ->
                Box(
                    modifier = Modifier
                        .size(width = 96.dp, height = 50.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardSurface)
                        .border(1.dp, ElectricCyan, RoundedCornerShape(10.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.logReps(target.id, reps)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+${reps.toInt()}",
                        color = ElectricCyan,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Secondary +1 increment chip
        Box(
            modifier = Modifier
                .size(width = 96.dp, height = 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.logReps(target.id, 1f)
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+1 REP",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bottom CTA
        if (target.isCompleted) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(EmeraldSuccess)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Done",
                        tint = OnEmeraldSuccess,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RETURN TO DASHBOARD",
                        color = OnEmeraldSuccess,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ElectricCyan)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SAVE & FINISH SESSION",
                    color = OnElectricCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
