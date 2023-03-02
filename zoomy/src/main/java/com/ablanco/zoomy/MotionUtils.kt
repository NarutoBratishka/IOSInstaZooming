package com.ablanco.zoomy

import android.graphics.PointF
import android.view.MotionEvent

/**
 * Created by Álvaro Blanco Cabrero on 11/02/2017.
 * Zoomy.
 */
internal object MotionUtils {
    private var savedPositionX = 0f
    private var savedPositionY = 0f

    private var newOffsetX = 0F
    private var newOffsetY = 0F
    fun midPointOfEvent(point: PointF, pointers: List<PointerInfo>, event: MotionEvent) {
        val x: Float
        val y: Float

        val actionMasked = event.actionMasked()
        when (actionMasked) {
//            MotionEvent.ACTION_POINTER_DOWN,
//            MotionEvent.ACTION_DOWN -> {
//                point[lastOffsetX] = lastOffsetY
//                return
//            }
//
//            MotionEvent.ACTION_POINTER_UP,
//            MotionEvent.ACTION_UP,
//            MotionEvent.ACTION_CANCEL -> {
//                return
//            }
        }

        when (pointers.size) {
            0 -> {
                point[savedPositionX] = savedPositionY
                return
            }
            1 -> {
                val lastPointer = pointers.last()
                if (actionMasked == MotionEvent.ACTION_MOVE && newOffsetX == newOffsetY) {
                    newOffsetX = lastPointer.rawX - savedPositionX
                    newOffsetY = lastPointer.rawY - savedPositionY
                }
                point[savedPositionX + lastPointer.rawX + newOffsetX] = savedPositionY + lastPointer.rawY + newOffsetY
                return
            }
            else -> {
                x = pointers[0].rawX + pointers[1].rawX
                y = pointers[0].rawY + pointers[1].rawY
            }
        }
        var pointX = x / 2
        var pointY = y / 2

        when (actionMasked) {
            MotionEvent.ACTION_POINTER_UP -> {
                savedPositionX = pointX
                savedPositionY = pointY
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
//                pointX = savedXOffset
//                pointY = savedYOffset
            }
        }
//        Log.e("TEST", "XXX: $pointX, YYY: $pointY")
//        if (actionMasked == MotionEvent.ACTION_POINTER_UP) {
//            Log.d("TEST", "XXX: $pointX, YYY: $pointY")
//            Log.e("TEST", "XXX: $pointX, YYY: $pointY")
//        }
        point[pointX] = pointY
    }

    fun MotionEvent.actionMasked() = action and MotionEvent.ACTION_MASK

}