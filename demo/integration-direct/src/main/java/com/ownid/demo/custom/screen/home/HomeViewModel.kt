package com.ownid.demo.custom.screen.home

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ownid.demo.custom.DemoApp
import com.ownid.demo.custom.IdentityPlatform

class HomeViewModel(private val identityPlatform: IdentityPlatform) : ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DemoApp).identityPlatform) }
        }
    }

    @MainThread
    fun authToken(): String? = identityPlatform.currentUser?.authToken

    @MainThread
    fun doLogout() {
        identityPlatform.logout()
    }
}