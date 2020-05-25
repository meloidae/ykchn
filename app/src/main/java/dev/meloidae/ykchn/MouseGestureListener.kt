package dev.meloidae.ykchn

import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.max

abstract class MouseGestureListener(context: Context) : View.OnTouchListener {
    companion object {
        private val TAG = MouseGestureListener::class.java.simpleName

        private const val STATE_START = 0x00
        private const val STATE_POTENTIAL_MOVE = 0x01
        private const val STATE_POTENTIAL_CLICK = 0x02
        private const val STATE_POTENTIAL_DRAG = 0x03
        private const val STATE_POTENTIAL_DOUBLE_CLICK = 0x04
    }

    private val handler = Handler(context.applicationContext.mainLooper)
    private var prevX = 0.0f
    private var prevY = 0.0f
    private var prevT = 0

    private var maxPointerCount = 0
    // private var doubleTapped = false
    // private var dragStarted = false
    // private var maxPointerCount = 0
    // private var doubleTapTimeout = 100
    // private var state = 0

    private var cursorMoveStarted = false
    private var clicked = false
    private var dragStarted = false
    private var doubleClicked = false
    var tapTimeout = ViewConfiguration.getTapTimeout()
    var tapIntervalTimeout = ViewConfiguration.getDoubleTapTimeout()
    private var state = STATE_START

    // private val listener = object : GestureDetector.SimpleOnGestureListener() {
    //     override fun onDown(e: MotionEvent): Boolean {
    //         prevX = e.x
    //         prevY = e.y
    //         return true
    //     }

    //     override fun onSingleTapUp(e: MotionEvent?): Boolean {
    //         Log.d(TAG, "onSingleTapUp()")
    //         return super.onSingleTapUp(e)
    //     }

    //     override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
    //         Log.d(TAG, "onSingleTapConfirmed()")
    //         val pointerCount = e.pointerCount
    //         onClick(pointerCount)
    //         return super.onSingleTapConfirmed(e)
    //     }

    //     override fun onDoubleTap(e: MotionEvent): Boolean {
    //         Log.d(TAG, "onDoubleTap()")
    //         doubleTapped = true
    //         handler.postDelayed(dragStartCallback, ViewConfiguration.getTapTimeout().toLong())
    //         return super.onDoubleTap(e)
    //     }
    // }

    // private val gestureDetector = GestureDetector(context, listener)

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        maxPointerCount = max(maxPointerCount, event.pointerCount)
        when (val action = event.action) {
            MotionEvent.ACTION_DOWN -> {
                prevX = event.x
                prevY = event.y
                when (state) {
                    STATE_START -> {
                        onInitialDown()
                        state = STATE_POTENTIAL_MOVE
                    }
                    STATE_POTENTIAL_CLICK -> {
                        if (clicked) {
                            onInitialDown()
                            clicked = false
                            state = STATE_POTENTIAL_MOVE
                        } else {
                            handler.removeCallbacks(clickCallback)
                            handler.postDelayed(dragStartCallback, tapTimeout.toLong())
                            state = STATE_POTENTIAL_DRAG
                        }
                    }
                    else -> {
                        throw UnsupportedOperationException(
                            "Should not be in state $state for action ${MotionEvent.actionToString(action)}")
                    }
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointerCount = max(maxPointerCount, event.pointerCount)
            }
            MotionEvent.ACTION_UP -> {
                when (state) {
                    STATE_POTENTIAL_MOVE -> {
                        if (cursorMoveStarted) {
                            cursorMoveStarted = false
                            maxPointerCount = 0
                            state = STATE_START
                        } else {
                            handler.removeCallbacks(cursorMoveStartCallback)
                            handler.postDelayed(clickCallback, tapIntervalTimeout.toLong())
                            state = STATE_POTENTIAL_CLICK
                        }
                    }
                    STATE_POTENTIAL_DRAG -> {
                        if (dragStarted) {
                            dragStarted = false
                            onDragEnd()
                        } else {
                            handler.removeCallbacks(dragStartCallback)
                            onDoubleClick(maxPointerCount)
                        }
                        maxPointerCount = 0
                        state = STATE_START
                    } else -> {
                    throw UnsupportedOperationException(
                        "Should not be in state $state for action ${MotionEvent.actionToString(action)}")

                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (state) {
                    STATE_POTENTIAL_MOVE -> {
                        if (cursorMoveStarted) {
                            val dx = (event.x - prevX).toInt()
                            val dy = (event.y - prevY).toInt()
                            when (event.pointerCount) {
                                1 -> {
                                    onCursorMove(dx, dy)
                                }
                                2 -> {
                                    onScroll(dx, dy)
                                }
                            }
                            prevX = event.x
                            prevY = event.y
                        }
                    }
                    STATE_POTENTIAL_DRAG -> {
                        if (dragStarted) {
                            val dx = (event.x - prevX).toInt()
                            val dy = (event.y - prevY).toInt()
                            onDragMove(dx, dy)
                            prevX = event.x
                            prevY = event.y
                        }
                    }
                }
            }
        }
        return true
    }

    // override fun onTouch(v: View, event: MotionEvent): Boolean {
    //     when (event.action) {
    //         MotionEvent.ACTION_MOVE -> {
    //             val dx = (event.x - prevX).toInt()
    //             val dy = (event.y - prevY).toInt()
    //             prevX = event.x
    //             prevY = event.y
    //             if (dragStarted) {
    //                 onDragMove(dx, dy)
    //             } else {
    //                 onCursorMove(dx, dy)
    //             }
    //         }
    //         MotionEvent.ACTION_UP -> {
    //             if (doubleTapped) {
    //                 // There was a double tap
    //                 handler.removeCallbacks(dragStartCallback)
    //                 doubleTapped = false
    //                 if (dragStarted) {
    //                     // Drag has been initiated, so end it
    //                     onDragEnd()
    //                     dragStarted = false
    //                 } else {
    //                     // No drag yet = confirmed double click
    //                     onDoubleClick(event.pointerCount)
    //                 }
    //             }
    //         }
    //     }
    //     if (event.action != MotionEvent.ACTION_MOVE) {
    //         Log.d(TAG, "onTouch() action: ${MotionEvent.actionToString(event.action)}")
    //     }
    //     return gestureDetector.onTouchEvent(event)
    // }

    private val dragStartCallback = Runnable {
        dragStarted = true
        onDragStart()
    }

    // private fun clickCallback(pointerCount: Int): Runnable {
    //     return Runnable {
    //         onClick(pointerCount)
    //     }
    // }

    private val clickCallback = Runnable {
        onClick(maxPointerCount)
        clicked = true
        maxPointerCount = 0
    }

    private val cursorMoveStartCallback = Runnable {
        cursorMoveStarted = true
    }

    private fun onInitialDown() {
        handler.postDelayed(cursorMoveStartCallback, tapTimeout.toLong())
    }

    abstract fun onClick(pointerCount: Int)
    abstract fun onCursorMove(dx: Int, dy: Int)
    abstract fun onDoubleClick(pointerCount: Int)
    abstract fun onDragStart()
    abstract fun onDragEnd()
    abstract fun onDragMove(dx: Int, dy: Int)
    abstract fun onScroll(dx: Int, dy: Int)

    init {
        // gestureDetector.setOnDoubleTapListener(listener)
    }
}