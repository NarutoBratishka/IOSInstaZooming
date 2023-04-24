package com.katorabian.zoomy

import android.app.Activity
import android.app.Dialog
import android.app.DialogFragment
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.Interpolator
import com.katorabian.zoomy.ZoomableTouchListener.Companion.MAX_SCALE_FACTOR
import com.katorabian.zoomy.ZoomableTouchListener.Companion.MIN_SCALE_FACTOR

/**
 * Created by Álvaro Blanco Cabrero on 12/02/2017.
 * Zoomy.
 */
object Zoomy {
    internal var TOUCH_LISTENER_DROP_TIME = System.currentTimeMillis()
    private val DEF_ANIM_VIEW_FPS: Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 60
        else 30
    //you could use another solutions for detecting device fps
    //e.g. https://github.com/wasabeef/Takt

    var ANIM_VIEW_FPS: Long = DEF_ANIM_VIEW_FPS
    var ENABLE_LOGGING: Boolean = false

    private var mDefaultConfig = ZoomyConfig()
    @JvmStatic
    fun setDefaultConfig(config: ZoomyConfig) {
        mDefaultConfig = config
    }

    fun unregister(view: View) {
        view.onTouchEvent(
            MotionEvent.obtain(
                0L, System.currentTimeMillis(), MotionEvent.ACTION_CANCEL,
                (view.bottom + view.top)/2F,
                (view.right + view.left)/2F,
                0
            )
        )
        view.setOnTouchListener(null)
    }

    fun dropPrevious() {
        TOUCH_LISTENER_DROP_TIME = System.currentTimeMillis()
    }

    @JvmStatic
    fun setLoginEnabled(setEnabled: Boolean = false) {
        ENABLE_LOGGING = setEnabled
    }

    fun log(
        tag: String? = this::class.java.simpleName,
        message: String,
        priority: Int = Log.DEBUG
    ) {
        if (ENABLE_LOGGING) Log.println(priority, tag, message)
    }

    class Builder {
        private var mDisposed = false
        private var mDimmingIntensity = 0.5F
        private var mTargetAnimated = false //например TextureView, VideoVide, GifImageView
        private var mUpdateFrequency = DEF_ANIM_VIEW_FPS //например TextureView, VideoVide, GifImageView
        private var mConfig: ZoomyConfig? = null
        private var mTargetContainer: TargetContainer?
        private var mTargetView: View? = null
        private var mZoomListener: ZoomListener? = null
        private var mZoomInterpolator: Interpolator? = null
        private var mTapListener: TapListener? = null
        private var mLongPressListener: LongPressListener? = null
        private var mdDoubleTapListener: DoubleTapListener? = null

        constructor(activity: Activity?) {
            mTargetContainer = ActivityContainer(activity)
        }

        constructor(dialog: Dialog?) {
            mTargetContainer = DialogContainer(dialog)
        }

        constructor(dialogFragment: DialogFragment?) {
            mTargetContainer = DialogFragmentContainer(dialogFragment)
        }

        fun target(target: View?): Builder {
            mTargetView = target
            return this
        }

        fun animateZooming(animate: Boolean): Builder {
            checkNotDisposed()
            if (mConfig == null) mConfig = ZoomyConfig()
            mConfig!!.isZoomAnimationEnabled = animate
            return this
        }

        fun enableImmersiveMode(enable: Boolean): Builder {
            checkNotDisposed()
            if (mConfig == null) mConfig = ZoomyConfig()
            mConfig!!.isImmersiveModeEnabled = enable
            return this
        }

        fun interpolator(interpolator: Interpolator?): Builder {
            checkNotDisposed()
            mZoomInterpolator = interpolator
            return this
        }

        fun zoomListener(listener: ZoomListener?): Builder {
            checkNotDisposed()
            mZoomListener = listener
            return this
        }

        fun customScaleLimiters(
            minScale: Float = DEF_MIN_SCALE_FACTOR,
            maxScale: Float = DEF_MAX_SCALE_FACTOR
        ): Builder {
            checkNotDisposed()
            MIN_SCALE_FACTOR = minScale
            MAX_SCALE_FACTOR = maxScale
            return this
        }

        fun bgDimmingIntensity(multiplier: Float): Builder {
            checkNotDisposed()
            if (multiplier !in 0.0 .. 1.0) {
                Log.e(
                    "::bgDimmingIntensity",
                    "expected value should be in 0F ~ 1F range"
                )
            } else {
                mDimmingIntensity = multiplier
            }
            return this
        }

        fun supportAnimatedView(
            isAnimated: Boolean,
            animViewFps: Long = DEF_ANIM_VIEW_FPS
        ): Builder {
            checkNotDisposed()
            mTargetAnimated = isAnimated
            ANIM_VIEW_FPS = animViewFps
            return this
        }

        fun tapListener(listener: TapListener?): Builder {
            checkNotDisposed()
            mTapListener = listener
            return this
        }

        fun longPressListener(listener: LongPressListener?): Builder {
            checkNotDisposed()
            mLongPressListener = listener
            return this
        }

        fun doubleTapListener(listener: DoubleTapListener?): Builder {
            checkNotDisposed()
            mdDoubleTapListener = listener
            return this
        }

        fun register() {
            checkNotDisposed()
            if (mConfig == null) mConfig = mDefaultConfig
            requireNotNull(mTargetContainer) { "Target container must not be null" }
            requireNotNull(mTargetView) { "Target view must not be null" }
            mTargetView!!.setOnTouchListener(
                ZoomableTouchListener(
                    mTargetContainer!!, mTargetView!!, mConfig!!,
                    mZoomInterpolator, mZoomListener, mTapListener,
                    mLongPressListener, mdDoubleTapListener, mTargetAnimated,
                    mDimmingIntensity
                )
            )

            mTargetView!!.addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {}
                    override fun onViewDetachedFromWindow(v: View) {
                        unregister(v)
                    }
                }
            )

            mDisposed = true
        }

        private fun checkNotDisposed() {
            check(!mDisposed) { "Builder already disposed" }
        }
    }
}