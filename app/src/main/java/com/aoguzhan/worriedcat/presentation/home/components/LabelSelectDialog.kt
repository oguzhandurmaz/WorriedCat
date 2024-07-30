package com.aoguzhan.worriedcat.presentation.home.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aoguzhan.worriedcat.R

@Composable
fun LabelSelectDialog(
    isOpen: Boolean,
    labels: List<String>,
    onDismiss: () -> Unit,
    onLabelSelected: (String) -> Unit
) {
    if(isOpen){
        Dialog(
            onDismissRequest = { onDismiss() },
        ) {
            Card(
                modifier = Modifier.heightIn(max = 400.dp),
            ) {
                Text(
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    text = stringResource(id = R.string.title_contents)
                )
                LazyColumn {
                    items(labels) { label ->
                        DropdownMenuItem(text = {
                            Text(text = label)
                        }, onClick = {
                            onLabelSelected(label)
                        })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}