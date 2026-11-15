package com.kunvarpreet.ascend.ui.shop

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kunvarpreet.ascend.data.local.AscendDatabaseHelper
import com.kunvarpreet.ascend.data.repository.HunterRepository
import com.kunvarpreet.ascend.domain.model.HunterProfile
import com.kunvarpreet.ascend.domain.model.InventoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShopViewModel(application: Application) : AndroidViewModel(application) {
    private val hunterRepo = HunterRepository(application)

    val hunterProfile: StateFlow<HunterProfile?> = hunterRepo.hunterProfile
    val inventoryState: StateFlow<List<InventoryItem>> = hunterRepo.inventoryState

    private val _purchaseMessage = MutableStateFlow<String?>(null)
    val purchaseMessage: StateFlow<String?> = _purchaseMessage.asStateFlow()

    val streakShieldsOwned: StateFlow<Int> = inventoryState.map { items ->
        items.firstOrNull { it.itemCode == "STREAK_SHIELD" }?.quantity ?: 0
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val isTitleOwned: StateFlow<Boolean> = inventoryState.map { items ->
        items.any { it.itemCode == "TITLE_ASCENDED_SOVEREIGN" }
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun purchaseItem(itemCode: String): Boolean {
        val result = hunterRepo.purchaseShopItem(itemCode)
        return when (result) {
            is AscendDatabaseHelper.PurchaseResult.Success -> {
                val msg = when (itemCode) {
                    "STREAK_SHIELD" -> "Acquired Streak Shield! 150 Gold deducted."
                    "STAMINA_POTION" -> "Consumed Stamina Potion! +75 XP granted."
                    "TITLE_ASCENDED_SOVEREIGN" -> "Title Unlocked & Equipped: Ascended Sovereign!"
                    else -> "Purchase successful!"
                }
                _purchaseMessage.value = msg
                true
            }
            is AscendDatabaseHelper.PurchaseResult.InsufficientGold -> {
                _purchaseMessage.value = "INSUFFICIENT GOLD BALANCE!"
                false
            }
            is AscendDatabaseHelper.PurchaseResult.Error -> {
                _purchaseMessage.value = "PURCHASE ERROR: ${result.message}"
                false
            }
        }
    }

    fun dismissMessage() {
        _purchaseMessage.value = null
    }

    fun refresh() {
        hunterRepo.refresh()
    }
}
