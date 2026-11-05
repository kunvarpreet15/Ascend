package com.kunvarpreet.ascend.ui.status

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kunvarpreet.ascend.ui.components.HunterHUDHeader
import com.kunvarpreet.ascend.ui.theme.BorderSubtle
import com.kunvarpreet.ascend.ui.theme.CardSurface
import com.kunvarpreet.ascend.ui.theme.DarkSurface
import com.kunvarpreet.ascend.ui.theme.ElectricCyan
import com.kunvarpreet.ascend.ui.theme.MonarchGold
import com.kunvarpreet.ascend.ui.theme.OnMonarchGold
import com.kunvarpreet.ascend.ui.theme.TextMuted
import com.kunvarpreet.ascend.ui.theme.TextPrimary
import com.kunvarpreet.ascend.ui.theme.VoidBlack

@Composable
fun StatusScreen(
    viewModel: StatusViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val hunter by viewModel.hunterProfile.collectAsState()
    val stats by viewModel.statsState.collectAsState()
    val haptic = LocalHapticFeedback.current

    val availablePoints = hunter?.availableStatPoints ?: 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
    ) {
        // Hunter HUD Header
        hunter?.let {
            HunterHUDHeader(hunter = it)
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "HUNTER STATUS SHEET",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "ATTRIBUTE MATRIX & CAPACITY EVALUATION",
                color = TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Available Stat Points Banner
            val isPointsAvailable = availablePoints > 0
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isPointsAvailable) MonarchGold.copy(alpha = 0.12f) else DarkSurface)
                    .border(
                        width = if (isPointsAvailable) 1.5.dp else 1.dp,
                        color = if (isPointsAvailable) MonarchGold else BorderSubtle,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AVAILABLE STAT POINTS: $availablePoints",
                            color = if (isPointsAvailable) MonarchGold else TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (isPointsAvailable) "Tap [+] to invest points into hunter attributes" else "Gain 5 stat points upon every level up",
                            color = if (isPointsAvailable) TextPrimary else TextMuted,
                            fontSize = 10.sp
                        )
                    }

                    if (isPointsAvailable) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MonarchGold)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "READY",
                                color = OnMonarchGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val statItems = listOf(
                StatRowData("strength", "STRENGTH (STR)", stats?.strength ?: 10, "Physical power & pushups capacity"),
                StatRowData("agility", "AGILITY (AGI)", stats?.agility ?: 10, "Speed & running cadence efficiency"),
                StatRowData("sense", "SENSE (SEN)", stats?.sense ?: 10, "Discipline & habit streak resilience"),
                StatRowData("vitality", "VITALITY (VIT)", stats?.vitality ?: 10, "Endurance & recovery factor"),
                StatRowData("intelligence", "INTELLIGENCE (INT)", stats?.intelligence ?: 10, "Quest reward multipliers (+2% XP/pt)")
            )

            statItems.forEach { item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = item.desc,
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = item.value.toString(),
                                color = ElectricCyan,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )

                            if (isPointsAvailable) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MonarchGold)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.allocateStat(item.key)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+",
                                        color = OnMonarchGold,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class StatRowData(
    val key: String,
    val name: String,
    val value: Int,
    val desc: String
)
