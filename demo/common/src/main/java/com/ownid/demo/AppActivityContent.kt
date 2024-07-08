package com.ownid.demo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Immutable
interface AppActivityContentScope {
    fun showError(message: String)
}

@Composable
fun AppActivityContent(
    content: @Composable AppActivityContentScope.() -> Unit
) {
    AppTheme {
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        val showErrorObject = remember(scope, snackbarHostState) {
            object : AppActivityContentScope {
                override fun showError(message: String) {
                    scope.launch { snackbarHostState.showSnackbar(message, actionLabel = "OK", duration = SnackbarDuration.Long) }
                }
            }
        }
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) {
                    Snackbar(
                        snackbarData = it,
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        actionColor = MaterialTheme.colorScheme.onError
                    )
                }
            }
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Header()

                content.invoke(showErrorObject)

                Spacer(modifier = Modifier.weight(1F))

                Text(
                    text = "This app is only for demoing purposes to showcase\nhow the OwnID widget functions.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun Header() {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.headerColor)
            .height(148.dp)
            .fillMaxWidth(),
    ) {
        Image(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_ownid_logo_full),
            contentDescription = "OwnID Logo",
            modifier = Modifier
                .padding(bottom = 16.dp)
                .align(Alignment.Center)
                .requiredSize(164.dp, 56.dp)
        )
    }
}

val TERMS_POLICY = buildAnnotatedString {
    append("By creating an account you agree to our\n")
    withLink(
        LinkAnnotation.Url("https://ownid.com/legal/terms-of-service", TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline)))
    ) {
        append("Terms of use")
    }
    append(" & ")
    withLink(
        LinkAnnotation.Url("https://ownid.com/legal/privacy-policy", TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline)))
    ) {
        append("Privacy policy")
    }
}