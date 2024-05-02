package com.ownid.demo.gigya.ui.activity

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.GigyaLoginCallback
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.network.GigyaError
import com.ownid.demo.gigya.ui.AppContent
import com.ownid.demo.gigya.ui.Header
import com.ownid.demo.gigya.ui.LoginScreen
import com.ownid.demo.gigya.ui.RegistrationScreen
import com.ownid.demo.gigya.ui.theme.MyApplicationTheme
import com.ownid.sdk.exception.GigyaException
import com.ownid.sdk.exception.OwnIdException
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gigyaLoginCallback = object : GigyaLoginCallback<GigyaAccount>() {
            override fun onSuccess(obj: GigyaAccount?) = onLogin(false)
            override fun onError(error: GigyaError?) {
                Log.e("MainActivity", "GigyaError: $error")
            }
        }

        if (Gigya.getInstance(GigyaAccount::class.java).isLoggedIn) {
            startActivity(UserActivity.getIntent(this@MainActivity, false))
            finish()
        }

        setContent {
            MyApplicationTheme {
                Column {
                    Header()
                    AppContent {
                        Tabs(
                            onPasswordLoginClick = { email, password ->
                                Gigya.getInstance(GigyaAccount::class.java).login(email, password, gigyaLoginCallback)
                            },
                            onPasswordRegistrationClick = { name, email, password ->
                                val params = mutableMapOf<String, Any>()
                                params["profile"] = JSONObject().put("firstName", name).toString()
                                Gigya.getInstance(GigyaAccount::class.java).register(email, password, params, gigyaLoginCallback)
                            },
                            onLogin = { onLogin(true) },
                            onError = { error ->
                                when (error) {
                                    is GigyaException -> Log.e("MainActivity", "GigyaException: ${error.gigyaError.localizedMessage}")
                                    else -> Log.e("MainActivity", "OwnIdException: $error")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun onLogin(isOwnidLogin: Boolean = true) {
        startActivity(UserActivity.getIntent(this@MainActivity, isOwnidLogin))
        finish()
    }
}

@Composable
fun BoxScope.Tabs(
    onPasswordLoginClick: (String, String) -> Unit,
    onPasswordRegistrationClick: (String, String, String) -> Unit,
    onLogin: () -> Unit,
    onError: (OwnIdException) -> Unit
) {
    val titles = listOf("Log in", "Create Account")
    var selectedTabIndex by remember { mutableIntStateOf(0) }

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
                0 -> LoginScreen(onPasswordLoginClick = onPasswordLoginClick, onLogin = onLogin, onError = onError)
                1 -> RegistrationScreen(onPasswordRegistrationClick = onPasswordRegistrationClick, onLogin = onLogin, onError = onError)
            }
        }
    }
}