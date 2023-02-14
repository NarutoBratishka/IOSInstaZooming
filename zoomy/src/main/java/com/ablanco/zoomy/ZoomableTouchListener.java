package com.ablanco.zoomy;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.view.GestureDetector;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;

/**
 * Created by Álvaro Blanco Cabrero on 12/02/2017.
 * Zoomy.
 */

class ZoomableTouchListener implements View.OnTouchListener, ScaleGestureDetector.OnScaleGestureListener {

    private static final int STATE_IDLE = 0;
    private static final int STATE_POINTER_DOWN = 1;
    private static final int STATE_ZOOMING = 2;

    private static int CURRENT_POINTER_COUNT = 0;
    private static int LAST_POINTER_COUNT = 0;

    private static float LAST_SCALE = 1F;

    private static final float MIN_SCALE_FACTOR = 0.2f;
    private static final float MAX_SCALE_FACTOR = 5f;
    private final TapListener mTapListener;
    private final LongPressListener mLongPressListener;
    private final DoubleTapListener mDoubleTapListener;
    private int mState = STATE_IDLE;
    private TargetContainer mTargetContainer;
    private View mTarget;
    private ImageView mZoomableView;
    private View mShadow;
    private ScaleGestureDetector mScaleGestureDetector;
    private GestureDetector mGestureDetector;
    private GestureDetector.SimpleOnGestureListener mGestureListener =
        new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (mTapListener != null) mTapListener.onTap(mTarget);
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (mLongPressListener != null) mLongPressListener.onLongPress(mTarget);
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (mDoubleTapListener != null) mDoubleTapListener.onDoubleTap(mTarget);
                return true;
            }
        };
    private float mScaleFactor = 1f;
    private PointF mCurrentMovementMidPoint = new PointF();
    private PointF mInitialPinchMidPoint = new PointF();
    private Point mTargetViewCords = new Point();
    private boolean mAnimatingZoomEnding = false;
    private Interpolator mEndZoomingInterpolator;
    private ZoomyConfig mConfig;
    private ZoomListener mZoomListener;
    private Runnable mEndingZoomAction = new Runnable() {
        @Override
        public void run() {
            removeFromDecorView(mShadow);
            removeFromDecorView(mZoomableView);
            mTarget.setVisibility(View.VISIBLE);
            mZoomableView = null;
            mCurrentMovementMidPoint = new PointF();
            mInitialPinchMidPoint = new PointF();
            mAnimatingZoomEnding = false;
            mState = STATE_IDLE;

            if (mZoomListener != null) mZoomListener.onViewEndedZooming(mTarget);

            if (mConfig.isImmersiveModeEnabled()) showSystemUI();
        }
    };


    ZoomableTouchListener(
        TargetContainer targetContainer,
        View view,
        ZoomyConfig config,
        Interpolator interpolator,
        ZoomListener zoomListener,
        TapListener tapListener,
        LongPressListener longPressListener,
        DoubleTapListener doubleTapListener
    ) {
        this.mTargetContainer = targetContainer;
        this.mTarget = view;
        this.mConfig = config;
        this.mEndZoomingInterpolator = interpolator != null
                ? interpolator : new AccelerateDecelerateInterpolator();
        this.mScaleGestureDetector = new ScaleGestureDetector(view.getContext(), this);
        this.mGestureDetector = new GestureDetector(view.getContext(), mGestureListener);
        this.mZoomListener = zoomListener;
        this.mTapListener = tapListener;
        this.mLongPressListener = longPressListener;
        this.mDoubleTapListener = doubleTapListener;
    }

    @Override
    public boolean onTouch(View v, MotionEvent ev) {

        if (mAnimatingZoomEnding || ev.getPointerCount() > 2) return true;

        mScaleGestureDetector.onTouchEvent(ev);
        mGestureDetector.onTouchEvent(ev);

        int action = ev.getAction() & MotionEvent.ACTION_MASK;
        CURRENT_POINTER_COUNT = ev.getPointerCount();

        boolean newPointerZoom = false;
        if (LAST_POINTER_COUNT == 1 && CURRENT_POINTER_COUNT == 2) newPointerZoom = true;

        switch (action) {
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_DOWN:
                switch (mState) {
                    case STATE_IDLE:
                        mState = STATE_POINTER_DOWN;
                        break;
                    case STATE_POINTER_DOWN:
                        mState = STATE_ZOOMING;
                        MotionUtils.midPointOfEvent(mInitialPinchMidPoint, ev, newPointerZoom);
                        startZoomingView(mTarget);
                        break;
                }
                break;

            case MotionEvent.ACTION_MOVE:

                if (mState == STATE_ZOOMING) {
                    MotionUtils.midPointOfEvent(mCurrentMovementMidPoint, ev, newPointerZoom);
                    //because our initial pinch could be performed in any of the view edges,
                    //we need to substract this difference and add system bars height
                    //as an offset to avoid an initial transition jump
                    mCurrentMovementMidPoint.x -= mInitialPinchMidPoint.x;
                    mCurrentMovementMidPoint.y -= mInitialPinchMidPoint.y;
                    //because previous function returns the midpoint for relative X,Y coords,
                    //we need to add absolute view coords in order to ensure the correct position
                    mCurrentMovementMidPoint.x += mTargetViewCords.x;
                    mCurrentMovementMidPoint.y += mTargetViewCords.y;
                    float x = mCurrentMovementMidPoint.x;
                    float y = mCurrentMovementMidPoint.y;
                    mZoomableView.setX(x);
                    mZoomableView.setY(y);
                }

                break;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:

                if (CURRENT_POINTER_COUNT == 1) {
                    switch (mState) {
                        case STATE_ZOOMING:
                            LAST_SCALE = 1F;
                            endZoomingView();
                            break;
                        case STATE_POINTER_DOWN:
                            mState = STATE_IDLE;
                            break;
                    }
                } else {
                    LAST_SCALE = mZoomableView.getScaleX();

                    if (mState == STATE_ZOOMING) {
                        MotionUtils.midPointOfEvent(mCurrentMovementMidPoint, ev, newPointerZoom);
                        //because our initial pinch could be performed in any of the view edges,
                        //we need to substract this difference and add system bars height
                        //as an offset to avoid an initial transition jump
                        mCurrentMovementMidPoint.x -= mInitialPinchMidPoint.x;
                        mCurrentMovementMidPoint.y -= mInitialPinchMidPoint.y;
                        //because previous function returns the midpoint for relative X,Y coords,
                        //we need to add absolute view coords in order to ensure the correct position
                        mCurrentMovementMidPoint.x += mTargetViewCords.x;
                        mCurrentMovementMidPoint.y += mTargetViewCords.y;
                        float x = mCurrentMovementMidPoint.x;
                        float y = mCurrentMovementMidPoint.y;
                        mZoomableView.setX(x);
                        mZoomableView.setY(y);
                    }
                }


                break;

        }

        return true;
    }


    private void endZoomingView() {
        if (mConfig.isZoomAnimationEnabled()) {
            mAnimatingZoomEnding = true;
            mZoomableView.animate()
                .x(mTargetViewCords.x)
                .y(mTargetViewCords.y)
                .scaleX(1)
                .scaleY(1)
                .setInterpolator(mEndZoomingInterpolator)
                .withEndAction(mEndingZoomAction).start();
        } else mEndingZoomAction.run();
    }


    @SuppressLint("ClickableViewAccessibility")
    private void startZoomingView(View view) {
        mZoomableView = new ImageView(mTarget.getContext());
        mZoomableView.setLayoutParams(new ViewGroup.LayoutParams(mTarget.getWidth(), mTarget.getHeight()));
        mZoomableView.setImageBitmap(ViewUtils.getBitmapFromView(view));
//        if (mTarget.getTag() instanceof Integer) mZoomableView.setOnTouchListener((touchedView, motionEvent) -> {
//            MotionEvent fakeEvent = MotionEvent.obtain(motionEvent);
//            if (fakeEvent.getActionMasked() == MotionEvent.ACTION_DOWN)
//                fakeEvent.setAction(MotionEvent.ACTION_POINTER_DOWN);
//            if (fakeEvent.getActionMasked() == MotionEvent.ACTION_UP)
//                fakeEvent.setAction(MotionEvent.ACTION_POINTER_UP);
//            fakeEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
//            fakeEvent.recycle();
//            this.onTouch(mTarget, fakeEvent);
//            return true;
//        });

        //show the view in the same coords
        mTargetViewCords = ViewUtils.getViewAbsoluteCords(view);

        mZoomableView.setX(mTargetViewCords.x);
        mZoomableView.setY(mTargetViewCords.y);

        if (mShadow == null) mShadow = new View(mTarget.getContext());
        mShadow.setBackgroundResource(0);

        addToDecorView(mShadow);
        addToDecorView(mZoomableView);

        //trick for simulating the view is getting out of his parent
        disableParentTouch(mTarget.getParent());
        mTarget.setVisibility(View.INVISIBLE);

        if (mConfig.isImmersiveModeEnabled()) hideSystemUI();
        if (mZoomListener != null) mZoomListener.onViewStartedZooming(mTarget);
    }


    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        if (mZoomableView == null) return false;

        mScaleFactor *= detector.getScaleFactor();

        // Don't let the object get too large.
        mScaleFactor = Math.max(MIN_SCALE_FACTOR, Math.min(mScaleFactor, MAX_SCALE_FACTOR));

        mZoomableView.setScaleX(LAST_SCALE * mScaleFactor);
        mZoomableView.setScaleY(LAST_SCALE * mScaleFactor);
        obscureDecorView(mScaleFactor);
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return mZoomableView != null;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        mScaleFactor = 1f;
    }

    private void addToDecorView(View v) {
        mTargetContainer.getDecorView().addView(v);
    }

    private void removeFromDecorView(View v) {
        mTargetContainer.getDecorView().removeView(v);
    }

    private void obscureDecorView(float factor) {
        //normalize value between 0 and 1
        float normalizedValue = (factor - MIN_SCALE_FACTOR) / (MAX_SCALE_FACTOR - MIN_SCALE_FACTOR);
        normalizedValue = Math.min(0.75f, normalizedValue * 2);
        int obscure = Color.argb((int) (normalizedValue * 255), 0, 0, 0);
        mShadow.setBackgroundColor(obscure);
    }

    private void hideSystemUI() {
        mTargetContainer.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                | View.SYSTEM_UI_FLAG_FULLSCREEN); // hide status ba;
    }

    private void showSystemUI() {
        mTargetContainer.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private void disableParentTouch(ViewParent view) {
        view.requestDisallowInterceptTouchEvent(true);
        if (view.getParent() != null) disableParentTouch((view.getParent()));
    }
}
