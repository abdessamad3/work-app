package com.marketcredits.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketcredits.app.data.local.entity.UserEntity
import com.marketcredits.app.data.repository.UserRepository
import com.marketcredits.app.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private val AVATAR_COLORS = listOf("#EF5350", "#42A5F5", "#66BB6A", "#FFA726", "#AB47BC", "#26C6DA")

@HiltViewModel
class ProfileSwitcherViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    val users: StateFlow<List<UserEntity>> = userRepository.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectUser(userId: Int, onSelected: () -> Unit) {
        viewModelScope.launch {
            sessionManager.setCurrentUser(userId)
            onSelected()
        }
    }

    fun createProfile(name: String, onCreated: () -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val color = AVATAR_COLORS.random()
            val newId = userRepository.createUser(name.trim(), color)
            sessionManager.setCurrentUser(newId.toInt())
            onCreated()
        }
    }
}
