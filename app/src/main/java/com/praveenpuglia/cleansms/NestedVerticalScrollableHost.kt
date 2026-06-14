package com.praveenpuglia.cleansms

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Layout to wrap a scrollable component inside a ViewPager2. Provided as a solution to the problem
 * where pages of ViewPager2 is horizontal and its has a vertical scrolling container.
 */
class NestedVerticalScrollableHost : FrameLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)


    private var lastX = 0.0f
    private var lastY = 0.0f

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x
                lastY = e.y
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val currentX = e.x
                val currentY = e.y
                val dx = abs(currentX - lastX)
                val dy = abs(currentY - lastY)
                parent.requestDisallowInterceptTouchEvent(dy > dx)
            }
        }
        return super.onInterceptTouchEvent(e)
    }
}
