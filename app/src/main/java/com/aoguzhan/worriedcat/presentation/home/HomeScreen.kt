package com.aoguzhan.worriedcat.presentation.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.aoguzhan.compose.ScreenCaptureCatTheme
import com.aoguzhan.worriedcat.R
import com.aoguzhan.worriedcat.data.datastore.PreferenceKeys.FAMILIARITY_VALUE
import com.aoguzhan.worriedcat.data.datastore.PreferenceKeys.SELECTED_LABELS
import com.aoguzhan.worriedcat.data.datastore.dataStore
import com.aoguzhan.worriedcat.presentation.components.CatSlider
import com.aoguzhan.worriedcat.presentation.home.components.LabelSelectDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.tensorflow.lite.support.common.FileUtil

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    recording: Boolean,
    onRecordClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allLabels = remember {
        mutableStateListOf<String>()
    }

    val selectedLabels = remember {
        mutableStateListOf<String>()
    }
    var sliderPosition by remember { mutableStateOf(0f) }

    var isOpenOverlayDialog by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(selectedLabels.size) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_LABELS] = selectedLabels.joinToString(",")
        }
    }

    LaunchedEffect(Unit) {
        allLabels.addAll(FileUtil.loadLabels(context, "labels.txt"))
        val savedLabels = if(context.dataStore.data.first()[SELECTED_LABELS].isNullOrEmpty()){
            emptyList()
        }else context.dataStore.data.first()[SELECTED_LABELS]?.split(",") ?: emptyList()
        sliderPosition = context.dataStore.data.first()[FAMILIARITY_VALUE] ?: 0.2f
        selectedLabels.addAll(savedLabels)
    }


    var isOpenSelectLabelDialog by remember {
        mutableStateOf(false)
    }

    Column {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {

            Crossfade(
                targetState = recording,
                animationSpec = tween(durationMillis = 500),
                label = "fade cat"
            ) {
                Image(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .aspectRatio(1f),
                    imageVector = if (it) ImageVector.vectorResource(R.drawable.ic_worried_cat) else ImageVector.vectorResource(
                        R.drawable.ic_smiling_cat_with_heart_eyes
                    ), contentDescription = ""
                )
            }

        }
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            text = stringResource(id = R.string.description_select_content_for_blocking)
        )


        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            OutlinedButton(
                onClick = { isOpenSelectLabelDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(text = stringResource(id = R.string.label_select_block_contents))
                }

            }

        }

        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            text = "${stringResource(id = R.string.label_familiarity_score)}: ${
                "%.2f".format(
                    sliderPosition
                )
            }"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {

            CatSlider(
                modifier = Modifier.fillMaxWidth(0.85f),
                sliderPosition = sliderPosition,
                onValueChange = {
                    sliderPosition = it
                },
                onValueChangeFinished = {
                    scope.launch {
                        context.dataStore.edit { preferences ->
                            preferences[FAMILIARITY_VALUE] = sliderPosition
                        }
                    }
                }
            )
        }


        FlowRow(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            selectedLabels.forEach {
                FilterChip(
                    selected = true, onClick = {
                        selectedLabels.remove(it)
                    }, label = {
                        Text(text = it)
                    }, leadingIcon = {
                        Icon(
                            modifier = Modifier.padding(4.dp),
                            imageVector = Icons.Default.Close, contentDescription = ""
                        )
                    })
            }
        }

        Spacer(modifier = Modifier.weight(1f))


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Center
        ) {

            Button(onClick = {
                if (!Settings.canDrawOverlays(context)) {
                    isOpenOverlayDialog = true
                    return@Button
                }
                onRecordClick()
            }) {
                Text(
                    text = if (recording) stringResource(id = R.string.label_stop_blocking) else stringResource(
                        id = R.string.label_start_blocking
                    )
                )
            }
        }

        LabelSelectDialog(
            isOpen = isOpenSelectLabelDialog,
            labels = allLabels,
            onDismiss = { isOpenSelectLabelDialog = false },
            onLabelSelected = { label ->
                if (label in selectedLabels) {
                    selectedLabels.remove(label)
                } else {
                    selectedLabels.add(label)
                }
                isOpenSelectLabelDialog = false
            })

        if(isOpenOverlayDialog){
            AlertDialog(
                onDismissRequest = { isOpenOverlayDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package: ${context.packageName}")
                        )
                        context.startActivity(intent)
                        isOpenOverlayDialog = false
                    }) {
                        Text(
                            text = stringResource(id = R.string.label_go_to_settings))
                    }
                },
                text = {
                    Text(text = stringResource(id = R.string.description_overlay_permission))
                },
                dismissButton = {
                    TextButton(onClick = { isOpenOverlayDialog = false }) {
                        Text(text = stringResource(id = R.string.label_cancel))
                    }
                },

                )
        }


    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    ScreenCaptureCatTheme {
        Surface {
            HomeScreen(recording = false) {

            }
        }
    }
}