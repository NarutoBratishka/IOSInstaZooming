package com.katorabian.zoomy

import android.content.Context
import android.graphics.Canvas
import android.view.View


open class DummyAnimatedViewRepeater(context: Context) : View(context) {
    private var animatedViewToCopy: View? = null
    fun setTextureViewToCopy(view: View?) {
        animatedViewToCopy = view
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        animatedViewToCopy?.draw(canvas)
    }
}