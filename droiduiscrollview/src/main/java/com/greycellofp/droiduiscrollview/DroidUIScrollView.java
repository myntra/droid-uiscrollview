package com.greycellofp.droiduiscrollview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.FocusFinder;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AnimationUtils;
import android.widget.EdgeEffect;
import android.widget.FrameLayout;
import android.widget.OverScroller;

import java.util.List;

/**
 * Created by pawan.kumar1 on 19/01/15.
 */
public class DroidUIScrollView extends FrameLayout {
    private static final String TAG = DroidUIScrollView.class.getSimpleName();

    private static final int ANIMATED_SCROLL_GAP = 250;

    private static final float MAX_SCROLL_FACTOR = 0.5f;

    private long mLastScroll;

    private final Rect mTempRect = new Rect();
    private OverScroller mScroller;
    private EdgeEffect mEdgeGlowTop;
    private EdgeEffect mEdgeGlowBottom;
    private EdgeEffect mEdgeGlowLeft;
    private EdgeEffect mEdgeGlowRight;

    private int mLastMotionX;
    private int mLastMotionY;

    private boolean mIsLayoutDirty = true;

    private View mChildToScrollTo = null;

    private boolean mIsBeingDragged = false;

    private VelocityTracker mVelocityTracker;

    @ViewDebug.ExportedProperty(category = "layout")
    private boolean mFillViewport;

    private boolean mSmoothScrollingEnabled = true;

    private int mTouchSlop;
    private int mMinimumVelocity;
    private int mMaximumVelocity;

    private int mOverscrollDistance;
    private int mOverflingDistance;

    /**
     * Used during scrolling to retrieve the new offset within the window.
     */
    private final int[] mScrollOffset = new int[2];
    private final int[] mScrollConsumed = new int[2];
    private int mNestedYOffset;

    private int mNestedXOffset;

    protected float mVerticalScrollFactor;
    protected float mHorizontalScrollFactor;

    /**
     * ID of the active pointer. This is used to retain consistency during
     * drags/flings if multiple pointers are used.
     */
    private int mActivePointerId = INVALID_POINTER;

    private static final int INVALID_POINTER = -1;

    private SavedState mSavedState;

    public DroidUIScrollView(Context context) {
        this(context, null);
    }

    public DroidUIScrollView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DroidUIScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        initScrollView();

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.DroidUIScrollView, defStyleAttr, defStyleAttr);

        setFillViewport(a.getBoolean(R.styleable.DroidUIScrollView_fillViewport, false));
        a.recycle();
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return true;
    }

    private void initScrollView() {
        mScroller = new OverScroller(getContext());
        setFocusable(true);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setWillNotDraw(false);
        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mOverscrollDistance = configuration.getScaledOverscrollDistance();
        mOverflingDistance = configuration.getScaledOverflingDistance();
    }

    @Override
    public void addView(View child) {
        if (getChildCount() > 0) {
            throw new IllegalStateException("ScrollView can host only one direct child");
        }

        super.addView(child);
    }

    @Override
    public void addView(View child, int index) {
        if (getChildCount() > 0) {
            throw new IllegalStateException("ScrollView can host only one direct child");
        }

        super.addView(child, index);
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        if (getChildCount() > 0) {
            throw new IllegalStateException("ScrollView can host only one direct child");
        }

        super.addView(child, params);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (getChildCount() > 0) {
            throw new IllegalStateException("ScrollView can host only one direct child");
        }

        super.addView(child, index, params);
    }

    /**
     * @return Returns true this ScrollView can be scrolled
     */
    private boolean canScroll() {
        View child = getChildAt(0);
        if (child != null) {
            int childHeight = child.getHeight();
            int childWidth = child.getWidth();
            return (getHeight() < childHeight + getPaddingTop() + getPaddingBottom()) ||
                    (getWidth() < childWidth + getPaddingLeft() + getPaddingRight());
        }
        return false;
    }

    /**
     * Indicates whether this DroidUIScrollView's content is stretched to fill the viewport.
     *
     * @return True if the content fills the viewport, false otherwise.
     *
     * @attr ref android.R.styleable#DroidUIScrollView_fillViewport
     */
    public boolean isFillViewport() {
        return mFillViewport;
    }

    /**
     * Indicates this DroidUIScrollView whether it should stretch its content to fill
     * the viewport or not.
     *
     * @param fillViewport True to stretch the content to the viewport's
     *        boundaries, false otherwise.
     *
     * @attr ref R.styleable#DroidUIScrollView_fillViewport
     */
    public void setFillViewport(boolean fillViewport) {
        if (fillViewport != mFillViewport) {
            mFillViewport = fillViewport;
            requestLayout();
        }
    }

    /**
     * @return Whether arrow scrolling will animate its transition.
     */
    public boolean isSmoothScrollingEnabled() {
        return mSmoothScrollingEnabled;
    }

    /**
     * Set whether arrow scrolling will animate its transition.
     * @param smoothScrollingEnabled whether arrow scrolling will animate its transition
     */
    public void setSmoothScrollingEnabled(boolean smoothScrollingEnabled) {
        mSmoothScrollingEnabled = smoothScrollingEnabled;
    }

    @Override
    protected float getTopFadingEdgeStrength() {
        if (getChildCount() == 0) {
            return 0.0f;
        }

        final int length = getVerticalFadingEdgeLength();
        if (getScrollY() < length) {
            return getScrollY() / (float) length;
        }

        return 1.0f;
    }

    @Override
    protected float getBottomFadingEdgeStrength() {
        if (getChildCount() == 0) {
            return 0.0f;
        }

        final int length = getVerticalFadingEdgeLength();
        final int bottomEdge = getHeight() - getPaddingBottom();
        final int span = getChildAt(0).getBottom() - getScrollY() - bottomEdge;
        if (span < length) {
            return span / (float) length;
        }

        return 1.0f;
    }

    @Override
    protected float getLeftFadingEdgeStrength() {
        if (getChildCount() == 0) {
            return 0.0f;
        }

        final int length = getHorizontalFadingEdgeLength();
        if (getScaleX() < length) {
            return getScaleX() / (float) length;
        }

        return 1.0f;
    }

    @Override
    protected float getRightFadingEdgeStrength() {
        if (getChildCount() == 0) {
            return 0.0f;
        }

        final int length = getHorizontalFadingEdgeLength();
        final int rightEdge = getWidth() - getPaddingRight();
        final int span = getChildAt(0).getRight() - getScrollX() - rightEdge;
        if (span < length) {
            return span / (float) length;
        }

        return 1.0f;
    }

    /**
     * @return The maximum amount this scroll view will scroll horizontally in response to
     *   an arrow event.
     */
    public int getMaxHorizontalScrollAmount() {
        return (int) (MAX_SCROLL_FACTOR * (getRight() - getLeft()));
    }

    /**
     * @return The maximum amount this scroll view will scroll vertically in response to
     *   an arrow event.
     */
    public int getMaxVerticalScrollAmount() {
        return (int) (MAX_SCROLL_FACTOR * (getBottom() - getTop()));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (!mFillViewport) {
            return;
        }

        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if (widthMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.UNSPECIFIED) {
            return;
        }

        if(getChildCount() > 0){
            final View child = getChildAt(0);
            int height = getMeasuredHeight();
            int width = getMeasuredWidth();

            if (child.getMeasuredHeight() < height) {
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec,
                        getPaddingLeft() + getPaddingRight(), lp.width);
                height -= getPaddingTop();
                height -= getPaddingBottom();
                int childHeightMeasureSpec =
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);

                child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            }



            if (child.getMeasuredWidth() < width) {
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                int childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec,
                        getPaddingTop() + getPaddingBottom(), lp.height);
                width -= getPaddingLeft();
                width -= getPaddingRight();
                int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);

                child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            }
        }

    }

    /**
     * You can call this function yourself to have the scroll view perform
     * scrolling from a key event, just as if the event had been dispatched to
     * it by the view hierarchy.
     *
     * @param event The key event to execute.
     * @return Return true if the event was handled, else false.
     */
    public boolean executeKeyEvent(KeyEvent event) {
        mTempRect.setEmpty();

        if (!canScroll()) {
            if (isFocused()) {
                View currentFocused = findFocus();
                if (currentFocused == this) currentFocused = null;
                View nextFocusedDown = FocusFinder.getInstance().findNextFocus(this,
                        currentFocused, View.FOCUS_DOWN);
                View nextFocusedRight = FocusFinder.getInstance().findNextFocus(this,
                        currentFocused, View.FOCUS_RIGHT);
                return (nextFocusedDown != null && nextFocusedDown != this &&
                        nextFocusedDown.requestFocus(View.FOCUS_DOWN)) ||
                        (nextFocusedRight != null && nextFocusedRight != this &&
                                nextFocusedRight.requestFocus(View.FOCUS_RIGHT));
            }
            return false;
        }

        boolean handled = false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (!event.isAltPressed()) {
                        handled = arrowScroll(View.FOCUS_UP);
                    } else {
                        handled = fullScroll(View.FOCUS_UP);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (!event.isAltPressed()) {
                        handled = arrowScroll(View.FOCUS_DOWN);
                    } else {
                        handled = fullScroll(View.FOCUS_DOWN);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (!event.isAltPressed()) {
                        handled = arrowScroll(View.FOCUS_LEFT);
                    } else {
                        handled = fullScroll(View.FOCUS_LEFT);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (!event.isAltPressed()) {
                        handled = arrowScroll(View.FOCUS_RIGHT);
                    } else {
                        handled = fullScroll(View.FOCUS_RIGHT);
                    }
                    break;
                case KeyEvent.KEYCODE_SPACE:
                    pageScroll(event.isShiftPressed() ? View.FOCUS_UP : View.FOCUS_DOWN);
                    //TODO: one of these may not be required
                    pageScroll(event.isShiftPressed() ? View.FOCUS_LEFT : View.FOCUS_RIGHT);
                    break;
            }
        }

        return handled;
    }

    private boolean inChild(int x, int y) {
        if (getChildCount() > 0) {
            final int scrollY = getScrollY();
            final int scrollX = getScrollX();
            final View child = getChildAt(0);
            return (!(y < child.getTop() - scrollY
                    || y >= child.getBottom() - scrollY
                    || x < child.getLeft()
                    || x >= child.getRight())) ||
                    (!(y < child.getTop()
                            || y >= child.getBottom()
                            || x < child.getLeft() - scrollX
                            || x >= child.getRight() - scrollX));
        }
        return false;
    }

    private void initOrResetVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
    }

    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (disallowIntercept) {
            recycleVelocityTracker();
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onMotionEvent will be called and we do the actual
         * scrolling there.
         */

        /*
        * Shortcut the most recurring case: the user is in the dragging
        * state and he is moving his finger.  We want to intercept this
        * motion.
        */
        final int action = ev.getAction();
        if ((action == MotionEvent.ACTION_MOVE) && (mIsBeingDragged)) {
            return true;
        }
        /*
         * Don't try to intercept touch if we can't scroll anyway.
         */
        if (getScrollY() == 0 && (!canScrollVertically(1) || !canScrollHorizontally(1))) {
            return false;
        }

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE: {
                /*
                 * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
                 * whether the user has moved far enough from his original down touch.
                 */

                /*
                * Locally do absolute value. mLastMotionY is set to the y value
                * of the down event.
                */
                final int activePointerId = mActivePointerId;
                if (activePointerId == INVALID_POINTER) {
                    // If we don't have a valid id, the touch down wasn't on content.
                    break;
                }

                final int pointerIndex = ev.findPointerIndex(activePointerId);
                if (pointerIndex == -1) {
                    Log.e(TAG, "Invalid pointerId=" + activePointerId
                            + " in onInterceptTouchEvent");
                    break;
                }

                final int y = (int) ev.getY(pointerIndex);
                final int x = (int) ev.getX(pointerIndex);
                final int yDiff = Math.abs(y - mLastMotionY);
                final int xDiff = Math.abs(x - mLastMotionX);
                if (yDiff > mTouchSlop || (xDiff > mTouchSlop )) {
                    mIsBeingDragged = true;
                    mLastMotionY = y;
                    mLastMotionX = x;
                    initVelocityTrackerIfNotExists();
                    mVelocityTracker.addMovement(ev);

                    final ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                }
                break;
            }

            case MotionEvent.ACTION_DOWN: {
                final int y = (int) ev.getY();
                final int x = (int) ev.getX();
                if (!inChild(x, y)) {
                    mIsBeingDragged = false;
                    recycleVelocityTracker();
                    break;
                }

                /*
                 * Remember location of down touch.
                 * ACTION_DOWN always refers to pointer index 0.
                 */
                mLastMotionY = y;
                mLastMotionX = x;
                mActivePointerId = ev.getPointerId(0);

                initOrResetVelocityTracker();
                mVelocityTracker.addMovement(ev);
                /*
                * If being flinged and user touches the screen, initiate drag;
                * otherwise don't.  mScroller.isFinished should be false when
                * being flinged.
                */
                mIsBeingDragged = !mScroller.isFinished();
//                startNestedScroll(SCROLL_AXIS_VERTICAL);
//                startNestedScroll(SCROLL_AXIS_HORIZONTAL);
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                /* Release the drag */
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                recycleVelocityTracker();
                if (mScroller.springBack(getScrollX(), getScrollY(), 0, getScrollRangeHorizontal(), 0, getScrollRangeVertical())) {
//                    postInvalidateOnAnimation();
                }
//                stopNestedScroll();
                break;
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int index = ev.getActionIndex();
                mLastMotionX = (int) ev.getX(index);
                mLastMotionY = (int) ev.getY(index);
                mActivePointerId = ev.getPointerId(index);
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                mLastMotionX = (int) ev.getX(ev.findPointerIndex(mActivePointerId));
                mLastMotionY = (int) ev.getY(ev.findPointerIndex(mActivePointerId));
                break;
        }

        /*
        * The only time we want to intercept motion events is if we are in the
        * drag mode.
        */
        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        initVelocityTrackerIfNotExists();

        MotionEvent vtev = MotionEvent.obtain(ev);

        final int actionMasked = ev.getActionMasked();
        if (actionMasked == MotionEvent.ACTION_DOWN) {
            mNestedYOffset = 0;
            mNestedXOffset = 0;
        }

        vtev.offsetLocation(mNestedXOffset, mNestedYOffset);

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN: {
                if (getChildCount() == 0) {
                    return false;
                }
                if ((mIsBeingDragged = !mScroller.isFinished())) {
                    final ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                }

                /*
                 * If being flinged and user touches, stop the fling. isFinished
                 * will be false if being flinged.
                 */
                if (!mScroller.isFinished()) {
                    mScroller.abortAnimation();
                }

                // Remember where the motion event started
                mLastMotionY = (int) ev.getY();
                mLastMotionX = (int) ev.getX();
                mActivePointerId = ev.getPointerId(0);
                break;
            }
            case MotionEvent.ACTION_MOVE:{
                final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                if (activePointerIndex == -1) {
                    Log.e(TAG, "Invalid pointerId=" + mActivePointerId + " in onTouchEvent");
                    break;
                }

                final int y = (int) ev.getY(activePointerIndex);
                final int x = (int) ev.getX(activePointerIndex);
                int deltaY = mLastMotionY - y;
                int deltaX = mLastMotionX - x;
//                if (dispatchNestedPreScroll(deltaX, deltaY, mScrollConsumed, mScrollOffset)) {
//                    deltaX -= mScrollConsumed[0];
//                    deltaY -= mScrollConsumed[1];
//                    vtev.offsetLocation(mScrollOffset[0], mScrollOffset[1]);
//                    mNestedYOffset += mScrollOffset[1];
//                    mNestedXOffset += mScrollOffset[0];
//                }
                if (!mIsBeingDragged && Math.abs(deltaY) > mTouchSlop) {
                    final ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                    mIsBeingDragged = true;
                    if (deltaY > 0) {
                        deltaY -= mTouchSlop;
                    } else {
                        deltaY += mTouchSlop;
                    }
                }
                if (!mIsBeingDragged && Math.abs(deltaX) > mTouchSlop) {
                    final ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                    mIsBeingDragged = true;
                    if (deltaX > 0) {
                        deltaX -= mTouchSlop;
                    } else {
                        deltaX += mTouchSlop;
                    }
                }
                if (mIsBeingDragged) {
                    // Scroll to follow the motion event
                    mLastMotionY = y - mScrollOffset[1];
                    mLastMotionX = x;

                    final int oldY = getScrollY();
                    final int oldX = getScrollX();
                    final int verticalRange = getScrollRangeVertical();
                    final int horizontalRange = getScrollRangeHorizontal();
                    final int overScrollMode = getOverScrollMode();
                    boolean canOverScroll = overScrollMode == OVER_SCROLL_ALWAYS ||
                            (overScrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && (verticalRange > 0 || horizontalRange > 0));

                    // Calling overScrollBy will call onOverScrolled, which
                    // calls onScrollChanged if applicable.
                    if (overScrollBy(deltaX, deltaY, getScrollX(), getScrollY(), horizontalRange, verticalRange, mOverscrollDistance, mOverscrollDistance, true)) {
                        // Break our velocity if we hit a scroll barrier.
                        mVelocityTracker.clear();
                    }

                    final int scrolledDeltaY = getScrollY() - oldY;
                    final int unconsumedY = deltaY - scrolledDeltaY;
                    final int scrolledDeltaX = getScrollX() - oldX;
                    final int unconsumedX = deltaX - scrolledDeltaX;
//                    if (dispatchNestedScroll(scrolledDeltaX, scrolledDeltaY, unconsumedX, unconsumedY, mScrollOffset)) {
//                        mLastMotionY -= mScrollOffset[1];
//                        mLastMotionX -= mScrollOffset[0];
//                        vtev.offsetLocation(mScrollOffset[0], mScrollOffset[1]);
//                        mNestedYOffset += mScrollOffset[1];
//                        mNestedXOffset += mScrollOffset[0];
//                    if (canOverScroll) {
//                        final int pulledToY = oldY + deltaY;
//                        final int pulledToX = oldX + deltaX;
//                        if (pulledToY < 0) {
//                            mEdgeGlowTop.onPull((float) deltaY / getHeight(),
//                                    ev.getX(activePointerIndex) / getWidth());
//                            if (!mEdgeGlowBottom.isFinished()) {
//                                mEdgeGlowBottom.onRelease();
//                            }
//                        } else if (pulledToY > verticalRange) {
//                            mEdgeGlowBottom.onPull((float) deltaY / getHeight(),
//                                    1.f - ev.getX(activePointerIndex) / getWidth());
//                            if (!mEdgeGlowTop.isFinished()) {
//                                mEdgeGlowTop.onRelease();
//                            }
//                        }if (pulledToX < 0) {
//                            mEdgeGlowLeft.onPull((float) deltaX / getWidth(),
//                                    1.f - ev.getY(activePointerIndex) / getHeight());
//                            if (!mEdgeGlowRight.isFinished()) {
//                                mEdgeGlowRight.onRelease();
//                            }
//                        } else if (pulledToX > horizontalRange) {
//                            mEdgeGlowRight.onPull((float) deltaX / getWidth(),
//                                    ev.getY(activePointerIndex) / getHeight());
//                            if (!mEdgeGlowLeft.isFinished()) {
//                                mEdgeGlowLeft.onRelease();
//                            }
//                        }
//                        if (mEdgeGlowTop != null
//                                && (!mEdgeGlowTop.isFinished() || !mEdgeGlowBottom.isFinished()) ||
//                                !mEdgeGlowLeft.isFinished() || !mEdgeGlowRight.isFinished()) {
//                            postInvalidateOnAnimation();
//                        }
//                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
                if (mIsBeingDragged) {
                    final VelocityTracker velocityTracker = mVelocityTracker;
                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int initialVelocityY = (int) velocityTracker.getYVelocity(mActivePointerId);
                    int initialVelocityX = (int) velocityTracker.getXVelocity(mActivePointerId);

                    if ((Math.abs(initialVelocityY) > mMinimumVelocity) || (Math.abs(initialVelocityX) > mMinimumVelocity)) {
                        flingWithNestedDispatch(-initialVelocityX, -initialVelocityY);
                    } else if (mScroller.springBack(getScrollX(), getScrollY(), 0, getScrollRangeHorizontal(), 0,
                            getScrollRangeVertical())) {
                        postInvalidateOnAnimation();
                    }

                    mActivePointerId = INVALID_POINTER;
                    endDrag();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mIsBeingDragged && getChildCount() > 0) {
                    if (mScroller.springBack(getScrollX(), getScrollY(), 0, getScrollRangeHorizontal(), 0, getScrollRangeVertical())) {
                        postInvalidateOnAnimation();
                    }
                    mActivePointerId = INVALID_POINTER;
                    endDrag();
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int index = ev.getActionIndex();
                mLastMotionY = (int) ev.getY(index);
                mLastMotionX = (int) ev.getX(index);
                mActivePointerId = ev.getPointerId(index);
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                mLastMotionY = (int) ev.getY(ev.findPointerIndex(mActivePointerId));
                mLastMotionX = (int) ev.getX(ev.findPointerIndex(mActivePointerId));
                break;
        }
        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(vtev);
        }
        vtev.recycle();
        return true;
    }

    /**
     * Fling the scroll view
     *
     * @param velocityX The initial velocity in the X direction. Positive
     *                  numbers mean that the finger/cursor is moving down the screen,
     *                  which means we want to scroll towards the left.
     * @param velocityY The initial velocity in the Y direction. Positive
     *                  numbers mean that the finger/cursor is moving down the screen,
     *                  which means we want to scroll towards the top.
     */
    public void fling(int velocityX, int velocityY) {
        if (getChildCount() > 0) {
            int width = getWidth() - getPaddingRight() - getPaddingLeft();
            int height = getHeight() - getPaddingBottom() - getPaddingTop();
            int right = getChildAt(0).getWidth();
            int bottom = getChildAt(0).getHeight();

            mScroller.fling(getScrollX(), getScrollY(), velocityX, velocityY, 0,
                    Math.max(0, right - width), 0, Math.max(0, bottom - height), width/2, height/2);

//            final boolean movingRight = velocityX > 0;
//
//            View currentFocused = findFocus();
//            View newFocused = findFocusableViewInMyBounds(movingRight,
//                    mScroller.getFinalX(), currentFocused);
//
//            if (newFocused == null) {
//                newFocused = this;
//            }
//
//            if (newFocused != currentFocused) {
//                newFocused.requestFocus(movingRight ? View.FOCUS_RIGHT : View.FOCUS_LEFT);
//            }

            postInvalidateOnAnimation();
        }
    }

    private void flingWithNestedDispatch(int velocityX, int velocityY) {
        final boolean canFlingVertical = (getScrollY() > 0 || velocityY > 0) &&
                (getScrollY() < getScrollRangeVertical() || velocityY < 0);
        final boolean canFlingHorizontal = (getScrollX() > 0 || velocityX > 0) &&
                (getScrollX() < getScrollRangeHorizontal() || velocityX < 0);
        if (canFlingVertical || canFlingHorizontal) {
            fling(velocityX, velocityY);
        }
    }

    private void endDrag() {
        mIsBeingDragged = false;

        recycleVelocityTracker();

        if (mEdgeGlowTop != null) {
            mEdgeGlowTop.onRelease();
        }
        if(mEdgeGlowBottom != null){
            mEdgeGlowBottom.onRelease();
        }
        if(mEdgeGlowLeft != null){
            mEdgeGlowLeft.onRelease();
        }
        if(mEdgeGlowRight != null){
            mEdgeGlowRight.onRelease();
        }
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            // TODO: Make this decision more intelligent.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionX = (int) ev.getX(newPointerIndex);
            mLastMotionY = (int) ev.getY(newPointerIndex);
            mActivePointerId = ev.getPointerId(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_SCROLL: {
                    if (!mIsBeingDragged) {
                        final float vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                        final float hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
                        int newScrollY = Integer.MAX_VALUE;
                        int newScrollX = Integer.MAX_VALUE;
                        boolean newVertical = false;
                        boolean newHorizontal = false;

                        if (vScroll != 0) {
                            final int delta = (int) (vScroll * getVerticalScrollFactor());
                            final int range = getScrollRangeVertical();
                            int oldScrollY = getScrollY();
                            newScrollY = oldScrollY - delta;
                            if (newScrollY < 0) {
                                newScrollY = 0;
                            } else if (newScrollY > range) {
                                newScrollY = range;
                            }
                            if (newScrollY != oldScrollY) {
                                newVertical = true;
                            }
                        }
                        if (hScroll != 0) {
                            final int delta = (int) (hScroll * getHorizontalScrollFactor());
                            final int range = getScrollRangeHorizontal();
                            int oldScrollX = getScrollX();
                            newScrollX = oldScrollX + delta;
                            if (newScrollX < 0) {
                                newScrollX = 0;
                            } else if (newScrollX > range) {
                                newScrollX = range;
                            }
                            if (newScrollX != oldScrollX) {
                                newHorizontal = true;
                            }
                        }
                        if (newVertical && newHorizontal) {
                            super.scrollTo(newScrollX, newScrollY);
                            return true;
                        }else if(newVertical){
                            super.scrollTo(getScrollX(), newScrollY);
                            return true;
                        }else if(newHorizontal){
                            super.scrollTo(newScrollX, getScrollY());
                            return true;
                        }
                    }
                }
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY,
                                  boolean clampedX, boolean clampedY) {
        // Treat animating scrolls differently; see #computeScroll() for why.
        if (!mScroller.isFinished()) {
            final int oldX = getScrollX();
            final int oldY = getScrollY();
            setScrollX(scrollX);
            setScrollY(scrollY);
            invalidateParentIfNeeded();
            onScrollChanged(getScrollX(), getScrollY(), oldX, oldY);
            if (clampedY) {
                mScroller.springBack(getScrollX(), getScrollY(), 0, getScrollRangeHorizontal(), 0, getScrollRangeVertical());
            }
        } else {
            super.scrollTo(scrollX, scrollY);
        }

        awakenScrollBars();
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (super.performAccessibilityAction(action, arguments)) {
            return true;
        }
        if (!isEnabled()) {
            return false;
        }
        switch (action) {
            case AccessibilityNodeInfo.ACTION_SCROLL_FORWARD: {
                final int viewportHeight = getHeight() - getPaddingBottom() - getPaddingTop();
                final int targetScrollY = (int) Math.min(getScaleY() + viewportHeight, getScrollRangeVertical());

                final int viewportWidth = getWidth() - getPaddingLeft() - getPaddingRight();
                final int targetScrollX = (int) Math.min(getScaleX() + viewportWidth, getScrollRangeHorizontal());
                if(targetScrollY != getScrollY() && targetScrollX != getScrollX()){
                    smoothScrollTo(targetScrollX, targetScrollY);
                    return true;
                }
                if (targetScrollY != getScrollY()) {
                    smoothScrollTo(0, targetScrollY);
                    return true;
                }
                if (targetScrollX != getScrollX()) {
                    smoothScrollTo(targetScrollX, 0);
                    return true;
                }
            } return false;
            case AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD: {
                final int viewportHeight = getHeight() - getPaddingBottom() - getPaddingTop();
                final int targetScrollY = (int) Math.max(getScaleY() - viewportHeight, 0);

                final int viewportWidth = getWidth() - getPaddingLeft() - getPaddingRight();
                final int targetScrollX = Math.max(0, getScrollX() - viewportWidth);
                if(targetScrollY != getScrollY() && targetScrollX != getScrollX()){
                    smoothScrollTo(targetScrollX, targetScrollY);
                    return true;
                }
                if (targetScrollY != getScrollY()) {
                    smoothScrollTo(0, targetScrollY);
                    return true;
                }
                if (targetScrollX != getScrollX()) {
                    smoothScrollTo(targetScrollX, 0);
                    return true;
                }
            } return false;
        }
        return false;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(DroidUIScrollView.class.getName());
        if (isEnabled()) {
            final int verticalScrollRange = getScrollRangeVertical();
            final int horizontalScrollRange = getScrollRangeHorizontal();
            if (verticalScrollRange > 0 || horizontalScrollRange > 0) {
                info.setScrollable(true);
                if (getScrollY() > 0 || getScrollX() > 0) {
                    info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
                }
                if (getScrollY() < verticalScrollRange || getScrollX() < horizontalScrollRange) {
                    info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                }
            }
        }
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(DroidUIScrollView.class.getName());
        final boolean scrollable = getScrollRangeVertical() > 0 || getScrollRangeVertical() > 0;
        event.setScrollable(scrollable);
        event.setScrollX(getScrollX());
        event.setScrollY(getScrollY());
        event.setMaxScrollX(getScrollRangeHorizontal());
        event.setMaxScrollY(getScrollRangeVertical());
    }

    private int getScrollRangeVertical() {
        int scrollRange = 0;
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            scrollRange = Math.max(0,
                    child.getHeight() - (getHeight() - getPaddingBottom() - getPaddingTop()));
        }
        return scrollRange;
    }

    private int getScrollRangeHorizontal() {
        int scrollRange = 0;
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            scrollRange = Math.max(0,
                    child.getWidth() - (getWidth() - getPaddingLeft() - getPaddingRight()));
        }
        return scrollRange;
    }



    /**
     * Handle scrolling in response to an arrow click.
     *
     * @param direction The direction corresponding to the arrow key that was
     *                  pressed
     * @return True if we consumed the event, false otherwise
     */
    public boolean arrowScroll(int direction) {

        View currentFocused = findFocus();
        if (currentFocused == this) currentFocused = null;

        View nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused, direction);

        int maxJump;
        switch (direction){
            case View.FOCUS_DOWN:
            case View.FOCUS_UP:
                maxJump = getMaxVerticalScrollAmount();

                if (nextFocused != null && isWithinDeltaOfScreenVertically(nextFocused, maxJump, getHeight())) {
                    nextFocused.getDrawingRect(mTempRect);
                    offsetDescendantRectToMyCoords(nextFocused, mTempRect);
                    int scrollDelta = computeScrollDeltaToGetChildRectOnScreenVertically(mTempRect);
                    doScrollY(scrollDelta);
                    nextFocused.requestFocus(direction);
                } else {
                    // no new focus
                    int scrollDelta = maxJump;

                    if (direction == View.FOCUS_UP && getScrollY() < scrollDelta) {
                        scrollDelta = getScrollY();
                    } else if (direction == View.FOCUS_DOWN) {
                        if (getChildCount() > 0) {
                            int daBottom = getChildAt(0).getBottom();
                            int screenBottom = getScrollY() + getHeight() - getPaddingBottom();
                            if (daBottom - screenBottom < maxJump) {
                                scrollDelta = daBottom - screenBottom;
                            }
                        }
                    }
                    if (scrollDelta == 0) {
                        return false;
                    }
                    doScrollY(direction == View.FOCUS_DOWN ? scrollDelta : -scrollDelta);
                }

                if (currentFocused != null && currentFocused.isFocused()
                        && isOffScreenVertically(currentFocused)) {
                    // previously focused item still has focus and is off screen, give
                    // it up (take it back to ourselves)
                    // (also, need to temporarily force FOCUS_BEFORE_DESCENDANTS so we are
                    // sure to
                    // get it)
                    final int descendantFocusability = getDescendantFocusability();  // save
                    setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
                    requestFocus();
                    setDescendantFocusability(descendantFocusability);  // restore
                }
                break;
            case View.FOCUS_LEFT:
            case View.FOCUS_RIGHT:
                maxJump = getMaxHorizontalScrollAmount();

                if (nextFocused != null && isWithinDeltaOfScreenHorizontally(nextFocused, maxJump)) {
                    nextFocused.getDrawingRect(mTempRect);
                    offsetDescendantRectToMyCoords(nextFocused, mTempRect);
                    int scrollDelta = computeScrollDeltaToGetChildRectOnScreenHorizontally(mTempRect);
                    doScrollX(scrollDelta);
                    nextFocused.requestFocus(direction);
                } else {
                    // no new focus
                    int scrollDelta = maxJump;

                    if (direction == View.FOCUS_LEFT && getScrollX() < scrollDelta) {
                        scrollDelta = getScrollX();
                    } else if (direction == View.FOCUS_RIGHT && getChildCount() > 0) {

                        int daRight = getChildAt(0).getRight();

                        int screenRight = getScrollX() + getWidth();

                        if (daRight - screenRight < maxJump) {
                            scrollDelta = daRight - screenRight;
                        }
                    }
                    if (scrollDelta == 0) {
                        return false;
                    }
                    doScrollX(direction == View.FOCUS_RIGHT ? scrollDelta : -scrollDelta);
                }

                if (currentFocused != null && currentFocused.isFocused()
                        && isOffScreenHorizontally(currentFocused)) {
                    // previously focused item still has focus and is off screen, give
                    // it up (take it back to ourselves)
                    // (also, need to temporarily force FOCUS_BEFORE_DESCENDANTS so we are
                    // sure to
                    // get it)
                    final int descendantFocusability = getDescendantFocusability();  // save
                    setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
                    requestFocus();
                    setDescendantFocusability(descendantFocusability);  // restore
                }
        }
        return true;
    }


    /**
     * @return whether the descendant of this scroll view is scrolled off
     *  screen Vertically.
     */
    private boolean isOffScreenVertically(View descendant) {
        return !isWithinDeltaOfScreenVertically(descendant, 0, getHeight());
    }

    /**
     * @return whether the descendant of this scroll view is scrolled off
     *  screen Horizontally.
     */
    private boolean isOffScreenHorizontally(View descendant) {
        return !isWithinDeltaOfScreenHorizontally(descendant, 0);
    }

    /**
     * @return whether the descendant of this scroll view is within delta
     *  pixels of being on the screen.
     */
    private boolean isWithinDeltaOfScreenVertically(View descendant, int delta, int height) {
        descendant.getDrawingRect(mTempRect);
        offsetDescendantRectToMyCoords(descendant, mTempRect);

        return (mTempRect.bottom + delta) >= getScrollY()
                && (mTempRect.top - delta) <= (getScrollY() + height);
    }

    /**
     * @return whether the descendant of this scroll view is within delta
     *  pixels of being on the screen Horizontally.
     */
    private boolean isWithinDeltaOfScreenHorizontally(View descendant, int delta) {
        descendant.getDrawingRect(mTempRect);
        offsetDescendantRectToMyCoords(descendant, mTempRect);

        return (mTempRect.right + delta) >= getScrollX()
                && (mTempRect.left - delta) <= (getScrollX() + getWidth());
    }

    /**
     * Smooth scroll by a Y delta
     *
     * @param delta the number of pixels to scroll by on the Y axis
     */
    private void doScrollY(int delta) {
        if (delta != 0) {
            if (mSmoothScrollingEnabled) {
                smoothScrollBy(0, delta);
            } else {
                scrollBy(0, delta);
            }
        }
    }

    /**
     * Smooth scroll by a X delta
     *
     * @param delta the number of pixels to scroll by on the X axis
     */
    private void doScrollX(int delta) {
        if (delta != 0) {
            if (mSmoothScrollingEnabled) {
                smoothScrollBy(delta, 0);
            } else {
                scrollBy(delta, 0);
            }
        }
    }


    /**
     * Like {@link android.view.View#scrollBy}, but scroll smoothly instead of immediately.
     *
     * @param dx the number of pixels to scroll by on the X axis
     * @param dy the number of pixels to scroll by on the Y axis
     */
    public final void smoothScrollBy(int dx, int dy) {
        if (getChildCount() == 0) {
            // Nothing to do.
            return;
        }
        long duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll;
        if (duration > ANIMATED_SCROLL_GAP) {
            final int height = getHeight() - getPaddingBottom() - getPaddingTop();
            final int width = getWidth() - getPaddingRight() - getPaddingLeft();
            final int bottom = getChildAt(0).getHeight();
            final int right = getChildAt(0).getWidth();
            final int maxY = Math.max(0, bottom - height);
            final int maxX = Math.max(0, right - width);
            final int scrollY = getScrollY();
            final int scrollX = getScrollX();
            dy = Math.max(0, Math.min(scrollY + dy, maxY)) - scrollY;
            dx = Math.max(0, Math.min(scrollX + dx, maxX)) - scrollX;

            mScroller.startScroll(scrollX, scrollY, dx, dy);
//            postInvalidateOnAnimation();
        } else {
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
            }
            scrollBy(dx, dy);
        }
        mLastScroll = AnimationUtils.currentAnimationTimeMillis();
    }

    /**
     * Like {@link #scrollTo}, but scroll smoothly instead of immediately.
     *
     * @param x the position where to scroll on the X axis
     * @param y the position where to scroll on the Y axis
     */
    public final void smoothScrollTo(int x, int y) {
        smoothScrollBy(x - getScrollX(), y - getScrollY());
    }

    /**
     * <p>The scroll range of a scroll view is the overall height of all of its
     * children.</p>
     */
    @Override
    protected int computeVerticalScrollRange() {
        final int count = getChildCount();
        final int contentHeight = getHeight() - getPaddingBottom() - getPaddingTop();
        if (count == 0) {
            return contentHeight;
        }

        int scrollRange = getChildAt(0).getBottom();
        final int scrollY = getScrollY();
        final int overScrollBottom = Math.max(0, scrollRange - contentHeight);
        if (scrollY < 0) {
            scrollRange -= scrollY;
        } else if (scrollY > overScrollBottom) {
            scrollRange += scrollY - overScrollBottom;
        }

        return scrollRange;
    }

    /**
     * <p>The scroll range of a scroll view is the overall width of all of its
     * children.</p>
     */
    @Override
    protected int computeHorizontalScrollRange() {
        final int count = getChildCount();
        final int contentWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        if (count == 0) {
            return contentWidth;
        }

        int scrollRange = getChildAt(0).getRight();
        final int scrollX = getScrollX();
        final int overScrollRight = Math.max(0, scrollRange - contentWidth);
        if (scrollX < 0) {
            scrollRange -= scrollX;
        } else if (scrollX > overScrollRight) {
            scrollRange += scrollX - overScrollRight;
        }

        return scrollRange;
    }

    @Override
    protected int computeVerticalScrollOffset() {
        return Math.max(0, super.computeVerticalScrollOffset());
    }

    @Override
    protected int computeHorizontalScrollOffset() {
        return Math.max(0, super.computeHorizontalScrollOffset());
    }

    @Override
    protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
        ViewGroup.LayoutParams lp = child.getLayoutParams();

        int childWidthMeasureSpec;
        int childHeightMeasureSpec;

        childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec, getPaddingLeft()
                + getPaddingRight(), lp.width);


        childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec, getPaddingTop()
                + getPaddingBottom(), lp.height);

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
                                           int parentHeightMeasureSpec, int heightUsed) {
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();

        final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
                getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin
                        + widthUsed, lp.width);


        final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
                getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin
                        + heightUsed, lp.height);

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            // This is called at drawing time by ViewGroup.  We don't want to
            // re-show the scrollbars at this point, which scrollTo will do,
            // so we replicate most of scrollTo here.
            //
            //         It's a little odd to call onScrollChanged from inside the drawing.
            //
            //         It is, except when you remember that computeScroll() is used to
            //         animate scrolling. So unless we want to defer the onScrollChanged()
            //         until the end of the animated scrolling, we don't really have a
            //         choice here.
            //
            //         I agree.  The alternative, which I think would be worse, is to post
            //         something and tell the subclasses later.  This is bad because there
            //         will be a window where mScrollX/Y is different from what the app
            //         thinks it is.
            //
            int oldX = getScrollX();
            int oldY = getScrollY();
            int x = mScroller.getCurrX();
            int y = mScroller.getCurrY();

            if (oldX != x || oldY != y) {
                final int verticalRange = getScrollRangeVertical();
                final int horizontalRange = getScrollRangeHorizontal();
                final int overscrollMode = getOverScrollMode();
                final boolean canOverscroll = overscrollMode == OVER_SCROLL_ALWAYS ||
                        (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && (verticalRange > 0 || horizontalRange > 0));

                overScrollBy(x - oldX, y - oldY, oldX, oldY, horizontalRange, verticalRange,
                        mOverflingDistance, mOverflingDistance, false);
                onScrollChanged(getScrollX(), getScrollY(), oldX, oldY);

                if (canOverscroll) {
                    if (y < 0 && oldY >= 0) {
                        mEdgeGlowTop.onAbsorb((int) mScroller.getCurrVelocity());
                    } else if (y > verticalRange && oldY <= verticalRange) {
                        mEdgeGlowBottom.onAbsorb((int) mScroller.getCurrVelocity());
                    }
                    if (x < 0 && oldX >= 0) {
                        mEdgeGlowLeft.onAbsorb((int) mScroller.getCurrVelocity());
                    } else if (x > horizontalRange && oldX <= horizontalRange) {
                        mEdgeGlowRight.onAbsorb((int) mScroller.getCurrVelocity());
                    }
                }
            }

            if (!awakenScrollBars()) {
                // Keep on drawing until the animation has finished.
                postInvalidateOnAnimation();
            }
        }
    }

    /**
     * Scrolls the view to the given child.
     *
     * @param child the View to scroll to
     */
    private void scrollToChild(View child) {
        child.getDrawingRect(mTempRect);

        /* Offset from child's local coordinates to ScrollView coordinates */
        offsetDescendantRectToMyCoords(child, mTempRect);

        int scrollDeltaVertical = computeScrollDeltaToGetChildRectOnScreenVertically(mTempRect);
        int scrollDeltaHorizontal = computeScrollDeltaToGetChildRectOnScreenHorizontally(mTempRect);

        if (scrollDeltaVertical != 0 && scrollDeltaHorizontal != 0) {
            scrollBy(scrollDeltaHorizontal, scrollDeltaVertical);
            return;
        }
        if(scrollDeltaVertical != 0){
            scrollBy(0, scrollDeltaVertical);
            return;
        }
        if(scrollDeltaHorizontal != 0){
            scrollBy(scrollDeltaHorizontal, 0);
        }
    }

    /**
     * If rect is off screen, scroll just enough to get it (or at least the
     * first screen size chunk of it) on screen.
     *
     * @param rect      The rectangle.
     * @param immediate True to scroll immediately without animation
     * @return true if scrolling was performed
     */
    private boolean scrollToChildRect(Rect rect, boolean immediate) {
        final int deltaHorizontal = computeScrollDeltaToGetChildRectOnScreenHorizontally(rect);
        final int deltaVertical = computeScrollDeltaToGetChildRectOnScreenVertically(rect);
        final boolean scroll = deltaHorizontal != 0 || deltaVertical != 0;
        if (scroll) {
            if (immediate) {
                if(deltaHorizontal != 0 && deltaVertical != 0){
                    scrollBy(deltaHorizontal, deltaVertical);
                    return scroll;
                }
                if(deltaHorizontal != 0){
                    scrollBy(deltaHorizontal, 0);
                    return scroll;
                }
                if(deltaVertical != 0){
                    scrollBy(0, deltaVertical);
                    return scroll;
                }
            } else {
                if(deltaHorizontal != 0 && deltaVertical != 0){
                    smoothScrollBy(deltaHorizontal, deltaVertical);
                    return scroll;
                }
                if(deltaHorizontal != 0){
                    smoothScrollBy(deltaHorizontal, 0);
                    return scroll;
                }
                if(deltaVertical != 0){
                    smoothScrollBy(0, deltaVertical);
                    return scroll;
                }
            }
        }
        return scroll;
    }

    /**
     * Compute the amount to scroll in the Y direction in order to get
     * a rectangle completely on the screen (or, if taller than the screen,
     * at least the first screen size chunk of it).
     *
     * @param rect The rect.
     * @return The scroll delta.
     */
    protected int computeScrollDeltaToGetChildRectOnScreenVertically(Rect rect) {
        if (getChildCount() == 0) return 0;

        int height = getHeight();
        int screenTop = getScrollY();
        int screenBottom = screenTop + height;

        int fadingEdge = getVerticalFadingEdgeLength();

        // leave room for top fading edge as long as rect isn't at very top
        if (rect.top > 0) {
            screenTop += fadingEdge;
        }

        // leave room for bottom fading edge as long as rect isn't at very bottom
        if (rect.bottom < getChildAt(0).getHeight()) {
            screenBottom -= fadingEdge;
        }

        int scrollYDelta = 0;

        if (rect.bottom > screenBottom && rect.top > screenTop) {
            // need to move down to get it in view: move down just enough so
            // that the entire rectangle is in view (or at least the first
            // screen size chunk).

            if (rect.height() > height) {
                // just enough to get screen size chunk on
                scrollYDelta += (rect.top - screenTop);
            } else {
                // get entire rect at bottom of screen
                scrollYDelta += (rect.bottom - screenBottom);
            }

            // make sure we aren't scrolling beyond the end of our content
            int bottom = getChildAt(0).getBottom();
            int distanceToBottom = bottom - screenBottom;
            scrollYDelta = Math.min(scrollYDelta, distanceToBottom);

        } else if (rect.top < screenTop && rect.bottom < screenBottom) {
            // need to move up to get it in view: move up just enough so that
            // entire rectangle is in view (or at least the first screen
            // size chunk of it).

            if (rect.height() > height) {
                // screen size chunk
                scrollYDelta -= (screenBottom - rect.bottom);
            } else {
                // entire rect at top
                scrollYDelta -= (screenTop - rect.top);
            }

            // make sure we aren't scrolling any further than the top our content
            scrollYDelta = Math.max(scrollYDelta, -getScrollY());
        }
        return scrollYDelta;
    }

    /**
     * Compute the amount to scroll in the X direction in order to get
     * a rectangle completely on the screen (or, if taller than the screen,
     * at least the first screen size chunk of it).
     *
     * @param rect The rect.
     * @return The scroll delta.
     */
    protected int computeScrollDeltaToGetChildRectOnScreenHorizontally(Rect rect) {
        if (getChildCount() == 0) return 0;

        int width = getWidth();
        int screenLeft = getScrollX();
        int screenRight = screenLeft + width;

        int fadingEdge = getHorizontalFadingEdgeLength();

        // leave room for left fading edge as long as rect isn't at very left
        if (rect.left > 0) {
            screenLeft += fadingEdge;
        }

        // leave room for right fading edge as long as rect isn't at very right
        if (rect.right < getChildAt(0).getWidth()) {
            screenRight -= fadingEdge;
        }

        int scrollXDelta = 0;

        if (rect.right > screenRight && rect.left > screenLeft) {
            // need to move right to get it in view: move right just enough so
            // that the entire rectangle is in view (or at least the first
            // screen size chunk).

            if (rect.width() > width) {
                // just enough to get screen size chunk on
                scrollXDelta += (rect.left - screenLeft);
            } else {
                // get entire rect at right of screen
                scrollXDelta += (rect.right - screenRight);
            }

            // make sure we aren't scrolling beyond the end of our content
            int right = getChildAt(0).getRight();
            int distanceToRight = right - screenRight;
            scrollXDelta = Math.min(scrollXDelta, distanceToRight);

        } else if (rect.left < screenLeft && rect.right < screenRight) {
            // need to move right to get it in view: move right just enough so that
            // entire rectangle is in view (or at least the first screen
            // size chunk of it).

            if (rect.width() > width) {
                // screen size chunk
                scrollXDelta -= (screenRight - rect.right);
            } else {
                // entire rect at left
                scrollXDelta -= (screenLeft - rect.left);
            }

            // make sure we aren't scrolling any further than the left our content
            scrollXDelta = Math.max(scrollXDelta, -getScrollX());
        }
        return scrollXDelta;
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        if (!mIsLayoutDirty) {
            scrollToChild(focused);
        } else {
            // The child may not be laid out yet, we can't compute the scroll yet
            mChildToScrollTo = focused;
        }
        super.requestChildFocus(child, focused);
    }

    /**
     * When looking for focus in children of a scroll view, need to be a little
     * more careful not to give focus to something that is scrolled off screen.
     *
     * This is more expensive than the default {@link android.view.ViewGroup}
     * implementation, otherwise this behavior might have been made the default.
     */
    @Override
    protected boolean onRequestFocusInDescendants(int direction,
                                                  Rect previouslyFocusedRect) {

        // convert from forward / backward notation to up / down / left / right
        // (ugh).
        if (direction == View.FOCUS_FORWARD) {
            direction = View.FOCUS_DOWN;
        } else if (direction == View.FOCUS_BACKWARD) {
            direction = View.FOCUS_UP;
        }

        final View nextFocus = previouslyFocusedRect == null ?
                FocusFinder.getInstance().findNextFocus(this, null, direction) :
                FocusFinder.getInstance().findNextFocusFromRect(this,
                        previouslyFocusedRect, direction);

        if (nextFocus == null) {
            return false;
        }

        if (isOffScreenVertically(nextFocus) || isOffScreenHorizontally(nextFocus)) {
            return false;
        }

        return nextFocus.requestFocus(direction, previouslyFocusedRect);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle,
                                                 boolean immediate) {
        // offset into coordinate space of this scroll view
        rectangle.offset(child.getLeft() - child.getScrollX(),
                child.getTop() - child.getScrollY());

        return scrollToChildRect(rectangle, immediate);
    }

    @Override
    public void requestLayout() {
        mIsLayoutDirty = true;
        super.requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mIsLayoutDirty = false;
        // Give a child focus if it needs it
        if (mChildToScrollTo != null && isViewDescendantOf(mChildToScrollTo, this)) {
            scrollToChild(mChildToScrollTo);
        }
        mChildToScrollTo = null;

        if (mSavedState != null) {
            setScrollX(mSavedState.scrollPosition[0]);
            setScrollY(mSavedState.scrollPosition[1]);
            mSavedState = null;
        }
        final int childHeight = (getChildCount() > 0) ? getChildAt(0).getMeasuredHeight() : 0;
        final int childWidth = (getChildCount() > 0) ? getChildAt(0).getMeasuredWidth() : 0;
        final int verticalScrollRange = Math.max(0,
                childHeight - (b - t - getPaddingBottom() - getPaddingTop()));
        final int horizontalScrollRange = Math.max(0,
                childWidth - (r - l - getPaddingLeft() - getPaddingRight()));
        // Don't forget to clamp
        if (getScrollY() > verticalScrollRange) {
            setScrollY(verticalScrollRange);
        } else if (getScrollY() < 0) {
            setScrollY(0);
        }
        if (getScrollX() > horizontalScrollRange) {
            setScrollX(horizontalScrollRange);
        } else if (getScrollX() < 0) {
            setScrollX(0);
        }

        // Calling this with the present values causes it to re-claim them
        scrollTo(getScrollX(), getScrollY());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        View currentFocused = findFocus();
        if (null == currentFocused || this == currentFocused)
            return;

        // If the currently-focused view was visible on the screen when the
        // screen was at the old height, then scroll the screen to make that
        // view visible with the new screen height.
        if (isWithinDeltaOfScreenVertically(currentFocused, 0, oldh)) {
            currentFocused.getDrawingRect(mTempRect);
            offsetDescendantRectToMyCoords(currentFocused, mTempRect);
            int scrollDelta = computeScrollDeltaToGetChildRectOnScreenVertically(mTempRect);
            doScrollY(scrollDelta);
        }


        final int maxJump = getRight() - getLeft();

        if (isWithinDeltaOfScreenHorizontally(currentFocused, maxJump)) {
            currentFocused.getDrawingRect(mTempRect);
            offsetDescendantRectToMyCoords(currentFocused, mTempRect);
            int scrollDelta = computeScrollDeltaToGetChildRectOnScreenHorizontally(mTempRect);
            doScrollX(scrollDelta);
        }
    }

    /**
     * Return true if child is a descendant of parent, (or equal to the parent).
     */
    private static boolean isViewDescendantOf(View child, View parent) {
        if (child == parent) {
            return true;
        }

        final ViewParent theParent = child.getParent();
        return (theParent instanceof ViewGroup) && isViewDescendantOf((View) theParent, parent);
    }

    @Override
    public void setOverScrollMode(int mode) {
        if (mode != OVER_SCROLL_NEVER) {
            if (mEdgeGlowTop == null) {
                Context context = getContext();
                mEdgeGlowTop = new EdgeEffect(context);
                mEdgeGlowBottom = new EdgeEffect(context);
            }
            if (mEdgeGlowLeft == null) {
                Context context = getContext();
                mEdgeGlowLeft = new EdgeEffect(context);
                mEdgeGlowRight = new EdgeEffect(context);
            }
        } else {
            mEdgeGlowTop = null;
            mEdgeGlowBottom = null;
            mEdgeGlowLeft = null;
            mEdgeGlowRight = null;
        }
        super.setOverScrollMode(mode);
    }


    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (mEdgeGlowTop != null) {
            final int scrollY = getScrollY();
            if (!mEdgeGlowTop.isFinished()) {
                final int restoreCount = canvas.save();
                final int width = getWidth() - getPaddingLeft() - getPaddingRight();

                canvas.translate(getPaddingLeft(), Math.min(0, scrollY));
                mEdgeGlowTop.setSize(width, getHeight());
                if (mEdgeGlowTop.draw(canvas)) {
                    postInvalidateOnAnimation();
                }
                canvas.restoreToCount(restoreCount);
            }
            if (!mEdgeGlowBottom.isFinished()) {
                final int restoreCount = canvas.save();
                final int width = getWidth() - getPaddingLeft() - getPaddingRight();
                final int height = getHeight();

                canvas.translate(-width + getPaddingLeft(),
                        Math.max(getScrollRangeVertical(), scrollY) + height);
                canvas.rotate(180, width, 0);
                mEdgeGlowBottom.setSize(width, height);
                if (mEdgeGlowBottom.draw(canvas)) {
                    postInvalidateOnAnimation();
                }
                canvas.restoreToCount(restoreCount);
            }
        }

        if (mEdgeGlowLeft != null) {
            final int scrollX = getScrollX();
            if (!mEdgeGlowLeft.isFinished()) {
                final int restoreCount = canvas.save();
                final int height = getHeight() - getPaddingTop() - getPaddingBottom();

                canvas.rotate(270);
                canvas.translate(-height + getPaddingTop(), Math.min(0, scrollX));
                mEdgeGlowLeft.setSize(height, getWidth());
                if (mEdgeGlowLeft.draw(canvas)) {
                    postInvalidateOnAnimation();
                }
                canvas.restoreToCount(restoreCount);
            }
            if (!mEdgeGlowRight.isFinished()) {
                final int restoreCount = canvas.save();
                final int width = getWidth();
                final int height = getHeight() - getPaddingTop() - getPaddingBottom();

                canvas.rotate(90);
                canvas.translate(-getPaddingTop(),
                        -(Math.max(getScrollRangeHorizontal(), scrollX) + width));
                mEdgeGlowRight.setSize(height, width);
                if (mEdgeGlowRight.draw(canvas)) {
                    postInvalidateOnAnimation();
                }
                canvas.restoreToCount(restoreCount);
            }
        }
    }

    private static int clamp(int n, int my, int child) {
        if (my >= child || n < 0) {
            /* my >= child is this case:
             *                    |--------------- me ---------------|
             *     |------ child ------|
             * or
             *     |--------------- me ---------------|
             *            |------ child ------|
             * or
             *     |--------------- me ---------------|
             *                                  |------ child ------|
             *
             * n < 0 is this case:
             *     |------ me ------|
             *                    |-------- child --------|
             *     |-- mScrollX --|
             */
            return 0;
        }
        if ((my+n) > child) {
            /* this case:
             *                    |------ me ------|
             *     |------ child ------|
             *     |-- mScrollX --|
             */
            return child-my;
        }
        return n;
    }

    /**
     * <p>Handles scrolling in response to a "home/end" shortcut press. This
     * method will scroll the view to the top or bottom and give the focus
     * to the topmost/bottommost component in the new visible area. If no
     * component is a good candidate for focus, this scrollview reclaims the
     * focus.</p>
     *
     * @param direction the scroll direction: {@link android.view.View#FOCUS_UP}
     *                  to go the top of the view or
     *                  {@link android.view.View#FOCUS_DOWN} to go the bottom
     * @return true if the key event is consumed by this method, false otherwise
     */
    public boolean fullScroll(int direction) {
        switch (direction){
            case View.FOCUS_UP:
            case View.FOCUS_DOWN:
                boolean down = direction == View.FOCUS_DOWN;
                int height = getHeight();

                mTempRect.top = 0;
                mTempRect.bottom = height;

                if (down) {
                    int count = getChildCount();
                    if (count > 0) {
                        View view = getChildAt(count - 1);
                        mTempRect.bottom = view.getBottom() + getPaddingBottom();
                        mTempRect.top = mTempRect.bottom - height;
                    }
                }

                return scrollAndFocusVertically(direction, mTempRect.top, mTempRect.bottom);
            case View.FOCUS_LEFT:
            case View.FOCUS_RIGHT:
                boolean right = direction == View.FOCUS_RIGHT;
                int width = getWidth();

                mTempRect.left = 0;
                mTempRect.right = width;

                if (right) {
                    int count = getChildCount();
                    if (count > 0) {
                        View view = getChildAt(0);
                        mTempRect.right = view.getRight();
                        mTempRect.left = mTempRect.right - width;
                    }
                }

                return scrollAndFocusHorizontally(direction, mTempRect.left, mTempRect.right);
        }
        return true;
    }

    /**
     * <p>Scrolls the view to make the area defined by <code>top</code> and
     * <code>bottom</code> visible. This method attempts to give the focus
     * to a component visible in this area. If no component can be focused in
     * the new visible area, the focus is reclaimed by this ScrollView.</p>
     *
     * @param direction the scroll direction: {@link android.view.View#FOCUS_UP}
     *                  to go upward, {@link android.view.View#FOCUS_DOWN} to downward
     * @param top       the top offset of the new area to be made visible
     * @param bottom    the bottom offset of the new area to be made visible
     * @return true if the key event is consumed by this method, false otherwise
     */
    private boolean scrollAndFocusVertically(int direction, int top, int bottom) {
        boolean handled = true;

        int height = getHeight();
        int containerTop = getScrollY();
        int containerBottom = containerTop + height;
        boolean up = direction == View.FOCUS_UP;

        View newFocused = findFocusableViewInBoundsVertically(up, top, bottom);
        if (newFocused == null) {
            newFocused = this;
        }

        if (top >= containerTop && bottom <= containerBottom) {
            handled = false;
        } else {
            int delta = up ? (top - containerTop) : (bottom - containerBottom);
            doScrollY(delta);
        }

        if (newFocused != findFocus()) newFocused.requestFocus(direction);

        return handled;
    }

    /**
     * <p>Scrolls the view to make the area defined by <code>left</code> and
     * <code>right</code> visible. This method attempts to give the focus
     * to a component visible in this area. If no component can be focused in
     * the new visible area, the focus is reclaimed by this scrollview.</p>
     *
     * @param direction the scroll direction: {@link android.view.View#FOCUS_LEFT}
     *                  to go left {@link android.view.View#FOCUS_RIGHT} to right
     * @param left     the left offset of the new area to be made visible
     * @param right    the right offset of the new area to be made visible
     * @return true if the key event is consumed by this method, false otherwise
     */
    private boolean scrollAndFocusHorizontally(int direction, int left, int right) {
        boolean handled = true;

        int width = getWidth();
        int containerLeft = getScrollX();
        int containerRight = containerLeft + width;
        boolean goLeft = direction == View.FOCUS_LEFT;

        View newFocused = findFocusableViewInBoundsHorizontally(goLeft, left, right);
        if (newFocused == null) {
            newFocused = this;
        }

        if (left >= containerLeft && right <= containerRight) {
            handled = false;
        } else {
            int delta = goLeft ? (left - containerLeft) : (right - containerRight);
            doScrollX(delta);
        }

        if (newFocused != findFocus()) newFocused.requestFocus(direction);

        return handled;
    }

    /**
     * <p>
     * Finds the next focusable component that fits in the specified bounds.
     * </p>
     *
     * @param topFocus look for a candidate is the one at the top of the bounds
     *                 if topFocus is true, or at the bottom of the bounds if topFocus is
     *                 false
     * @param top      the top offset of the bounds in which a focusable must be
     *                 found
     * @param bottom   the bottom offset of the bounds in which a focusable must
     *                 be found
     * @return the next focusable component in the bounds or null if none can
     *         be found
     */
    private View findFocusableViewInBoundsVertically(boolean topFocus, int top, int bottom) {

        List<View> focusables = getFocusables(View.FOCUS_FORWARD);
        View focusCandidate = null;

        /*
         * A fully contained focusable is one where its top is below the bound's
         * top, and its bottom is above the bound's bottom. A partially
         * contained focusable is one where some part of it is within the
         * bounds, but it also has some part that is not within bounds.  A fully contained
         * focusable is preferred to a partially contained focusable.
         */
        boolean foundFullyContainedFocusable = false;

        int count = focusables.size();
        for (int i = 0; i < count; i++) {
            View view = focusables.get(i);
            int viewTop = view.getTop();
            int viewBottom = view.getBottom();

            if (top < viewBottom && viewTop < bottom) {
                /*
                 * the focusable is in the target area, it is a candidate for
                 * focusing
                 */

                final boolean viewIsFullyContained = (top < viewTop) &&
                        (viewBottom < bottom);

                if (focusCandidate == null) {
                    /* No candidate, take this one */
                    focusCandidate = view;
                    foundFullyContainedFocusable = viewIsFullyContained;
                } else {
                    final boolean viewIsCloserToBoundary =
                            (topFocus && viewTop < focusCandidate.getTop()) ||
                                    (!topFocus && viewBottom > focusCandidate
                                            .getBottom());

                    if (foundFullyContainedFocusable) {
                        if (viewIsFullyContained && viewIsCloserToBoundary) {
                            /*
                             * We're dealing with only fully contained views, so
                             * it has to be closer to the boundary to beat our
                             * candidate
                             */
                            focusCandidate = view;
                        }
                    } else {
                        if (viewIsFullyContained) {
                            /* Any fully contained view beats a partially contained view */
                            focusCandidate = view;
                            foundFullyContainedFocusable = true;
                        } else if (viewIsCloserToBoundary) {
                            /*
                             * Partially contained view beats another partially
                             * contained view if it's closer
                             */
                            focusCandidate = view;
                        }
                    }
                }
            }
        }

        return focusCandidate;
    }

    /**
     * <p>
     * Finds the next focusable component that fits in the specified bounds.
     * </p>
     *
     * @param leftFocus look for a candidate is the one at the left of the bounds
     *                  if leftFocus is true, or at the right of the bounds if
     *                  leftFocus is false
     * @param left      the left offset of the bounds in which a focusable must be
     *                  found
     * @param right     the right offset of the bounds in which a focusable must
     *                  be found
     * @return the next focusable component in the bounds or null if none can
     *         be found
     */
    private View findFocusableViewInBoundsHorizontally(boolean leftFocus, int left, int right) {

        List<View> focusables = getFocusables(View.FOCUS_FORWARD);
        View focusCandidate = null;

        /*
         * A fully contained focusable is one where its left is below the bound's
         * left, and its right is above the bound's right. A partially
         * contained focusable is one where some part of it is within the
         * bounds, but it also has some part that is not within bounds.  A fully contained
         * focusable is preferred to a partially contained focusable.
         */
        boolean foundFullyContainedFocusable = false;

        int count = focusables.size();
        for (int i = 0; i < count; i++) {
            View view = focusables.get(i);
            int viewLeft = view.getLeft();
            int viewRight = view.getRight();

            if (left < viewRight && viewLeft < right) {
                /*
                 * the focusable is in the target area, it is a candidate for
                 * focusing
                 */

                final boolean viewIsFullyContained = (left < viewLeft) &&
                        (viewRight < right);

                if (focusCandidate == null) {
                    /* No candidate, take this one */
                    focusCandidate = view;
                    foundFullyContainedFocusable = viewIsFullyContained;
                } else {
                    final boolean viewIsCloserToBoundary =
                            (leftFocus && viewLeft < focusCandidate.getLeft()) ||
                                    (!leftFocus && viewRight > focusCandidate.getRight());

                    if (foundFullyContainedFocusable) {
                        if (viewIsFullyContained && viewIsCloserToBoundary) {
                            /*
                             * We're dealing with only fully contained views, so
                             * it has to be closer to the boundary to beat our
                             * candidate
                             */
                            focusCandidate = view;
                        }
                    } else {
                        if (viewIsFullyContained) {
                            /* Any fully contained view beats a partially contained view */
                            focusCandidate = view;
                            foundFullyContainedFocusable = true;
                        } else if (viewIsCloserToBoundary) {
                            /*
                             * Partially contained view beats another partially
                             * contained view if it's closer
                             */
                            focusCandidate = view;
                        }
                    }
                }
            }
        }

        return focusCandidate;
    }

    /**
     * <p>Handles scrolling in response to a "page up/down/left/right" shortcut press. This
     * method will scroll the view by one page left, right, up or down and give the focus
     * to the leftmost/rightmost/topmost/bottommost component in the new visible area. If no
     * component is a good candidate for focus, this DroidUIScrollView reclaims the
     * focus.</p>
     *
     * @param direction the scroll direction:
     * {@link android.view.View#FOCUS_LEFT} to go one page left or
     * {@link android.view.View#FOCUS_RIGHT} to go one page right or
     * {@link android.view.View#FOCUS_UP} to go one page up or
     * {@link android.view.View#FOCUS_DOWN} to go one page down
     * @return true if the key event is consumed by this method, false otherwise
     */
    public boolean pageScroll(int direction) {
        switch (direction){
            case View.FOCUS_DOWN:
            case View.FOCUS_UP:
                boolean down = direction == View.FOCUS_DOWN;
                int height = getHeight();

                if (down) {
                    mTempRect.top = getScrollY() + height;
                    int count = getChildCount();
                    if (count > 0) {
                        View view = getChildAt(count - 1);
                        if (mTempRect.top + height > view.getBottom()) {
                            mTempRect.top = view.getBottom() - height;
                        }
                    }
                } else {
                    mTempRect.top = getScrollY() - height;
                    if (mTempRect.top < 0) {
                        mTempRect.top = 0;
                    }
                }
                mTempRect.bottom = mTempRect.top + height;

                return scrollAndFocusVertically(direction, mTempRect.top, mTempRect.bottom);
            case View.FOCUS_LEFT:
            case View.FOCUS_RIGHT:
                boolean right = direction == View.FOCUS_RIGHT;
                int width = getWidth();

                if (right) {
                    mTempRect.left = getScrollX() + width;
                    int count = getChildCount();
                    if (count > 0) {
                        View view = getChildAt(0);
                        if (mTempRect.left + width > view.getRight()) {
                            mTempRect.left = view.getRight() - width;
                        }
                    }
                } else {
                    mTempRect.left = getScrollX() - width;
                    if (mTempRect.left < 0) {
                        mTempRect.left = 0;
                    }
                }
                mTempRect.right = mTempRect.left + width;

                return scrollAndFocusHorizontally(direction, mTempRect.left, mTempRect.right);
        }
        return true;
    }


    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (getContext().getApplicationInfo().targetSdkVersion <= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            // Some old apps reused IDs in ways they shouldn't have.
            // Don't break them, but they don't get scroll state restoration.
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        mSavedState = ss;
        requestLayout();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        if (getContext().getApplicationInfo().targetSdkVersion <= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            // Some old apps reused IDs in ways they shouldn't have.
            // Don't break them, but they don't get scroll state restoration.
            return super.onSaveInstanceState();
        }
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.scrollPosition[0] = getScrollX();
        ss.scrollPosition[1] = getScrollY();
        return ss;
    }

    /**
     * Warning, Below are all protected View methods
     */

    /**
     * Gets a scale factor that determines the distance the view should scroll
     * vertically in response to {@link android.view.MotionEvent#ACTION_SCROLL}.
     * @return The vertical scroll scale factor.
     * @hide
     */
    protected float getVerticalScrollFactor() {
        if (mVerticalScrollFactor == 0) {
            TypedValue outValue = new TypedValue();
//            if (!getContext().getTheme().resolveAttribute(
//                    com.android.internal.R.attr.listPreferredItemHeight, outValue, true)) {
//                throw new IllegalStateException(
//                        "Expected theme to define listPreferredItemHeight.");
//            }
            mVerticalScrollFactor = outValue.getDimension(
                    getContext().getResources().getDisplayMetrics());
        }
        return mVerticalScrollFactor;
    }

    /**
     * Gets a scale factor that determines the distance the view should scroll
     * horizontally in response to {@link android.view.MotionEvent#ACTION_SCROLL}.
     * @return The horizontal scroll scale factor.
     * @hide
     */
    protected float getHorizontalScrollFactor() {
        // TODO: Should use something else.
        return getVerticalScrollFactor();
    }

    /**
     * Used to indicate that the parent of this view should be invalidated. This functionality
     * is used to force the parent to rebuild its display list (when hardware-accelerated),
     * which is necessary when various parent-managed properties of the view change, such as
     * alpha, translationX/Y, scrollX/Y, scaleX/Y, and rotation/X/Y. This method will propagate
     * an invalidation event to the parent.
     *
     * @hide
     */
    protected void invalidateParentIfNeeded() {
        if (isHardwareAccelerated() && getParent() instanceof View) {
            ((View) getParent()).invalidate();
        }
    }



    static class SavedState extends BaseSavedState {
        public int[] scrollPosition = new int[2];

        SavedState(Parcelable superState) {
            super(superState);
        }

        public SavedState(Parcel source) {
            super(source);
            source.readIntArray(scrollPosition);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeIntArray(scrollPosition);
        }

        @Override
        public String toString() {
            return DroidUIScrollView.class.getCanonicalName()
                    + Integer.toHexString(System.identityHashCode(this))
                    + " scrollPosition=[" + scrollPosition[0] + ", " + scrollPosition[1] +"]}";
        }

        public static final Creator<SavedState> CREATOR
                = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}