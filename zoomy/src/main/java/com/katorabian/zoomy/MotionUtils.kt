package com.katorabian.zoomy

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

    private var pointerBeforePointerUp: List<PointerInfo> = emptyList()
    private var previousPointers: List<PointerInfo> = emptyList()


    //похоже что фича перемещения увеличенного видео всё еще работает некорректно
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
                if (pointerBeforePointerUp.isNotEmpty()) {
                    val currExistPtr = pointers.single()
                    //Ищем элемент, который у нас был, чтоб скорректировать новые координаты
                    //Потому что ACTION_UP
                    pointerBeforePointerUp.find {
                        it.pointerId == currExistPtr.pointerId && it.source == currExistPtr.source
                    }?.let { removedPointer ->
                        newOffsetX = savedPositionX - removedPointer.rawX
                        newOffsetY = savedPositionY - removedPointer.rawY
                    }
                    pointerBeforePointerUp = emptyList()

                } else if (actionMasked == MotionEvent.ACTION_UP) {
                    val currExistPtr = pointers.single()
                    //Ищем элемент, который у нас был, чтоб скорректировать новые координаты
                    //Потому что ACTION_UP
                    previousPointers.find {
                        it.pointerId == currExistPtr.pointerId && it.source == currExistPtr.source
                    }?.let { removedPointer ->
                        newOffsetX = savedPositionX - removedPointer.rawX
                        newOffsetY = savedPositionY - removedPointer.rawY
                    }
                }
                x = (pointers[0].rawX + newOffsetX) * 2
                y = (pointers[0].rawY + newOffsetY) * 2
                previousPointers = pointers.toList()
            }

            else -> {
                if (actionMasked == MotionEvent.ACTION_POINTER_UP) {
                    pointerBeforePointerUp = pointers.toList()
                    //следующие расчеты происходят в when -> 1
                } else if (actionMasked in arrayOf(
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_POINTER_DOWN
                )) {
                    val newPointer = kotlin.runCatching {
                        val currExistPtr = previousPointers.single()
                        //Ищем элемент, которого у нас не было, чтоб скорректировать новые координаты
                        //Потому что ACTION_DOWN
                        pointers.find {
                            it.pointerId != currExistPtr.pointerId || it.source != currExistPtr.source
                        }?: pointers[1]
                    }.getOrDefault(pointers[1])

                    newOffsetX -= newPointer.rawX
                    newOffsetY -= newPointer.rawY
                }
                x = (pointers[0].rawX + pointers[1].rawX + newOffsetX) * 2
                y = (pointers[0].rawY + pointers[1].rawY + newOffsetY) * 2
                previousPointers = pointers.toList()
            }
        }
        val pointX = x / 2
        val pointY = y / 2

        savedPositionX = pointX
        savedPositionY = pointY
        point[pointX] = pointY
    }

    fun MotionEvent.actionMasked() = action and actionMasked

}