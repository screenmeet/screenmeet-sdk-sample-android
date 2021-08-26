package com.screenmeet.sdkdemo.overlay

import android.content.Context
import androidx.activity.ComponentActivity
import org.webrtc.VideoTrack

class WidgetManager(val context: Context) {

    private var videoOverlay: VideoOverlay? = null
    private val configurationWatcher = ConfigurationWatcher(context)

    init {
        configurationWatcher.subscribeChanges {
            updateScreenConfig(it)
        }
    }

    fun showFloatingWidget(activity: ComponentActivity, videoTrack: VideoTrack) {
        if(PermissionProvider.canDrawOverlay(context)){
            doShowFloatingWidget(configurationWatcher.screenConfig, videoTrack)
        } else {
            PermissionProvider.requestOverlay(context, activity.activityResultRegistry){ granted ->
                if (granted){
                    doShowFloatingWidget(configurationWatcher.screenConfig, videoTrack)
                }
            }
        }
    }

    private fun doShowFloatingWidget(screenConfig: ScreenConfig, track: VideoTrack) {
        val overlayAttached: Boolean
        if (videoOverlay == null){
            val overlay = VideoOverlay(context)
            overlayAttached = overlay.showOverlay(screenConfig)
            if(overlayAttached) {
                 videoOverlay = overlay
            }
        } else overlayAttached = true

        if (overlayAttached){
            videoOverlay?.attachVideoTrack(track)
        }
    }

    fun hideFloatingWidget(){
        videoOverlay?.let {
            it.hideOverlay()
            videoOverlay = null
        }
    }

    private fun updateScreenConfig(screenConfig: ScreenConfig) {
        videoOverlay?.updateScreenConfig(screenConfig)
    }
}