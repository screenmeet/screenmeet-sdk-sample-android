package com.screenmeet.sdkdemo.overlay

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.setPadding
import com.screenmeet.sdk.ScreenMeet.Companion.eglContext
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import kotlin.math.abs

class VideoOverlay(context: Context): BaseOverlay(context){

    private val widgetCornerMargin = convertDpToPixel(20, context)
    private val widgetInnerPadding = convertDpToPixel(5, context)
    private val widgetMaxSize = convertDpToPixel(200, context)

    private var inBounded = true
    private var initialXCoordinate = 0
    private var startX = 0
    private var initialYCoordinate = 0
    private var startY = 0

    private var renderer: SurfaceViewRenderer? = null
    private var videoTrack: VideoTrack? = null

    override fun buildOverlay(context: Context): View {
        overlayWidth = WindowManager.LayoutParams.WRAP_CONTENT
        overlayHeight = WindowManager.LayoutParams.WRAP_CONTENT

        renderer = SurfaceViewRenderer(context).apply {
            isClickable = false
            isFocusable = false
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            setZOrderMediaOverlay(false)

            init(eglContext, object : RendererCommon.RendererEvents {

                override fun onFirstFrameRendered() {}

                override fun onFrameResolutionChanged(width: Int, height: Int, p2: Int) {
                    val overlay = this@VideoOverlay.overlay
                    val layoutParams = overlay?.layoutParams
                    if (layoutParams != null) {
                        var widgetWidth = width
                        var widgetHeight = height
                        while (widgetWidth > widgetMaxSize || widgetHeight > widgetMaxSize){
                            widgetWidth = (widgetWidth / 1.3).toInt()
                            widgetHeight = (widgetHeight / 1.3).toInt()
                        }
                        layoutParams.width = widgetWidth
                        layoutParams.height = widgetHeight
                        Handler(Looper.getMainLooper()).post {
                            windowManager.updateViewLayout(overlay, layoutParams)
                        }
                    }
                }
            })
        }

        return FrameLayout(context).apply {
            setPadding(widgetInnerPadding)
            setOnTouchListener { v, event ->
                v.performClick()
                processTouch(event)
                return@setOnTouchListener true
            }
            addView(renderer)
        }
    }

    fun attachVideoTrack(track: VideoTrack){
        if (videoTrack != null){
            videoTrack?.removeSink(renderer)
        }

        renderer?.let {
            overlay?.let {  view ->
                val layoutParams = view.layoutParams
                if(layoutParams != null){
                    layoutParams.width = widgetMaxSize
                    layoutParams.height = widgetMaxSize
                    windowManager.updateViewLayout(overlay, layoutParams)
                }
            }

            videoTrack = track
            videoTrack?.addSink(it)
        }
    }

    override fun hideOverlay() {
        super.hideOverlay()
        videoTrack?.removeSink(renderer)
    }

    private fun processTouch(event: MotionEvent) {
        val x = event.rawX.toInt()
        val y = event.rawY.toInt()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> touchDownEvent(x, y)
            MotionEvent.ACTION_MOVE -> touchMoveEvent(x, y)
            MotionEvent.ACTION_UP ->   touchUpEvent(x, y)
        }
    }

    private fun touchDownEvent(x: Int, y: Int) {
        startX = x
        startY = y
        initialXCoordinate = x
        initialYCoordinate = y
    }

    private fun touchMoveEvent(x: Int, y: Int) {
        overlay?.let {
            inBounded = false

            val yDiffMove = y - initialYCoordinate
            val xDiffMove = x - initialXCoordinate
            initialYCoordinate = y
            initialXCoordinate = x
            applyDiff(it, xDiffMove, yDiffMove)
        }
    }

    private fun touchUpEvent(x: Int, y: Int): Boolean {
        val vicinity = 48
        overlay?.let {
            if (inBounded) {
                inBounded = false
                return false
            }
            inBounded = false

            val yDiff = y - initialYCoordinate
            val xDiff = x - initialXCoordinate
            applyDiff(it, xDiff, yDiff, true)
        }

        return abs(y - startY) < vicinity || abs(x - startX) < vicinity
    }

    private fun applyDiff(view: View, xDiff: Int, yDiff: Int, stickToCorner: Boolean = false){
        val layoutParamsWidget = view.layoutParams as WindowManager.LayoutParams

        val widgetX = layoutParamsWidget.x
        val widgetY = layoutParamsWidget.y
        val widgetHeight: Int = view.measuredHeight
        val widgetWidth: Int = view.measuredWidth
        val screenHeight = screen.height
        val screenWidth = screen.width

        val widgetMaxY: Int = screenHeight - widgetHeight - statusBarHeight
        val widgetMaxX = screenWidth - widgetWidth

        var widgetNewY = widgetY + yDiff
        var widgetNewX = widgetX + xDiff

        if(stickToCorner){
            widgetNewX = if(widgetNewX > widgetMaxX / 2){
                widgetMaxX - widgetCornerMargin
            } else 0 + widgetCornerMargin

            widgetNewY = if(widgetNewY > widgetMaxY / 2){
                widgetMaxY - widgetCornerMargin
            } else 0 + widgetCornerMargin

        } else {
            if (widgetNewY < 0) widgetNewY = 0 else if (widgetNewY > widgetMaxY) widgetNewY = widgetMaxY
            if (widgetNewX < 0) widgetNewX = 0 else if (widgetNewX > widgetMaxX) widgetNewX = widgetMaxX
        }

        layoutParamsWidget.x = widgetNewX
        layoutParamsWidget.y = widgetNewY
        windowManager.updateViewLayout(view, layoutParamsWidget)
    }
}