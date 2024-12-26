package com.example.ar

import android.app.Activity
import android.content.Context
import android.view.Display
import android.view.Surface
import com.google.ar.core.Session

class DisplayRotationHelper(val context: Context) {
    private var viewportChanged = false
    private var viewportWidth = 100
    private var viewportHeight = 100

    fun onSurfaceChanged(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        viewportChanged = true
    }

    fun updateSessionIfNeeded(session: Session) {
        if (viewportChanged) {
            val display: Display = (context as Activity).windowManager.defaultDisplay
            val rotation = display.rotation
            session.setDisplayGeometry(rotation, viewportWidth, viewportHeight)
            viewportChanged = false
        }
    }

    fun getRotation(): Int {
        return (context as Activity).windowManager.defaultDisplay.rotation
    }
}
