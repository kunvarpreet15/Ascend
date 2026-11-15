package com.kunvarpreet.ascend.domain.model

import java.util.UUID

data class PenaltyLog(
    val id: String = UUID.randomUUID().toString(),
    val hunterId: String,
    val triggeredDate: String,
    val penaltyTaskDescription: String,
    val isCleared: Boolean = false,
    val clearedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
