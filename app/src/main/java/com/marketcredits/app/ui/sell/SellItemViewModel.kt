package com.marketcredits.app.ui.sell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketcredits.app.data.repository.ItemRepository
import com.marketcredits.app.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SellItemViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _itemCreated = MutableSharedFlow<Unit>()
    val itemCreated: SharedFlow<Unit> = _itemCreated.asSharedFlow()

    fun createItem(title: String, description: String, price: Int) {
        if (title.isBlank() || price <= 0) return
        viewModelScope.launch {
            val sellerId = sessionManager.currentUserId.filterNotNull().first()
            itemRepository.createItem(sellerId, title.trim(), description.trim(), price)
            _itemCreated.emit(Unit)
        }
    }
}
