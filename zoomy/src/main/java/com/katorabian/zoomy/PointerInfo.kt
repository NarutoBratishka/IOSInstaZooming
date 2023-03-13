package com.katorabian.zoomy

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
//        log(TAG, "rawX: $rawX, rawY: $rawY")
    }

    internal enum class Priority {
        PRIMARY, DEPENDENT
    }

    internal enum class Source {
        TARGET_VIEW, TOUCH_CATCHER
    }

    override fun toString(): String {
        return "PointerInfo(pointerId=$pointerId, rawX=$rawX, rawY=$rawY, priority=$priority, source=$source)"
    }
}