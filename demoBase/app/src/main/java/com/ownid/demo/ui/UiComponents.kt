package com.ownid.demo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun LogSection(
    logEntries: List<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(
                text = logEntries.takeIf { it.isNotEmpty() }?.joinToString(separator = "\n") ?: "No logs yet.",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
    }
}
