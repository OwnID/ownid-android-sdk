package com.ownid.demo.boost

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BoostFlowScreen(
    onPasswordLogin: suspend (email: String, password: String) -> Result<Unit>,
    onRegister: suspend (name: String, email: String, password: String, ownIdData: String?) -> Result<Unit>,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by retain { mutableIntStateOf(0) }
    val saveableStateHolder = rememberSaveableStateHolder()

    Column(modifier = modifier) {
        PrimaryTabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("Login") }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("Register") }
            )
        }

        when (selectedTabIndex) {
            0 -> saveableStateHolder.SaveableStateProvider(key = "login_tab") {
                BoostLoginTab(onPasswordLogin = onPasswordLogin)
            }

            1 -> saveableStateHolder.SaveableStateProvider(key = "register_tab") {
                BoostCreatePasskeyTab(onRegister = onRegister)
            }
        }
    }
}
