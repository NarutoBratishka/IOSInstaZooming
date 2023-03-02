package com.ablanco.zoomy

import android.annotation.SuppressLint
import android.graphics.Point
import android.graphics.PointF
import android.util.Log
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.ScaleGestureDetector.OnScaleGestureListener
import android.view.View.OnTouchListener
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.ImageView
import java.util.*
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Created by Álvaro Blanco Cabrero on 12/02/2017.
 * Zoomy.
 */
internal class ZoomableTouchListener(
    private val mTargetContainer: TargetContainer,
    private val mTarget: View,
    private val mConfig: ZoomyConfig,
    interpolator: Interpolator?,
    zoomListener: ZoomListener?,
    tapListener: TapListener?,
    longPressListener: LongPressListener?,
    doubleTapListener: DoubleTapListener?
) : OnTouchListener, OnScaleGestureListener {
    private var fakeScaleFactorStartPointer: Float = 0F
    private var isScalingNow = false
    private val activePointers: MutableList<PointerInfo> = mutableListOf()
    private var mTapListener: TapListener? = null
    private var mLongPressListener: LongPressListener? = null
    private var mDoubleTapListener: DoubleTapListener? = null
    private var mState = STATE_IDLE
    private var mZoomableView: ImageView? = null
    private var mTouchCatcherPanel: View? = null
    private var mShadow: View? = null
    private val mScaleGestureDetector: ScaleGestureDetector
    private val mGestureDetector: GestureDetector
    private val mGestureListener: SimpleOnGestureListener = object : SimpleOnGestureListener() {
        //TODO disable actions on second finger handle
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            mTapListener?.onTap(mTarget)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            mLongPressListener?.onLongPress(mTarget)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            mDoubleTapListener?.onDoubleTap(mTarget)
            return true
        }
    }
    private var mScaleFactor = 1f
    private var mCurrentMovementMidPoint = PointF()
    private var mInitialPinchMidPoint = PointF()
    private var mTargetViewCords = Point()
    private var mAnimatingZoomEnding = false
    private val mEndZoomingInterpolator: Interpolator
    private var mZoomListener: ZoomListener? = null
    private val mEndingZoomAction = Runnable {
        removeFromDecorView(mShadow)
        removeFromDecorView(mZoomableView)
        mTarget.visibility = View.VISIBLE
        mZoomableView = null
        mCurrentMovementMidPoint = PointF()
        mInitialPinchMidPoint = PointF()
        mAnimatingZoomEnding = false
        mState = STATE_IDLE
        mZoomListener?.onViewEndedZooming(mTarget)
        if (mConfig.isImmersiveModeEnabled) showSystemUI()
    }

    private val checkAndRemoveTargetPoints = mutableListOf<Int>()
    private val checkAndRemoveCatcherPoints = mutableListOf<Int>()

    init {
        mEndZoomingInterpolator = interpolator ?: AccelerateDecelerateInterpolator()
        mScaleGestureDetector = ScaleGestureDetector(mTarget.context, this)
        mGestureDetector = GestureDetector(mTarget.context, mGestureListener)
        mZoomListener = zoomListener
        mTapListener = tapListener
        mLongPressListener = longPressListener
        mDoubleTapListener = doubleTapListener
    }

    override fun onTouch(v: View, ev: MotionEvent): Boolean {
        if (mAnimatingZoomEnding || ev.pointerCount > 2) return true
        mScaleGestureDetector.onTouchEvent(ev)
        mGestureDetector.onTouchEvent(ev)
        val action = ev.action and MotionEvent.ACTION_MASK

        //все перехватываемые поинтеры в этой части будут Source.TARGET_VIEW
        LAST_POINTER_COUNT = CURRENT_POINTER_COUNT
        collectTargetViewPointers(ev)
        CURRENT_POINTER_COUNT = activePointers.count()

        Log.e("Target", "LAST: $LAST_POINTER_COUNT || CURR: $CURRENT_POINTER_COUNT, srcs: ${activePointers.joinToString(", ") { "[${it.pointerId} ${it.source}]" }}")

        val newPointerZoom = LAST_POINTER_COUNT == 1 && CURRENT_POINTER_COUNT == 2
        when (action) {
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_DOWN ->
                when (mState) {
                    STATE_IDLE -> mState = STATE_POINTER_DOWN
                    STATE_POINTER_DOWN -> {
                        mState = STATE_ZOOMING
                        MotionUtils.midPointOfEvent(
                            mInitialPinchMidPoint,
                            activePointers,
                            newPointerZoom
                        )
                        startZoomingView(mTarget)
                    }
                }

            MotionEvent.ACTION_MOVE ->
                onFingerMove(newPointerZoom)

            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL ->
                onFingerUp(newPointerZoom)
        }
        return true
    }

    private fun collectTargetViewPointers(event: MotionEvent) = synchronized(this) {
        val pointerIds = executeListOfPointersIds(event)

        if (checkAndRemoveTargetPoints.isNotEmpty()) {
            val pointersToRemove = checkAndRemoveTargetPoints.subtract(pointerIds.toSet())
            activePointers.removeAll {
                it.source == PointerInfo.Source.TARGET_VIEW && it.pointerId in pointersToRemove
            }
            checkAndRemoveTargetPoints.clear()
        }

        for (i in 0..1) {
            val priority =
                if (i == 0) PointerInfo.Priority.PRIMARY
                else PointerInfo.Priority.DEPENDENT

            kotlin.runCatching {
                //Создаем pointer
                PointerInfo(
                    pointerIds[i],
                    event.getX(i),
                    event.getY(i),
                    priority,
                    PointerInfo.Source.TARGET_VIEW
                )
            }.getOrNull()?.let { pointer ->
                //Если такой уже есть в списке активных
                if (activePointers.find { it.pointerId == pointer.pointerId } != null)
                    //Перезаписываем
                    activePointers[i] = pointer
                else
                    //Добавляем
                    activePointers.add(i, pointer)
            }
            //Стираем pointers которые больше не существуют
            val actionMasked = event.action and MotionEvent.ACTION_MASK
            if (actionMasked in arrayOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL))
                activePointers.removeAll {
                    it.source == PointerInfo.Source.TARGET_VIEW
                }
            else if (actionMasked == MotionEvent.ACTION_POINTER_UP)
                checkAndRemoveTargetPoints.addAll(pointerIds)
        }
    }

    private fun collectTouchCatcherViewPointers(event: MotionEvent) = synchronized(this) {
        val pointerIds = executeListOfPointersIds(event)

        if (checkAndRemoveCatcherPoints.isNotEmpty()) {
            val pointersToRemove = checkAndRemoveCatcherPoints.subtract(pointerIds.toSet())
            activePointers.removeAll {
                it.source == PointerInfo.Source.TOUCH_CATCHER && it.pointerId in pointersToRemove
            }
            checkAndRemoveCatcherPoints.clear()
        }

        val activeTargetPts = activePointers.count { it.source == PointerInfo.Source.TARGET_VIEW }
        for (i in 0 until 2 - activeTargetPts) {
            val priority =
                if (i == 0 && activeTargetPts == 0) PointerInfo.Priority.PRIMARY
                else PointerInfo.Priority.DEPENDENT

            kotlin.runCatching {
                //Создаем pointer
                PointerInfo(
                    pointerIds[i],
                    event.getX(i),
                    event.getY(i),
                    priority,
                    PointerInfo.Source.TOUCH_CATCHER
                )
            }.getOrNull()?.let { pointer ->
                //Если такой уже есть в списке активных
                if (activePointers.find { it.pointerId == pointer.pointerId } != null)
                    //Перезаписываем
                    activePointers[i + activeTargetPts] = pointer
                else
                    //Добавляем
                    activePointers.add(i + activeTargetPts, pointer)
                //В случае ошибки создания pointer - берем pointer по индексу
            }
            //Стираем pointers которые больше не существуют
            val actionMasked = event.action and MotionEvent.ACTION_MASK
            if (actionMasked in arrayOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL))
                activePointers.removeAll {
                    it.source == PointerInfo.Source.TOUCH_CATCHER
                }
            else if (actionMasked == MotionEvent.ACTION_POINTER_UP)
                checkAndRemoveCatcherPoints.addAll(pointerIds)
        }
    }

    private fun isFingerUp(actionMasked: Int): Boolean {
        return actionMasked in arrayOf(
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL
        )
    }

    private fun executeListOfPointersIds(event: MotionEvent): List<Int> {
        val pointersIds = mutableListOf<Int>()
        for (n in 0 until event.pointerCount) {
            pointersIds.add(event.getPointerId(n))
        }
        return pointersIds
    }


    private fun endZoomingView() {
        LAST_SCALE = 1F
        isScalingNow = false
        mScaleFactor = 1f
        removeTouchCatcherPanel()
        if (mConfig.isZoomAnimationEnabled) {
            mAnimatingZoomEnding = true
            mZoomableView!!.animate()
                .x(mTargetViewCords.x.toFloat())
                .y(mTargetViewCords.y.toFloat())
                .scaleX(1f)
                .scaleY(1f)
                .setInterpolator(mEndZoomingInterpolator)
                .withEndAction(mEndingZoomAction).start()
        } else mEndingZoomAction.run()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun startZoomingView(view: View) {
        isScalingNow = true
        mZoomableView = ImageView(mTarget.context)
        mZoomableView!!.layoutParams = ViewGroup.LayoutParams(mTarget.width, mTarget.height)
        mZoomableView!!.setImageBitmap(ViewUtils.getBitmapFromView(view))
        addTouchCatcherPanel()

        //show the view in the same coords
        mTargetViewCords = ViewUtils.getViewAbsoluteCords(view)
        mZoomableView!!.x = mTargetViewCords.x.toFloat()
        mZoomableView!!.y = mTargetViewCords.y.toFloat()
        if (mShadow == null) mShadow = View(mTarget.context)
        mShadow!!.setBackgroundResource(0)
        addToDecorView(mShadow!!)
        addToDecorView(mZoomableView!!)

        //trick for simulating the view is getting out of his parent
        disableParentTouch(mTarget.parent)
        mTarget.visibility = View.INVISIBLE
        if (mConfig.isImmersiveModeEnabled) hideSystemUI()
        mZoomListener?.onViewStartedZooming(mTarget)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addTouchCatcherPanel() {
        mTouchCatcherPanel = View(mTarget.context)
        mTouchCatcherPanel!!.layoutParams = ViewGroup.LayoutParams(
            getParentRecursively(mTarget).width,
            getParentRecursively(mTarget).height
        )
        mTouchCatcherPanel!!.setOnTouchListener { catcher: View, event: MotionEvent ->
            /*VISUALISING*/
//            val rnd = Random()
//            val color = Color.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
//            catcher.setBackgroundColor(color)

            /** new try */
            LAST_POINTER_COUNT = CURRENT_POINTER_COUNT
            collectTouchCatcherViewPointers(event)
            CURRENT_POINTER_COUNT = activePointers.count()

            val newPointerZoom = LAST_POINTER_COUNT == 1 && CURRENT_POINTER_COUNT == 2
            val actionMasked = event.actionMasked and MotionEvent.ACTION_MASK

            if (isFingerUp(actionMasked)) {
                onFingerUp(newPointerZoom)
            }

            MotionUtils.midPointOfEvent(
                mCurrentMovementMidPoint,
                activePointers,
                newPointerZoom
            )
            Log.e("Catcher", "LAST: $LAST_POINTER_COUNT || CURR: $CURRENT_POINTER_COUNT, srcs: ${activePointers.joinToString(", ") { "[${it.pointerId} ${it.source}]" }}")
//            Log.e("Учитывать как новое касание", "$newPointerZoom")

            if (CURRENT_POINTER_COUNT == 2) {
                if (actionMasked == MotionEvent.ACTION_MOVE) {
                    mState = STATE_ZOOMING
                }

                val point1 = activePointers[0]
                val point2 = activePointers[1]

                val hypotenuse = sqrt(
                    (point2.rawX - point1.rawX).pow(2) +
                    (point2.rawY - point1.rawY).pow(2)
                ) / LAST_SCALE

                var fakeScaleFactor: Float = 1F
                when (event.action and MotionEvent.ACTION_MASK) {
                    MotionEvent.ACTION_POINTER_DOWN,
                    MotionEvent.ACTION_DOWN -> {
                        fakeScaleFactorStartPointer = hypotenuse
                        return@setOnTouchListener true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        fakeScaleFactor = hypotenuse / fakeScaleFactorStartPointer
                    }

                }

                LAST_SCALE *= fakeScaleFactor
                onScaleFactorChanged(fakeScaleFactor)
            }

            true
        }
        getParentRecursively(mTarget).addView(mTouchCatcherPanel)
    }

    private fun onScaleFactorChanged(scaleFactor: Float) {
        mScaleFactor *= scaleFactor

        // Don't let the object get too large.
        mScaleFactor = Math.max(MIN_SCALE_FACTOR, Math.min(mScaleFactor, MAX_SCALE_FACTOR))
        mZoomableView!!.scaleX = LAST_SCALE * mScaleFactor
        mZoomableView!!.scaleY = LAST_SCALE * mScaleFactor
        obscureDecorView(mScaleFactor)
    }

    private fun onFingerUp(newPointerZoom: Boolean) {
        if (CURRENT_POINTER_COUNT == 0) {
            when (mState) {
                STATE_ZOOMING -> {
                    endZoomingView()
                }
                STATE_POINTER_DOWN -> mState = STATE_IDLE
            }
        } else {
            LAST_SCALE = mZoomableView!!.scaleX
            onFingerMove(newPointerZoom)
        }
    }

    private fun onFingerMove(newPointerZoom: Boolean) {
        if (mState == STATE_ZOOMING) {
            MotionUtils.midPointOfEvent(
                mCurrentMovementMidPoint,
                activePointers,
                newPointerZoom
            )
            //because our initial pinch could be performed in any of the view edges,
            //we need to substract this difference and add system bars height
            //as an offset to avoid an initial transition jump
            mCurrentMovementMidPoint.x -= mInitialPinchMidPoint.x
            mCurrentMovementMidPoint.y -= mInitialPinchMidPoint.y
            //because previous function returns the midpoint for relative X,Y coords,
            //we need to add absolute view coords in order to ensure the correct position
            mCurrentMovementMidPoint.x += mTargetViewCords.x.toFloat()
            mCurrentMovementMidPoint.y += mTargetViewCords.y.toFloat()
            val x = mCurrentMovementMidPoint.x
            val y = mCurrentMovementMidPoint.y
            mZoomableView!!.x = x
            mZoomableView!!.y = y
        }
    }

    private fun removeTouchCatcherPanel() {
        getParentRecursively(mTarget).removeView(mTouchCatcherPanel)
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        if (mZoomableView == null) return false
        onScaleFactorChanged(detector.scaleFactor)
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        return mZoomableView != null
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
//        mScaleFactor = 1f
    }

    private fun addToDecorView(v: View) {
        mTargetContainer.decorView.addView(v)
    }

    private fun removeFromDecorView(v: View?) {
        mTargetContainer.decorView.removeView(v)
    }

    private fun getParentRecursively(v: View): ViewGroup {
        val presumablyParent = v.parent
        return if (presumablyParent is ViewGroup) getParentRecursively(presumablyParent) else v as ViewGroup
    }

    private fun obscureDecorView(factor: Float) {
//        Log.e(TAG, "scaleFactor: $factor")
        //normalize value between 0 and 1
//        var normalizedValue = (factor - MIN_SCALE_FACTOR) / (MAX_SCALE_FACTOR - MIN_SCALE_FACTOR)
//        normalizedValue = Math.min(0.75f, normalizedValue * 2)
//        val obscure = Color.argb((normalizedValue * 255).toInt(), 0, 0, 0)
//        mShadow!!.setBackgroundColor(obscure)
    }

    private fun hideSystemUI() {
        mTargetContainer.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                or View.SYSTEM_UI_FLAG_FULLSCREEN) // hide status ba;
    }

    private fun showSystemUI() {
        mTargetContainer.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun disableParentTouch(view: ViewParent) {
        view.requestDisallowInterceptTouchEvent(true)
        if (view.parent != null) disableParentTouch(view.parent)
    }

    companion object {
        private const val STATE_IDLE = 0
        private const val STATE_POINTER_DOWN = 1
        private const val STATE_ZOOMING = 2
        @Volatile private var LAST_POINTER_COUNT = 0
        @Volatile private var CURRENT_POINTER_COUNT = 0
        private var LAST_SCALE = 1f
        private const val MIN_SCALE_FACTOR = 0.2f
        private const val MAX_SCALE_FACTOR = 5f
    }
}

internal class PointerInfo(id: Int, x: Float, y: Float, priority: Priority, source: Source) {
    var pointerId = 0
    var rawX = 0f
    var rawY = 0f
    var priority: Priority
    var source: Source

    init {
        pointerId = id
        rawX = x
        rawY = y
        this.priority = priority
        this.source = source
//        Log.e(TAG, "rawX: $rawX, rawY: $rawY")
    }

    internal enum class Priority {
        PRIMARY, DEPENDENT
    }

    internal enum class Source {
        TARGET_VIEW, TOUCH_CATCHER
    }
}