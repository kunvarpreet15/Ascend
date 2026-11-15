package com.kunvarpreet.ascend.domain.model

data class InventoryItem(
    val id: String,
    val hunterId: String,
    val itemCode: String,
    val itemName: String,
    val quantity: Int = 1,
    val acquiredAt: Long = System.currentTimeMillis()
)
