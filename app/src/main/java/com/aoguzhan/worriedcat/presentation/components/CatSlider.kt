package com.aoguzhan.worriedcat.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.aoguzhan.worriedcat.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatSlider(
    modifier: Modifier = Modifier,
    sliderPosition: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Slider(
        modifier = modifier,
        value = sliderPosition, onValueChange = {
            onValueChange(it)
        },
        thumb = {
            Image(
                imageVector = ImageVector.vectorResource(R.drawable.ic_worried_cat),
                contentDescription = null,
                modifier = Modifier.size(42.dp),
            )
        },
        track = {
            SliderTrack(sliderState = it, trackHeight = 12.dp, tickSize = 4.dp)
        },
        onValueChangeFinished = {
            onValueChangeFinished()
        }
    )
}