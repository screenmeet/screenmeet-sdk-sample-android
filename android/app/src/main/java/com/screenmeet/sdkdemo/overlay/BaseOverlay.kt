package com.screenmeet.sdkdemo.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.annotation.UiThread
import kotlin.math.roundToInt

abstract class BaseOverlay(val context: Context) {

    protected lateinit var screen: ScreenConfig

    protected val statusBarHeight by lazy { statusBarHeight(context) }

    protected var overlayHeight: Int = WindowManager.LayoutParams.WRAP_CONTENT
    protected var overlayWidth: Int = WindowManager.LayoutParams.WRAP_CONTENT
    protected var overlay: View? = null

    protected val windowManager by lazy { windowManager() }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun showOverlay(screenConfig: ScreenConfig): Boolean {
        screen = screenConfig

        removeIfAdded()
        val view = buildOverlay(context)
        applyOverlay(false, view, buildLayoutParams(overlayWidth, overlayHeight))
        overlay = view

        return true
    }

    open fun hideOverlay() {
        removeIfAdded()
    }

    protected abstract fun buildOverlay(context: Context): View

    fun updateScreenConfig(screenConfig: ScreenConfig){
        screen = screenConfig
    }

    protected fun moveOverlay(x: Int, y: Int) {
        overlay?.let {
            val layoutParams = it.layoutParams as WindowManager.LayoutParams

            //Checking if pointer coordinates are in screen bounds
            val pointerHeight = layoutParams.height
            val pointerWidth = layoutParams.width

            var xNew = x - pointerWidth / 2
            var yNew = y - pointerHeight / 2 - statusBarHeight

            xNew = if (xNew + pointerWidth > screen.width) {
                screen.width - pointerWidth
            } else xNew.coerceAtLeast(0)
            yNew = if (yNew + pointerHeight * 2 > screen.height) {
                screen.height - pointerHeight * 2
            } else yNew.coerceAtLeast(0)

            layoutParams.x = xNew
            layoutParams.y = yNew

            applyOverlay(true, it, layoutParams)
        }
    }

    protected fun convertDpToPixel(dp: Int, context: Context): Int {
        val resources = context.resources
        val metrics = resources.displayMetrics
        return (metrics.density * dp).roundToInt()
    }

    @UiThread
    private fun applyOverlay(attached: Boolean, view: View, layoutParams: WindowManager.LayoutParams){
        mainHandler.post {
            if(attached) windowManager.updateViewLayout(view, layoutParams)
            else windowManager.addView(view, layoutParams)
        }
    }

    private fun buildLayoutParams(viewWidth: Int, viewHeight: Int): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            viewWidth, viewHeight,
            getOverlayType(),
            getOverlayFlagsTouch(),
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.START or Gravity.TOP
        return params
    }

    private fun getOverlayFlagsNotTouch() = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private fun getOverlayFlagsTouch() = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private fun getOverlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    @UiThread
    private fun removeIfAdded(){
        mainHandler.post {
            if (isViewAttached(overlay)) {
                windowManager.removeView(overlay)
                overlay = null
            }
        }
    }

    private fun isViewAttached(view: View?): Boolean {
        return view != null && view.parent != null
    }

    private fun windowManager(): WindowManager {
        return context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private fun statusBarHeight(context: Context): Int {
        var result = 0
        val resourceId: Int = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) result = context.resources.getDimensionPixelSize(resourceId)
        return result
    }
}