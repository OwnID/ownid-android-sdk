package com.ownid.demo.gigya.screen.home

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.account.models.GigyaAccount

class HomeViewModel : ViewModel() {
    private val gigya: Gigya<GigyaAccount> = Gigya.getInstance(GigyaAccount::class.java)

    @MainThread
    fun doLogout() {
        gigya.logout()
    }
}