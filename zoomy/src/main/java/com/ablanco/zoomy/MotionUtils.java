package com.ablanco.zoomy;

import android.graphics.PointF;
import android.view.MotionEvent;

/**
 * Created by Álvaro Blanco Cabrero on 11/02/2017.
 * Zoomy.
 */
class MotionUtils {
    static float lastOffsetX = 0;
    static float lastOffsetY = 0;

    static void midPointOfEvent(PointF point, MotionEvent event, boolean breakFakeFinger){
        int divider = 1;
        float x;
        float y;

        if (event.getPointerCount() == 2) {
            divider = 2;

            if (breakFakeFinger) {
                lastOffsetX = lastOffsetX - event.getX(1);
                lastOffsetY = lastOffsetY - event.getY(1);
            }

            x = event.getX(0) + event.getX(1);
            y = event.getY(0) + event.getY(1);
        } else {
            x = event.getX(0);
            y = event.getY(0);
        }

        point.set(x / divider + lastOffsetX, y / divider + lastOffsetY);
    }
}
