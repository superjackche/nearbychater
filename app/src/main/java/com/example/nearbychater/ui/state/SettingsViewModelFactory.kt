package com.example.nearbychater.ui.state

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.nearbychater.data.chat.ChatRepository

class SettingsViewModelFactory(
    private val application: Application,
    private val chatRepository: ChatRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(application, chatRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}