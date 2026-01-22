package com.logseq.kmp.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Composable for rendering a single block.
 * Handles indentation and text editing.
 */
@Composable
fun BlockView(
    model: BlockUiModel,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = (model.level * 20).dp, top = 4.dp, bottom = 4.dp)
    ) {
        // Bullet point
        Text(
            text = "•",
            modifier = Modifier.padding(end = 8.dp),
            style = TextStyle(fontSize = 18.sp)
        )

        // Block Content
        // Using BasicTextField or TextField. For MVP, TextField is easier but heavier.
        // We'll strip styling for a cleaner look.
        TextField(
            value = model.content,
            onValueChange = onContentChange,
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(fontSize = 16.sp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
    }
}
