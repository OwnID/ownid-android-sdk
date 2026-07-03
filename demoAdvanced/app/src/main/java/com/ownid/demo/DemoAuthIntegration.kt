package com.ownid.demo

import androidx.compose.runtime.Immutable
import com.ownid.sdk.model.AccessToken
import kotlinx.coroutines.flow.Flow

interface DemoAuthIntegration {
    @Immutable
    data class CurrentUser(val email: String, val name: String?, val accessToken: AccessToken?)

    val isSignedIn: Flow<Boolean>

    suspend fun signIn(email: String, password: String): Result<Unit>

    suspend fun registerAndSignIn(name: String, email: String, password: String, ownIdData: String?): Result<Unit>

    suspend fun createUser(name: String, email: String, password: String, ownIdData: String?): Result<Unit>

    suspend fun loadCurrentUser(): Result<CurrentUser>

    fun signOut()
}

object DemoAuthIntegrationProvider {
    lateinit var integration: DemoAuthIntegration
}
