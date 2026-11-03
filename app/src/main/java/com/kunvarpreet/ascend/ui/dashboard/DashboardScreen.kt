package com.kunvarpreet.ascend.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunvarpreet.ascend.domain.model.ExerciseTarget
import com.kunvarpreet.ascend.domain.model.ExerciseType
import com.kunvarpreet.ascend.ui.components.HunterHUDHeader
import com.kunvarpreet.ascend.ui.components.QuestDeckCard
import com.kunvarpreet.ascend.ui.theme.BorderSubtle
import com.kunvarpreet.ascend.ui.theme.CardSurface
import com.kunvarpreet.ascend.ui.theme.CrimsonDanger
import com.kunvarpreet.ascend.ui.theme.ElectricCyan
import com.kunvarpreet.ascend.ui.theme.MonarchGold
import com.kunvarpreet.ascend.ui.theme.TextMuted
import com.kunvarpreet.ascend.ui.theme.TextPrimary
import com.kunvarpreet.ascend.ui.theme.VoidBlack

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onTargetClick: (ExerciseTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading || uiState.hunter == null) {
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

    val hunter = uiState.hunter!!
    val todayQuest = uiState.todayQuest

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
    ) {
        // Top Hunter HUD Header
        HunterHUDHeader(hunter = hunter)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                // System Quest Deadline Banner
                SystemDeadlineCard(timeRemaining = uiState.timeRemainingUntilMidnight)
            }

            item {
                // Streak Card
                StreakStatusCard(streakDays = hunter.currentStreak)
            }

            item {
                // Section Title: Daily Quest Deck
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DAILY QUEST DECK",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    val completedCount = todayQuest?.targets?.count { it.isCompleted } ?: 0
                    val totalCount = todayQuest?.targets?.size ?: 0
                    Text(
                        text = "$completedCount / $totalCount COMPLETED",
                        color = ElectricCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // List of Exercise Target Cards
            if (todayQuest != null && todayQuest.targets.isNotEmpty()) {
                items(todayQuest.targets, key = { it.id }) { target ->
                    QuestDeckCard(
                        target = target,
                        onClick = { onTargetClick(target) }
                    )
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CardSurface)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NO ACTIVE QUESTS GENERATED",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SystemDeadlineCard(
    timeRemaining: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1317))
            .border(1.dp, CrimsonDanger, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Deadline Warning",
                tint = CrimsonDanger,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = "SYSTEM DEADLINE : SURVIVE OR PENALTY",
                    color = CrimsonDanger,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Resets in: $timeRemaining",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun StreakStatusCard(
    streakDays: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardSurface)
            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = "Streak",
                    tint = MonarchGold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "DISCIPLINE STREAK",
                        color = MonarchGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "$streakDays DAY${if (streakDays > 1) "S" else ""}",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Text(
                text = "THE SYSTEM REWARDS CONSISTENCY",
                color = TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
