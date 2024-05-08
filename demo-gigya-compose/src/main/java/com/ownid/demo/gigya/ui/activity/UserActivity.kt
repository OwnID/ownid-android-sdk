package com.ownid.demo.gigya.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.GigyaCallback
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.network.GigyaError
import com.ownid.demo.gigya.ui.AppContent
import com.ownid.demo.gigya.ui.Header
import com.ownid.demo.gigya.ui.theme.MyApplicationTheme
import com.ownid.demo.gigya.ui.theme.textBackgroundColor
import com.ownid.sdk.OwnIdGigya
import com.ownid.sdk.compose.ownIdViewModel
import com.ownid.sdk.defaultAuthTokenProvider
import com.ownid.sdk.defaultLoginIdProvider
import com.ownid.sdk.viewmodel.OwnIdEnrollmentViewModel

class UserActivity : ComponentActivity() {

    internal companion object {
        private const val IS_OWNID_LOGIN = "IS_OWNID_LOGIN"

        internal fun getIntent(context: Context, isOwnidLogin: Boolean): Intent =
            Intent(context, UserActivity::class.java)
                .putExtra(IS_OWNID_LOGIN, isOwnidLogin)
    }

    private val gigya by lazy(LazyThreadSafetyMode.NONE) { Gigya.getInstance(GigyaAccount::class.java) }

    private val user = MutableLiveData<Pair<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyApplicationTheme {
                Column {
                    Header()
                    AppContent(color = MaterialTheme.colorScheme.background) {
                        User(
                            userLive = user,
                            isOwnidLogin = intent.getBooleanExtra(IS_OWNID_LOGIN, false),
                            onLogOutClick = ::onLogOut
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        gigya.getAccount(true, object : GigyaCallback<GigyaAccount>() {
            override fun onSuccess(account: GigyaAccount?) {
                if (account == null) {
                    onLogOut()
                } else {
                    user.postValue(Pair(account.profile?.firstName ?: "", account.profile?.email ?: ""))
                }
            }

            override fun onError(error: GigyaError) {
                Log.e("UserActivity", error.localizedMessage)
            }
        })
    }

    private fun onLogOut() {
        gigya.logout()
        startActivity(Intent(this@UserActivity, MainActivity::class.java))
        finish()
    }
}

@Composable
fun BoxScope.User(
    userLive: LiveData<Pair<String, String>>,
    isOwnidLogin: Boolean,
    onLogOutClick: () -> Unit,
) {
    val user by userLive.observeAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .align(BiasAlignment(horizontalBias = 0f, verticalBias = -0.6f))
    ) {
        Text(
            text = "Welcome, ${user?.first ?: ""} !",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(text = "Name", modifier = Modifier.padding(top = 16.dp))
        Text(
            text = user?.first ?: "",
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.textBackgroundColor, RoundedCornerShape(6.dp))
                .padding(8.dp),
        )
        Text(text = "Email", modifier = Modifier.padding(top = 16.dp))
        Text(
            text = user?.second ?: "",
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.textBackgroundColor, RoundedCornerShape(6.dp))
                .padding(8.dp),
        )
        Button(
            onClick = { onLogOutClick.invoke() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(text = "Log Out", fontSize = 16.sp)
        }

        val context = LocalContext.current
        val ownIdEnrollmentViewModel = ownIdViewModel<OwnIdEnrollmentViewModel>()
        TextButton(
            onClick = {
                ownIdEnrollmentViewModel.enrollCredential(
                    context = context,
                    loginIdProvider = OwnIdGigya.defaultLoginIdProvider(),
                    authTokenProvider = OwnIdGigya.defaultAuthTokenProvider(),
                    true
                )
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Trigger credential enrollment")
        }

        LaunchedEffect(key1 = isOwnidLogin) {
            if (isOwnidLogin.not()) {
                ownIdEnrollmentViewModel.enrollCredential(
                    context = context,
                    loginIdProvider = OwnIdGigya.defaultLoginIdProvider(),
                    authTokenProvider = OwnIdGigya.defaultAuthTokenProvider()
                )
            }
        }
    }
}