package com.marketcredits.app.ui.marketplace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketcredits.app.data.local.entity.ItemEntity
import com.marketcredits.app.data.local.entity.UserEntity
import com.marketcredits.app.data.repository.ItemRepository
import com.marketcredits.app.data.repository.UserRepository
import com.marketcredits.app.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MarketplaceItem(
    val item: ItemEntity,
    val sellerName: String
)

data class MarketplaceUiState(
    val currentUser: UserEntity? = null,
    val items: List<MarketplaceItem> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MarketplaceViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val userRepository: UserRepository,
    sessionManager: SessionManager
) : ViewModel() {

    val uiState: StateFlow<MarketplaceUiState> = sessionManager.currentUserId
        .filterNotNull()
        .flatMapLatest { userId ->
            combine(
                userRepository.getUserById(userId),
                itemRepository.getAvailableItemsExcludingSeller(userId),
                userRepository.getAllUsers()
            ) { currentUser, items, allUsers ->
                val nameById = allUsers.associateBy({ it.id }, { it.name })
                MarketplaceUiState(
                    currentUser = currentUser,
                    items = items.map { item -> MarketplaceItem(item, nameById[item.sellerId] ?: "Unknown") }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MarketplaceUiState())
}
