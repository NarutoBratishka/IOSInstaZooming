package com.ablanco.zoomy

import android.graphics.PointF
import java.util.*

/**
 * Created by Álvaro Blanco Cabrero on 11/02/2017.
 * Zoomy.
 */
internal object MotionUtils {
    var lastOffsetX = 0f
    var lastOffsetY = 0f
    fun midPointOfEvent(point: PointF, pointers: List<PointerInfo>, breakFakeFinger: Boolean) {
        var divider = 1
        val x: Float
        val y: Float
        if (pointers.size == 2) {
            divider = 2
            if (breakFakeFinger) {
                lastOffsetX = lastOffsetX - pointers[1].rawX
                lastOffsetY = lastOffsetY - pointers[1].rawY
            }
            x = pointers[0].rawX + pointers[1].rawX
            y = pointers[0].rawY + pointers[1].rawY
        } else {
            x = pointers[0].rawX
            y = pointers[0].rawY
        }
        point[x / divider + lastOffsetX] = y / divider + lastOffsetY
    }

}