package com.ownid.demo.gigya.ui.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ownid.demo.gigya.ui.AppContent
import com.ownid.demo.gigya.ui.Header
import com.ownid.demo.gigya.ui.LoginScreen
import com.ownid.demo.gigya.ui.RegistrationScreen
import com.ownid.demo.gigya.ui.theme.MyApplicationTheme
import com.ownid.sdk.OwnId
import com.ownid.sdk.RegistrationParameters
import com.ownid.sdk.event.OwnIdLoginEvent
import com.ownid.sdk.event.OwnIdRegisterEvent
import com.ownid.sdk.exception.GigyaException
import com.ownid.sdk.gigya
import com.ownid.sdk.ownIdViewModel
import com.ownid.sdk.viewmodel.OwnIdLoginViewModel
import com.ownid.sdk.viewmodel.OwnIdRegisterViewModel

class MainActivity : ComponentActivity() {
    private val ownIdLoginViewModel: OwnIdLoginViewModel by ownIdViewModel(OwnId.gigya)
    private val ownIdRegisterViewModel: OwnIdRegisterViewModel by ownIdViewModel(OwnId.gigya)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ownIdLoginViewModel.events.observe(this) { ownIdEvent ->
            when (ownIdEvent) {
                is OwnIdLoginEvent.Busy -> Log.e("OwnIdLoginViewModel", "Busy: ${ownIdEvent.isBusy}")
                is OwnIdLoginEvent.LoggedIn -> startActivity(Intent(this, UserActivity::class.java))
                is OwnIdLoginEvent.Error ->
                    when (val cause = ownIdEvent.cause) {
                        is GigyaException -> Log.e("OwnIdLoginViewModel", "Error: ${cause.gigyaError.localizedMessage}")
                        else -> Log.e("OwnIdLoginViewModel", "Error: $cause")
                    }
            }
        }

        ownIdRegisterViewModel.events.observe(this) { ownIdEvent ->
            when (ownIdEvent) {
                is OwnIdRegisterEvent.Busy -> Log.e("OwnIdRegisterViewModel", "Busy: ${ownIdEvent.isBusy}")
                is OwnIdRegisterEvent.ReadyToRegister -> Unit
                OwnIdRegisterEvent.Undo -> Unit
                is OwnIdRegisterEvent.LoggedIn -> startActivity(Intent(this, UserActivity::class.java))
                is OwnIdRegisterEvent.Error ->
                    when (val cause = ownIdEvent.cause) {
                        is GigyaException -> Log.e("OwnIdLoginViewModel", "Error: ${cause.gigyaError.localizedMessage}")
                        else -> Log.e("OwnIdLoginViewModel", "Error: $cause")
                    }
            }
        }

        setContent {
            MyApplicationTheme {
                Column {
                    Header()
                    AppContent {
                        Tabs(
                            onLoginClick = { email, password -> /*Login with Gigya*/ },
                            onRegistrationClick = { name, email, password -> /*Register with Gigya*/ },
                            onRegistrationClickWithOwnId = { loginId, params -> ownIdRegisterViewModel.register(loginId, params) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BoxScope.Tabs(
    onLoginClick: (String, String) -> Unit,
    onRegistrationClick: (String, String, String) -> Unit,
    onRegistrationClickWithOwnId: (String, RegistrationParameters?) -> Unit
) {
    val titles = listOf("Log in", "Create Account")
    var selectedTabIndex by remember { mutableStateOf(0) }

    Surface(
        modifier = Modifier
            .padding(8.dp)
            .align(BiasAlignment(horizontalBias = 0f, verticalBias = -0.6f)), shape = ShapeDefaults.Small
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TabRow(selectedTabIndex = selectedTabIndex, modifier = Modifier.wrapContentWidth()) {
                titles.forEachIndexed { index, title ->
                    Tab(text = {
                        Text(
                            title,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }, selected = selectedTabIndex == index, onClick = { selectedTabIndex = index })
                }
            }
            when (selectedTabIndex) {
                0 -> LoginScreen(onLoginClick = onLoginClick)
                1 -> RegistrationScreen(
                    onRegistrationClick = onRegistrationClick,
                    onRegistrationClickWithOwnId = onRegistrationClickWithOwnId
                )
            }
        }
    }
}