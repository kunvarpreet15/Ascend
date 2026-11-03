package com.kunvarpreet.ascend.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunvarpreet.ascend.domain.model.HunterProfile
import com.kunvarpreet.ascend.domain.model.RankTier
import com.kunvarpreet.ascend.ui.theme.BorderSubtle
import com.kunvarpreet.ascend.ui.theme.CardSurface
import com.kunvarpreet.ascend.ui.theme.DarkSurface
import com.kunvarpreet.ascend.ui.theme.ElectricCyan
import com.kunvarpreet.ascend.ui.theme.MonarchGold
import com.kunvarpreet.ascend.ui.theme.OnElectricCyan
import com.kunvarpreet.ascend.ui.theme.OnMonarchGold
import com.kunvarpreet.ascend.ui.theme.TextMuted
import com.kunvarpreet.ascend.ui.theme.TextPrimary

@Composable
fun HunterHUDHeader(
    hunter: HunterProfile,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .border(width = 1.dp, color = BorderSubtle, shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                RankBadge(rank = hunter.rankTier)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = hunter.alias,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = hunter.rankTier.title,
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Level Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CardSurface)
                    .border(1.dp, ElectricCyan, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "LVL ${hunter.level}",
                    color = ElectricCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // XP Energy Bar
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SYSTEM EXP",
                    color = ElectricCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${hunter.currentXp} / ${hunter.xpToNextLevel} XP",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            XPProgressBar(
                progress = hunter.xpProgress,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun RankBadge(
    rank: RankTier,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (rank) {
        RankTier.E -> Pair(CardSurface, TextPrimary)
        RankTier.D -> Pair(CardSurface, ElectricCyan)
        RankTier.C -> Pair(CardSurface, Color(0xFF60A5FA))
        RankTier.B -> Pair(CardSurface, Color(0xFFA78BFA))
        RankTier.A -> Pair(CardSurface, Color(0xFFF472B6))
        RankTier.S, RankTier.SSS -> Pair(MonarchGold, OnMonarchGold)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, if (rank == RankTier.S || rank == RankTier.SSS) MonarchGold else BorderSubtle, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = rank.name,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun XPProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "xp_bar"
    )

    Box(
        modifier = modifier
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(CardSurface)
            .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = animatedProgress)
                .height(8.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF0077B6),
                            ElectricCyan,
                            Color(0xFFE0F7FA)
                        )
                    )
                )
        )
    }
}
