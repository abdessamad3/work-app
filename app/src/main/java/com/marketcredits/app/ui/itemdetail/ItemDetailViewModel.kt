package com.marketcredits.app.ui.itemdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketcredits.app.data.local.entity.ItemEntity
import com.marketcredits.app.data.local.entity.UserEntity
import com.marketcredits.app.data.repository.BuyResult
import com.marketcredits.app.data.repository.ItemRepository
import com.marketcredits.app.data.repository.TransactionRepository
import com.marketcredits.app.data.repository.UserRepository
import com.marketcredits.app.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItemDetailUiState(
    val item: ItemEntity? = null,
    val sellerName: String = "",
    val currentUser: UserEntity? = null
) {
    val isOwnItem: Boolean get() = item != null && currentUser != null && item.sellerId == currentUser.id
    val canAfford: Boolean get() = item != null && currentUser != null && currentUser.creditBalance >= item.price
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    itemRepository: ItemRepository,
    userRepository: UserRepository,
    private val transactionRepository: TransactionRepository,
    sessionManager: SessionManager
) : ViewModel() {

    private val itemId: Int = checkNotNull(savedStateHandle.get<Int>("itemId"))

    val uiState: StateFlow<ItemDetailUiState> = sessionManager.currentUserId
        .filterNotNull()
        .flatMapLatest { userId ->
            combine(
                itemRepository.getItemById(itemId),
                userRepository.getAllUsers(),
                userRepository.getUserById(userId)
            ) { item, allUsers, currentUser ->
                ItemDetailUiState(
                    item = item,
                    sellerName = allUsers.find { it.id == item?.sellerId }?.name ?: "Unknown",
                    currentUser = currentUser
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ItemDetailUiState())

    private val _buyResult = MutableSharedFlow<BuyResult>()
    val buyResult: SharedFlow<BuyResult> = _buyResult.asSharedFlow()

    fun buy() {
        val currentUser = uiState.value.currentUser ?: return
        viewModelScope.launch {
            val result = transactionRepository.buyItem(itemId, currentUser.id)
            _buyResult.emit(result)
        }
    }
}
