package com.ownid.demo.gigya.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ownid.demo.gigya.R
import com.ownid.demo.gigya.ui.theme.headerColor

@Composable
fun Header() {
    Surface(
        modifier = Modifier
            .height(148.dp)
            .fillMaxWidth(), color = MaterialTheme.colorScheme.headerColor
    ) {
        Image(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_ownid_logo_full),
            contentDescription = "OwnID Logo",
            modifier = Modifier.requiredSize(164.dp, 56.dp)
        )
    }
}

@Composable
fun AppContent(
    color: Color = MaterialTheme.colorScheme.background,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = color,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                content()
            }

            Text(
                text = "This app is only for demoing purposes to showcase\nhow the OwnID widget functions.",
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                lineHeight = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}