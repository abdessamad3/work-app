package com.marketcredits.app.ui.myprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketcredits.app.data.local.entity.ItemEntity
import com.marketcredits.app.data.local.entity.TransactionEntity
import com.marketcredits.app.data.local.entity.UserEntity
import com.marketcredits.app.data.repository.ItemRepository
import com.marketcredits.app.data.repository.TransactionRepository
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

data class TransactionDisplay(
    val transaction: TransactionEntity,
    val counterpartyName: String,
    val wasSale: Boolean
)

data class MyProfileUiState(
    val user: UserEntity? = null,
    val myListings: List<ItemEntity> = emptyList(),
    val history: List<TransactionDisplay> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyProfileViewModel @Inject constructor(
    userRepository: UserRepository,
    itemRepository: ItemRepository,
    transactionRepository: TransactionRepository,
    sessionManager: SessionManager
) : ViewModel() {

    val uiState: StateFlow<MyProfileUiState> = sessionManager.currentUserId
        .filterNotNull()
        .flatMapLatest { userId ->
            combine(
                userRepository.getUserById(userId),
                itemRepository.getItemsBySeller(userId),
                transactionRepository.getTransactionsForUser(userId),
                userRepository.getAllUsers()
            ) { user, listings, transactions, allUsers ->
                val nameById = allUsers.associateBy({ it.id }, { it.name })
                MyProfileUiState(
                    user = user,
                    myListings = listings,
                    history = transactions.map { tx ->
                        val wasSale = tx.sellerId == userId
                        val counterpartyId = if (wasSale) tx.buyerId else tx.sellerId
                        TransactionDisplay(
                            transaction = tx,
                            counterpartyName = nameById[counterpartyId] ?: "Unknown",
                            wasSale = wasSale
                        )
                    }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MyProfileUiState())
}
