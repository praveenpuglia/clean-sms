package com.praveenpuglia.cleansms

import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class StickyDayHeaderDecoration(
    private val adapter: MessageAdapter
) : RecyclerView.ItemDecoration() {

    private var currentStickyPosition = RecyclerView.NO_POSITION
    private var stickyTranslationY = 0f
    private val interpolator = DecelerateInterpolator()

    override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(canvas, parent, state)

        val topChild = parent.getChildAt(0) ?: return
        val topChildPosition = parent.getChildAdapterPosition(topChild)
        if (topChildPosition == RecyclerView.NO_POSITION) return

        val headerPos = adapter.getHeaderPositionForItem(topChildPosition)
        if (headerPos == RecyclerView.NO_POSITION) return

        // Don't show sticky header if we're at the very top (oldest messages)
        val layoutManager = parent.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
        val firstVisiblePosition = layoutManager?.findFirstVisibleItemPosition() ?: 0
        
        // If the header itself is visible, don't draw the sticky version
        if (firstVisiblePosition <= headerPos) {
            return
        }

        // Don't show sticky header if we're at the bottom (most recent messages)
        // Check if this is the last section
        val lastVisiblePosition = layoutManager?.findLastVisibleItemPosition() ?: 0
        val isLastSection = headerPos >= adapter.itemCount - 1 || 
                           (headerPos + 1 < adapter.itemCount && 
                            adapter.getHeaderPositionForItem(adapter.itemCount - 1) == headerPos)
        
        // If we can see the last item and this is the last section, don't show sticky header
        if (isLastSection && lastVisiblePosition >= adapter.itemCount - 1) {
            return
        }

        val currentHeader = getHeaderViewForItem(headerPos, parent)
        fixLayoutSize(parent, currentHeader)

        val contactPoint = currentHeader.bottom
        val childInContact = getChildInContact(parent, contactPoint, headerPos)

        // Animate position changes
        val targetY = if (childInContact != null && adapter.isHeader(parent.getChildAdapterPosition(childInContact))) {
            (childInContact.top - currentHeader.height).toFloat()
        } else {
            0f
        }

        // Smooth animation when header position changes
        if (currentStickyPosition != headerPos) {
            currentStickyPosition = headerPos
            stickyTranslationY = targetY
        } else {
            // Interpolate towards target
            val diff = targetY - stickyTranslationY
            if (Math.abs(diff) > 0.5f) {
                stickyTranslationY += diff * 0.3f // Smooth animation factor
            } else {
                stickyTranslationY = targetY
            }
        }

        drawHeader(canvas, currentHeader, stickyTranslationY, parent.paddingLeft.toFloat())
        
        // Request another frame if animating
        if (Math.abs(stickyTranslationY - targetY) > 0.5f) {
            parent.invalidate()
        }
    }

    private fun getHeaderViewForItem(itemPosition: Int, parent: RecyclerView): View {
        val layoutInflater = LayoutInflater.from(parent.context)
        val headerView = layoutInflater.inflate(R.layout.item_day_indicator, parent, false)
        
        // Bind the header data
        val viewHolder = adapter.onCreateViewHolder(parent, MessageAdapter.VIEW_TYPE_DAY_INDICATOR)
        adapter.onBindViewHolder(viewHolder, itemPosition)
        
        if (viewHolder is MessageAdapter.DayIndicatorVH) {
            (headerView.findViewById<TextView>(R.id.day_indicator_text)).text = 
                viewHolder.text.text
        }
        
        return headerView
    }

    private fun drawHeader(canvas: Canvas, header: View, translationY: Float, translationX: Float = 0f) {
        canvas.save()
        canvas.translate(translationX, translationY)
        header.draw(canvas)
        canvas.restore()
    }

    private fun getChildInContact(parent: RecyclerView, contactPoint: Int, currentHeaderPos: Int): View? {
        var childInContact: View? = null
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val childPosition = parent.getChildAdapterPosition(child)
            if (childPosition == RecyclerView.NO_POSITION) continue
            
            // Only consider headers that come after the current sticky header
            if (childPosition > currentHeaderPos && adapter.isHeader(childPosition)) {
                if (child.bottom > contactPoint) {
                    if (child.top <= contactPoint) {
                        childInContact = child
                        break
                    }
                }
            }
        }
        return childInContact
    }

    private fun fixLayoutSize(parent: ViewGroup, view: View) {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED)

        val childWidthSpec = ViewGroup.getChildMeasureSpec(
            widthSpec,
            parent.paddingLeft + parent.paddingRight,
            view.layoutParams.width
        )
        val childHeightSpec = ViewGroup.getChildMeasureSpec(
            heightSpec,
            parent.paddingTop + parent.paddingBottom,
            view.layoutParams.height
        )

        view.measure(childWidthSpec, childHeightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
}
