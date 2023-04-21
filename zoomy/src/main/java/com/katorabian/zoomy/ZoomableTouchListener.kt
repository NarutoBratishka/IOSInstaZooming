package com.katorabian.zoomy

import android.annotation.SuppressLint
import android.graphics.*
import android.util.Log
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.ScaleGestureDetector.OnScaleGestureListener
import android.view.View.OnTouchListener
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.ImageView
import com.katorabian.zoomy.MotionUtils.actionMasked
import com.katorabian.zoomy.Zoomy.log
import kotlinx.coroutines.*
import java.lang.Runnable
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
    doubleTapListener: DoubleTapListener?,
    isAnimated: Boolean = false,
    private val mDimmingIntensity: Float = 1F
) : OnTouchListener, OnScaleGestureListener {
    private var fakeScaleFactorStartPointer: Float = 0F
    private var isScalingNow = false
    private val activePointers: MutableList<PointerInfo> = mutableListOf()
    private var mTapListener: TapListener? = null
    private var mLongPressListener: LongPressListener? = null
    private var mDoubleTapListener: DoubleTapListener? = null
    private var mIsAnimatedTarget: Boolean = false
    private var mState = STATE_IDLE
    private var mZoomableView: View? = null
    //для перехвата ненужных ивентов
    private var mReserveCatcherPanel: View? = null
    //для перехвата новых ивентов
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
        mTarget.alpha = 1F
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
        mIsAnimatedTarget = isAnimated
    }

    override fun onTouch(v: View, ev: MotionEvent): Boolean {
        if (mAnimatingZoomEnding || ev.pointerCount > 2) return true
        mScaleGestureDetector.onTouchEvent(ev)
        mGestureDetector.onTouchEvent(ev)
        val action = ev.action and MotionEvent.ACTION_MASK

        kotlin.runCatching {
            //все перехватываемые поинтеры в этой части будут Source.TARGET_VIEW
            collectViewPointers(v, ev, PointerInfo.Source.TARGET_VIEW)
        }.getOrElse {//при попытке спровоцировать жест, двумя пальцами по разным вьюхам
            return false
        }

        log(
            "Target",
            "LAST: $LAST_POINTER_COUNT || CURR: $CURRENT_POINTER_COUNT, " +
                    "srcs: ${activePointers.joinToString(", ") { "[${it.pointerId} ${it.source}]" }}"
        )

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
                            ev
                        )
                        startZoomingView(mTarget)
                    }
                }

            MotionEvent.ACTION_MOVE ->
                onFingerMove(ev)

            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL ->
                onFingerUp(ev)
        }
        return true
    }

    private fun collectViewPointers(
        view: View, event: MotionEvent,
        source: PointerInfo.Source
    ) = synchronized(this) {

        //игнор 3го и последующих пальцев
        if (event.actionMasked() in arrayOf(
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_DOWN
        ) && activePointers.count() >= 2) return@synchronized false

        val pointerIds = executeListOfPointersIds(event)

        checkAndRemove(source, pointerIds)

        //сдвиг для catcher, чтобы не затирать pointer с оригинального touchListener
        val toOffset =
            if (source == PointerInfo.Source.TOUCH_CATCHER)
                activePointers.count { it.source == PointerInfo.Source.TARGET_VIEW }
            else 0

        //проходим по первым поинтерам
        for (i in 0 until 2 - toOffset) {
            val priority =
                if (i == 0 && (source == PointerInfo.Source.TARGET_VIEW || toOffset == 0))
                    PointerInfo.Priority.PRIMARY
                else
                    PointerInfo.Priority.DEPENDENT

            //собираем инфо о поинтерах
            createAndAddPointer(
                pointerIds, i,
                view, event,
                priority, source, toOffset
            )

            //Стираем pointers которые больше не существуют
            val actionMasked = event.actionMasked()
            //Если event прекратился - стираем всю информацию о поинтерах с него
            if (actionMasked in arrayOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL))
                activePointers.removeAll {
                    it.source == source
                }
            //Если убрали 1 палец - сохраняем текущий набор глобально чтобы стереть разницу в следующем заходе
            else if (actionMasked == MotionEvent.ACTION_POINTER_UP) {
                val checkAndRemoveList = when (source) {
                    PointerInfo.Source.TARGET_VIEW -> checkAndRemoveTargetPoints
                    PointerInfo.Source.TOUCH_CATCHER -> checkAndRemoveCatcherPoints
                }
                checkAndRemoveList.addAll(pointerIds)
            }
        }
        return@synchronized true
    }

    private fun createAndAddPointer(
        pointerIds: List<Int>,
        index: Int,
        view: View,
        event: MotionEvent,
        priority: PointerInfo.Priority,
        source: PointerInfo.Source,
        toOffset: Int
    ) {
        kotlin.runCatching {
            //Создаем pointer
            PointerInfo(
                pointerIds[index],
                view.x + event.getX(index),
                view.y + event.getY(index),
                priority,
                source
            )
        }.getOrNull()?.let { pointer ->
            //Если такой уже есть в списке активных
            if (activePointers.find {
                it.pointerId == pointer.pointerId &&
                it.source == pointer.source
            } != null)
            //Перезаписываем
                activePointers[index + toOffset] = pointer
            else
            //Добавляем
                activePointers.add(index + toOffset, pointer)
        }
    }

    private fun checkAndRemove(
        source: PointerInfo.Source,
        existingPointerIds: List<Int>,
    ) {
        val preparedList: MutableList<Int> = when (source) {
            PointerInfo.Source.TOUCH_CATCHER -> checkAndRemoveCatcherPoints
            PointerInfo.Source.TARGET_VIEW -> checkAndRemoveTargetPoints
        }

        if (preparedList.isNotEmpty()) {
            val pointersToRemove = preparedList.subtract(existingPointerIds.toSet())
            activePointers.removeAll {
                it.source == source && it.pointerId in pointersToRemove
            }
            preparedList.clear()
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


    fun endZoomingView() {
        LAST_SCALE = 1F
        isScalingNow = false
        mScaleFactor = 1f
        removeReserveCatcherPanel()
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
        when {
            mIsAnimatedTarget -> {
                mZoomableView = DummyAnimatedViewRepeater(mTarget.context).also { zoomTxtr ->
                    zoomTxtr.setTextureViewToCopy(mTarget)
                    CoroutineScope(Dispatchers.IO).launch {
                        while (isScalingNow) {
                            withContext(Dispatchers.Main) {
                                kotlin.runCatching {
                                    zoomTxtr.invalidate()
                                }
                            }
                            delay(1000 / Zoomy.ANIM_VIEW_FPS)
                        }
                    }
                }
            }
            mTarget is ImageView -> {
                mZoomableView = ImageView(mTarget.context).also { zoomImg ->
                    zoomImg.layoutParams = ViewGroup.LayoutParams(mTarget.width, mTarget.height)
                    zoomImg.setImageBitmap(ViewUtils.getBitmapFromView(view))
                }
            }
            else -> {
                mZoomableView = ImageView(mTarget.context).also { zoomImg ->
                    val targetBmp = Bitmap.createBitmap(
                        mTarget.measuredWidth,
                        mTarget.measuredHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(targetBmp)
                    mTarget.layout(mTarget.left, mTarget.top, mTarget.right, mTarget.bottom)
                    mTarget.draw(canvas)

                    zoomImg.layoutParams = ViewGroup.LayoutParams(mTarget.width, mTarget.height)
                    zoomImg.setImageBitmap(targetBmp)
                }
            }
        }

        addReserveCatcherPanel()
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
        mTarget.alpha = 0F
        if (mConfig.isImmersiveModeEnabled) hideSystemUI()
        mZoomListener?.onViewStartedZooming(mTarget)
    }

    private fun addReserveCatcherPanel() {
        mReserveCatcherPanel = View(mTarget.context)
        mReserveCatcherPanel!!.isClickable = true

        val mTargetRootParent = getParentRecursively(mTarget) ?: kotlin.run {
            endZoomingView()
            return
        }

        mReserveCatcherPanel!!.layoutParams = ViewGroup.LayoutParams(
            mTargetRootParent.width,
            mTargetRootParent.height
        )
        mTargetRootParent.addView(mReserveCatcherPanel)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addTouchCatcherPanel() {
        mTouchCatcherPanel = View(mTarget.context)

        val mTargetRootParent = getParentRecursively(mTarget) ?: kotlin.run {
            endZoomingView()
            return
        }

        mTouchCatcherPanel!!.layoutParams = ViewGroup.LayoutParams(
            mTargetRootParent.width,
            mTargetRootParent.height
        )
        mTouchCatcherPanel!!.setOnTouchListener { catcher: View, event: MotionEvent ->

            LAST_POINTER_COUNT = CURRENT_POINTER_COUNT
            val handle = collectViewPointers(catcher, event, PointerInfo.Source.TOUCH_CATCHER)
            if (!handle) return@setOnTouchListener false
            CURRENT_POINTER_COUNT = activePointers.count()

            val actionMasked = event.actionMasked()
            mState = STATE_ZOOMING

            if (isFingerUp(actionMasked))
                onFingerUp(event)
            else
                onFingerMove(event)

            log(
                "Catcher",
                "LAST: $LAST_POINTER_COUNT || CURR: $CURRENT_POINTER_COUNT, " +
                        "srcs: ${activePointers.joinToString(", ") { "[${it.pointerId} ${it.source}]" }}"
            )

            //если на экране минимум 2 пальца
            if (activePointers.count() == 2) {
                //рассчитываем зум по гипотенузе
                calculateScale(event)
            }
            true
        }
        mTargetRootParent.addView(mTouchCatcherPanel)
    }

    private fun calculateScale(event: MotionEvent) {
        val point1 = activePointers[0]
        val point2 = activePointers[1]

        val hypotenuse = sqrt(
            (point2.rawX - point1.rawX).pow(2) +
            (point2.rawY - point1.rawY).pow(2)
        ) / LAST_SCALE

        var fakeScaleFactor: Float = 1F
        when (event.actionMasked()) {
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_DOWN -> {
                fakeScaleFactorStartPointer = hypotenuse
                return
            }

            MotionEvent.ACTION_MOVE -> {
                fakeScaleFactor = hypotenuse / fakeScaleFactorStartPointer
            }
        }

        onScaleFactorChanged(fakeScaleFactor)
        return
    }

    private fun onScaleFactorChanged(scaleFactor: Float) {
        // Don't let the object get too large.
        mScaleFactor = Math.max(MIN_SCALE_FACTOR, Math.min(scaleFactor * LAST_SCALE, MAX_SCALE_FACTOR))
        LAST_SCALE = mScaleFactor
        mZoomableView!!.scaleX = mScaleFactor
        mZoomableView!!.scaleY = mScaleFactor
        obscureDecorView(mScaleFactor)
    }

    private fun onFingerUp(event: MotionEvent) {
        if (activePointers.count() == 0) {
            when (mState) {
                STATE_ZOOMING -> {
                    endZoomingView()
                }
                STATE_POINTER_DOWN -> mState = STATE_IDLE
            }
        } else {
            onFingerMove(event)
        }
    }

    private fun onFingerMove(event: MotionEvent) {
        if (mState == STATE_ZOOMING) {
            MotionUtils.midPointOfEvent(
                mCurrentMovementMidPoint,
                activePointers,
                event
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
            log("onFingerMove", "XXX: $x, YYY: $y")
        }
    }

    private fun removeTouchCatcherPanel() {
        getParentRecursively(mTarget)?.removeView(mTouchCatcherPanel)
    }
    private fun removeReserveCatcherPanel() {
        getParentRecursively(mTarget)?.removeView(mReserveCatcherPanel)
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

    private fun getParentRecursively(v: View): ViewGroup? {
        val presumablyParent = v.parent
        return if (presumablyParent is ViewGroup) getParentRecursively(presumablyParent)
            else if (v is ViewGroup) v
            else null
    }

    private fun obscureDecorView(factor: Float) {
        log(message = "scaleFactor: $factor", priority = Log.DEBUG)
        //normalize value between 0 and 1
        var normalizedValue = (factor - MIN_SCALE_FACTOR) / (MAX_SCALE_FACTOR - MIN_SCALE_FACTOR)
        normalizedValue = Math.min(0.75f, normalizedValue * 2)
        val obscure = Color.argb(
            (normalizedValue * 255 * mDimmingIntensity).toInt(),
            0, 0, 0
        )
        mShadow!!.setBackgroundColor(obscure)
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
        internal var MIN_SCALE_FACTOR = 0.8f
        internal var MAX_SCALE_FACTOR = 5f
    }
}

const val DEF_MIN_SCALE_FACTOR = 0.8f
const val DEF_MAX_SCALE_FACTOR = 5f