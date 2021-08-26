package com.screenmeet.sdkdemo.overlay

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.core.content.getSystemService

typealias ConfigurationChangeListener = (screenConfig : ScreenConfig) -> Unit

class ConfigurationWatcher(private val appContext: Context) {

    private val listeners = mutableSetOf<ConfigurationChangeListener>()

    private val callback = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            screenConfig = loadScreenConfiguration(appContext)
            listeners.forEach { it.invoke(screenConfig) }
        }

        override fun onLowMemory() {}
    }

    var screenConfig = loadScreenConfiguration(appContext)

    init {
        appContext.registerComponentCallbacks(callback)
    }

    fun subscribeChanges(listener: ConfigurationChangeListener){
        listeners.add(listener)
    }

    fun unsubscribeChanges(listener: ConfigurationChangeListener){
        listeners.remove(listener)
    }

    private fun loadScreenConfiguration(appContext: Context): ScreenConfig {
        val displaySize = Point()
        appContext.getSystemService<DisplayManager>()
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?.getRealSize(displaySize)

        return ScreenConfig(
            displaySize.x,
            displaySize.y,
            appContext.resources.configuration.orientation
        )
    }
}