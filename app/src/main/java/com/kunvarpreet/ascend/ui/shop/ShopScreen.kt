package com.kunvarpreet.ascend.ui.shop

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.kunvarpreet.ascend.ui.theme.CrimsonDanger
import com.kunvarpreet.ascend.ui.theme.DarkSurface
import com.kunvarpreet.ascend.ui.theme.ElectricCyan
import com.kunvarpreet.ascend.ui.theme.EmeraldSuccess
import com.kunvarpreet.ascend.ui.theme.MonarchGold
import com.kunvarpreet.ascend.ui.theme.OnMonarchGold
import com.kunvarpreet.ascend.ui.theme.TextMuted
import com.kunvarpreet.ascend.ui.theme.TextPrimary
import com.kunvarpreet.ascend.ui.theme.VoidBlack

@Composable
fun ShopScreen(
    modifier: Modifier = Modifier,
    viewModel: ShopViewModel = viewModel()
) {
    val hunter by viewModel.hunterProfile.collectAsState()
    val shieldsOwned by viewModel.streakShieldsOwned.collectAsState()
    val isTitleOwned by viewModel.isTitleOwned.collectAsState()
    val message by viewModel.purchaseMessage.collectAsState()

    val haptic = LocalHapticFeedback.current
    val goldBalance = hunter?.goldBalance ?: 0L

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
    ) {
        // Hunter HUD Header at top to view equipped title and XP
        hunter?.let {
            HunterHUDHeader(hunter = it)
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row with Gold Balance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SYSTEM ARMORY & SHOP",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "EXCHANGE GOLD FOR RECOVERY & POWER",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Gold Balance Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurface)
                        .border(1.dp, MonarchGold, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$goldBalance GOLD",
                        color = MonarchGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Purchase Message Banner
            AnimatedVisibility(
                visible = message != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val isError = message?.contains("INSUFFICIENT", ignoreCase = true) == true ||
                    message?.contains("ERROR", ignoreCase = true) == true

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isError) CrimsonDanger.copy(alpha = 0.15f) else EmeraldSuccess.copy(alpha = 0.15f))
                        .border(1.dp, if (isError) CrimsonDanger else EmeraldSuccess, RoundedCornerShape(8.dp))
                        .clickable { viewModel.dismissMessage() }
                        .padding(12.dp)
                ) {
                    Text(
                        text = message ?: "",
                        color = if (isError) CrimsonDanger else EmeraldSuccess,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Item 1: Streak Shield (150 Gold)
            ShopItemCard(
                title = "STREAK PRESERVATION SHIELD",
                itemCode = "STREAK_SHIELD",
                description = "Protects your discipline streak if a daily quest is missed. Automatically consumed by CatchUpEvaluator on missed days.",
                cost = 150L,
                goldBalance = goldBalance,
                statusTag = if (shieldsOwned > 0) "IN INVENTORY: $shieldsOwned" else null,
                actionLabel = "ACQUIRE (150 G)",
                onPurchase = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.purchaseItem("STREAK_SHIELD")
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Item 2: Stamina Potion (100 Gold)
            ShopItemCard(
                title = "STAMINA POTION",
                itemCode = "STAMINA_POTION",
                description = "Instant +75 XP consumable boost to accelerate hunter rank progression and stat point allocations.",
                cost = 100L,
                goldBalance = goldBalance,
                actionLabel = "CONSUME (+75 XP / 100 G)",
                onPurchase = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.purchaseItem("STAMINA_POTION")
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Item 3: Title: Ascended Sovereign (500 Gold)
            ShopItemCard(
                title = "TITLE: ASCENDED SOVEREIGN",
                itemCode = "TITLE_ASCENDED_SOVEREIGN",
                description = "Unlocks and equips the exclusive 'ASCENDED SOVEREIGN' prestige title badge on your Hunter HUD header.",
                cost = 500L,
                goldBalance = goldBalance,
                statusTag = if (isTitleOwned) "OWNED & EQUIPPED" else null,
                isOwned = isTitleOwned,
                actionLabel = if (isTitleOwned) "EQUIPPED" else "ACQUIRE TITLE (500 G)",
                onPurchase = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (!isTitleOwned) {
                        viewModel.purchaseItem("TITLE_ASCENDED_SOVEREIGN")
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ShopItemCard(
    title: String,
    itemCode: String,
    description: String,
    cost: Long,
    goldBalance: Long,
    actionLabel: String,
    statusTag: String? = null,
    isOwned: Boolean = false,
    onPurchase: () -> Unit
) {
    val canAfford = goldBalance >= cost

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardSurface)
            .border(1.dp, if (isOwned) MonarchGold else BorderSubtle, RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = MonarchGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                statusTag?.let {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DarkSurface)
                            .border(1.dp, if (isOwned) MonarchGold else ElectricCyan, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = it,
                            color = if (isOwned) MonarchGold else ElectricCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "COST: $cost GOLD",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )

                Button(
                    onClick = onPurchase,
                    enabled = !isOwned && canAfford,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MonarchGold,
                        contentColor = OnMonarchGold,
                        disabledContainerColor = DarkSurface,
                        disabledContentColor = TextMuted
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = actionLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
