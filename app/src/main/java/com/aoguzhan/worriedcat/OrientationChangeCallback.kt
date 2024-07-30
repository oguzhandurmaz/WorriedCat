package com.aoguzhan.worriedcat

import android.content.Context
import android.view.OrientationEventListener

class OrientationChangeCallback(
    context: Context,
    private val onOrientationChanged: () -> Unit
) : OrientationEventListener(context) {

    private var orientation: Int = ORIENTATION_UNKNOWN

    override fun onOrientationChanged(orientation: Int) {
        if (this.orientation != orientation) {
            this.orientation = orientation
            onOrientationChanged()
        }
    }
}