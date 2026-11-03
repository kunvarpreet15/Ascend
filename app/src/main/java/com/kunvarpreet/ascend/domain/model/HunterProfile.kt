package com.kunvarpreet.ascend.domain.model

import java.util.UUID

enum class RankTier(val title: String, val minLevel: Int) {
    E("E-Rank Novice", 1),
    D("D-Rank Striker", 10),
    C("C-Rank Adept", 25),
    B("B-Rank Elite", 45),
    A("A-Rank Champion", 70),
    S("S-Rank Monarch", 100),
    SSS("SSS-Rank Sovereign", 150)
}

data class HunterProfile(
    val id: String = UUID.randomUUID().toString(),
    val alias: String = "SHADOW_HUNTER",
    val avatarId: String = "default_avatar",
    val level: Int = 1,
    val currentXp: Long = 0,
    val xpToNextLevel: Long = 100,
    val rankTier: RankTier = RankTier.E,
    val availableStatPoints: Int = 0,
    val currentStreak: Int = 1,
    val longestStreak: Int = 1,
    val goldBalance: Long = 100,
    val heightCm: Float = 175.0f,
    val weightKg: Float = 70.0f,
    val isOnboarded: Boolean = true
) {
    val xpProgress: Float
        get() = if (xpToNextLevel > 0) (currentXp.toFloat() / xpToNextLevel.toFloat()).coerceIn(0f, 1f) else 0f
}

data class AttributeStats(
    val hunterId: String,
    val strength: Int = 10,
    val agility: Int = 10,
    val sense: Int = 10,
    val vitality: Int = 10,
    val intelligence: Int = 10
)
