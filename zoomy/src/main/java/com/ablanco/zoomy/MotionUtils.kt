package com.ablanco.zoomy

import android.graphics.PointF
import android.util.Log
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

    private var pointerBeforePointerUp: List<PointerInfo> = emptyList()

    fun midPointOfEvent(point: PointF, pointers: List<PointerInfo>, event: MotionEvent) {
        val x: Float
        val y: Float

        val actionMasked = event.actionMasked()
        when (pointers.size) {
            0 -> {
                point[savedPositionX] = savedPositionY
                return
            }
            1 -> {
                //TODO вот тут нужно применять тот магический свиг
                if (pointerBeforePointerUp.isNotEmpty()) {
                    val currExistPtr = pointers.single()
                    pointerBeforePointerUp.find {
                        it.pointerId == currExistPtr.pointerId && it.source == currExistPtr.source
                    }?.let { removedPointer ->
                        newOffsetX = savedPositionX * 2 - removedPointer.rawX
                        newOffsetY = savedPositionY * 2 - removedPointer.rawY
                    }
                    pointerBeforePointerUp = emptyList()
                }
                x = pointers[0].rawX + newOffsetX
                y = pointers[0].rawY + newOffsetY
            }
            else -> {
                //TODO вот тут надо высчитать сдвиг, который нужно сохранить и применять дальше ко всем пересчетам без исключения
                Log.e("MotionEvent", MotionEvent.actionToString(actionMasked))
                if (actionMasked == MotionEvent.ACTION_POINTER_UP) {
                    pointerBeforePointerUp = pointers.toList()
                }
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

    fun MotionEvent.actionMasked() = action and actionMasked

}