package com.kunvarpreet.ascend.ui.evolution

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunvarpreet.ascend.ui.theme.BorderSubtle
import com.kunvarpreet.ascend.ui.theme.CardSurface
import com.kunvarpreet.ascend.ui.theme.ElectricCyan
import com.kunvarpreet.ascend.ui.theme.TextMuted
import com.kunvarpreet.ascend.ui.theme.TextPrimary
import com.kunvarpreet.ascend.ui.theme.VoidBlack

@Composable
fun EvolutionScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(16.dp)
    ) {
        Text(
            text = "BODY EVOLUTION ARCHIVE",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "TRANSFORMATION HEATMAP & METRICS",
            color = TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(CardSurface)
                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "CONSISTENCY CALENDAR",
                    color = ElectricCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Track your daily workout completion history and body composition milestones.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}
