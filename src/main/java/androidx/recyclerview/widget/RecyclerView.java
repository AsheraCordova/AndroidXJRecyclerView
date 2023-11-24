package androidx.recyclerview.widget;
import r.android.graphics.Rect;
import r.android.graphics.RectF;
import r.android.graphics.drawable.Drawable;
import r.android.os.Build;
import r.android.os.Parcelable;
import r.android.os.SystemClock;
import r.android.util.Log;
import r.android.util.SparseArray;
import r.android.view.MotionEvent;
import r.android.view.View;
import r.android.view.ViewGroup;
import r.android.view.ViewParent;
import r.android.widget.LinearLayout;
import androidx.core.os.TraceCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView.ItemAnimator.ItemHolderInfo;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
public class RecyclerView extends ViewGroup {
  static final String TAG="RecyclerView";
  static final boolean DEBUG=false;
  static final boolean VERBOSE_TRACING=false;
  static final boolean FORCE_INVALIDATE_DISPLAY_LIST=Build.VERSION.SDK_INT == 18 || Build.VERSION.SDK_INT == 19 || Build.VERSION.SDK_INT == 20;
  static final boolean ALLOW_SIZE_IN_UNSPECIFIED_SPEC=Build.VERSION.SDK_INT >= 23;
  static final boolean POST_UPDATES_ON_ANIMATION=Build.VERSION.SDK_INT >= 16;
  static final boolean ALLOW_THREAD_GAP_WORK=Build.VERSION.SDK_INT >= 21;
  private static final boolean FORCE_ABS_FOCUS_SEARCH_DIRECTION=Build.VERSION.SDK_INT <= 15;
  private static final boolean IGNORE_DETACHED_FOCUSED_CHILD=Build.VERSION.SDK_INT <= 15;
  static final boolean DISPATCH_TEMP_DETACH=false;
  public static final int HORIZONTAL=LinearLayout.HORIZONTAL;
  public static final int VERTICAL=LinearLayout.VERTICAL;
  static final int DEFAULT_ORIENTATION=VERTICAL;
  public static final int NO_POSITION=-1;
  public static final int INVALID_TYPE=-1;
  public static final int TOUCH_SLOP_DEFAULT=0;
  public static final int TOUCH_SLOP_PAGING=1;
  public static final int UNDEFINED_DURATION=Integer.MIN_VALUE;
  static final int MAX_SCROLL_DURATION=2000;
  static final String TRACE_SCROLL_TAG="RV Scroll";
  private static final String TRACE_ON_LAYOUT_TAG="RV OnLayout";
  private static final String TRACE_ON_DATA_SET_CHANGE_LAYOUT_TAG="RV FullInvalidate";
  private static final String TRACE_HANDLE_ADAPTER_UPDATES_TAG="RV PartialInvalidate";
  static final String TRACE_BIND_VIEW_TAG="RV OnBindView";
  static final String TRACE_PREFETCH_TAG="RV Prefetch";
  static final String TRACE_NESTED_PREFETCH_TAG="RV Nested Prefetch";
  static final String TRACE_CREATE_VIEW_TAG="RV CreateView";
  private final RecyclerViewDataObserver mObserver=new RecyclerViewDataObserver();
  final Recycler mRecycler=new Recycler();
  AdapterHelper mAdapterHelper;
  ChildHelper mChildHelper;
  final ViewInfoStore mViewInfoStore=new ViewInfoStore();
  boolean mClipToPadding;
  final Rect mTempRect=new Rect();
  final RectF mTempRectF=new RectF();
  Adapter mAdapter;
  LayoutManager mLayout;
  RecyclerListener mRecyclerListener;
  final List<RecyclerListener> mRecyclerListeners=new ArrayList<>();
  final ArrayList<ItemDecoration> mItemDecorations=new ArrayList<>();
  boolean mIsAttached;
  boolean mHasFixedSize;
  boolean mEnableFastScroller;
  boolean mFirstLayoutComplete;
  private int mInterceptRequestLayoutDepth=0;
  boolean mLayoutWasDefered;
  boolean mLayoutSuppressed;
  private boolean mIgnoreMotionEventTillDown;
  private int mEatenAccessibilityChangeFlags;
  boolean mAdapterUpdateDuringMeasure;
  private List<OnChildAttachStateChangeListener> mOnChildAttachStateListeners;
  boolean mDataSetHasChangedAfterLayout=false;
  boolean mDispatchItemsChangedEvent=false;
  private int mLayoutOrScrollCounter=0;
  private int mDispatchScrollCounter=0;
  private static final int INVALID_POINTER=-1;
  public static final int SCROLL_STATE_IDLE=0;
  public static final int SCROLL_STATE_DRAGGING=1;
  public static final int SCROLL_STATE_SETTLING=2;
  static final long FOREVER_NS=Long.MAX_VALUE;
  private int mScrollState=SCROLL_STATE_IDLE;
  private int mScrollPointerId=INVALID_POINTER;
  private int mInitialTouchX;
  private int mInitialTouchY;
  private int mLastTouchX;
  private int mLastTouchY;
  private int mTouchSlop;
  private final int mMinFlingVelocity;
  private final int mMaxFlingVelocity;
  private boolean mPreserveFocusAfterLayout=true;
  GapWorker.LayoutPrefetchRegistryImpl mPrefetchRegistry=ALLOW_THREAD_GAP_WORK ? new GapWorker.LayoutPrefetchRegistryImpl() : null;
  final State mState=new State();
  private OnScrollListener mScrollListener;
  private List<OnScrollListener> mScrollListeners;
  boolean mItemsAddedOrRemoved=false;
  boolean mItemsChanged=false;
  boolean mPostedAnimatorRunner=false;
  private final int[] mMinMaxLayoutPositions=new int[2];
  private NestedScrollingChildHelper mScrollingChildHelper;
  private final int[] mScrollOffset=new int[2];
  private final int[] mNestedOffsets=new int[2];
  final int[] mReusableIntPair=new int[2];
  private Runnable mItemAnimatorRunner=new Runnable(){
    public void run(){
      if (mItemAnimator != null) {
        mItemAnimator.runPendingAnimations();
      }
      mPostedAnimatorRunner=false;
    }
  }
;
  private boolean mLastAutoMeasureSkippedDueToExact;
  private int mLastAutoMeasureNonExactMeasuredWidth=0;
  private int mLastAutoMeasureNonExactMeasuredHeight=0;
  private final ViewInfoStore.ProcessCallback mViewInfoProcessCallback=new ViewInfoStore.ProcessCallback(){
    public void processDisappeared(    ViewHolder viewHolder,    ItemHolderInfo info,    ItemHolderInfo postInfo){
      mRecycler.unscrapView(viewHolder);
      animateDisappearance(viewHolder,info,postInfo);
    }
    public void processAppeared(    ViewHolder viewHolder,    ItemHolderInfo preInfo,    ItemHolderInfo info){
      animateAppearance(viewHolder,preInfo,info);
    }
    public void processPersistent(    ViewHolder viewHolder,    ItemHolderInfo preInfo,    ItemHolderInfo postInfo){
      viewHolder.setIsRecyclable(false);
      if (mDataSetHasChangedAfterLayout) {
        if (mItemAnimator.animateChange(viewHolder,viewHolder,preInfo,postInfo)) {
          postAnimationRunner();
        }
      }
 else       if (mItemAnimator.animatePersistence(viewHolder,preInfo,postInfo)) {
        postAnimationRunner();
      }
    }
    public void unused(    ViewHolder viewHolder){
      mLayout.removeAndRecycleView(viewHolder.itemView,mRecycler);
    }
  }
;
  String exceptionLabel(){
    return " " + super.toString() + ", adapter:"+ mAdapter+ ", layout:"+ mLayout+ ", context:"+ getContext();
  }
  private void initChildrenHelper(){
    mChildHelper=new ChildHelper(new ChildHelper.Callback(){
      public int getChildCount(){
        return RecyclerView.this.getChildCount();
      }
      public void addView(      View child,      int index){
        if (VERBOSE_TRACING) {
          TraceCompat.beginSection("RV addView");
        }
        RecyclerView.this.addView(child,index);
        if (VERBOSE_TRACING) {
          TraceCompat.endSection();
        }
        dispatchChildAttached(child);
      }
      public int indexOfChild(      View view){
        return RecyclerView.this.indexOfChild(view);
      }
      public void removeViewAt(      int index){
        final View child=RecyclerView.this.getChildAt(index);
        if (child != null) {
          dispatchChildDetached(child);
          //child.clearAnimation();
        }
        if (VERBOSE_TRACING) {
          TraceCompat.beginSection("RV removeViewAt");
        }
        RecyclerView.this.removeViewAt(index);
        if (VERBOSE_TRACING) {
          TraceCompat.endSection();
        }
      }
      public View getChildAt(      int offset){
        return RecyclerView.this.getChildAt(offset);
      }
      public void removeAllViews(){
        final int count=getChildCount();
        for (int i=0; i < count; i++) {
          View child=getChildAt(i);
          dispatchChildDetached(child);
          //child.clearAnimation();
        }
        RecyclerView.this.removeAllViews();
      }
      public ViewHolder getChildViewHolder(      View view){
        return getChildViewHolderInt(view);
      }
      public void attachViewToParent(      View child,      int index,      ViewGroup.LayoutParams layoutParams){
        final ViewHolder vh=getChildViewHolderInt(child);
        if (vh != null) {
          if (!vh.isTmpDetached() && !vh.shouldIgnore()) {
            throw new IllegalArgumentException("Called attach on a child which is not" + " detached: " + vh + exceptionLabel());
          }
          if (DEBUG) {
            Log.d(TAG,"reAttach " + vh);
          }
          vh.clearTmpDetachFlag();
        }
        RecyclerView.this.attachViewToParent(child,index,layoutParams);
      }
      public void detachViewFromParent(      int offset){
        final View view=getChildAt(offset);
        if (view != null) {
          final ViewHolder vh=getChildViewHolderInt(view);
          if (vh != null) {
            if (vh.isTmpDetached() && !vh.shouldIgnore()) {
              throw new IllegalArgumentException("called detach on an already" + " detached child " + vh + exceptionLabel());
            }
            if (DEBUG) {
              Log.d(TAG,"tmpDetach " + vh);
            }
            vh.addFlags(ViewHolder.FLAG_TMP_DETACHED);
          }
        }
        RecyclerView.this.detachViewFromParent(offset);
      }
      public void onEnteredHiddenState(      View child){
        final ViewHolder vh=getChildViewHolderInt(child);
        if (vh != null) {
          vh.onEnteredHiddenState(RecyclerView.this);
        }
      }
      public void onLeftHiddenState(      View child){
        final ViewHolder vh=getChildViewHolderInt(child);
        if (vh != null) {
          vh.onLeftHiddenState(RecyclerView.this);
        }
      }
    }
);
  }
  void initAdapterManager(){
    mAdapterHelper=new AdapterHelper(new AdapterHelper.Callback(){
      public ViewHolder findViewHolder(      int position){
        final ViewHolder vh=findViewHolderForPosition(position,true);
        if (vh == null) {
          return null;
        }
        if (mChildHelper.isHidden(vh.itemView)) {
          if (DEBUG) {
            Log.d(TAG,"assuming view holder cannot be find because it is hidden");
          }
          return null;
        }
        return vh;
      }
      public void offsetPositionsForRemovingInvisible(      int start,      int count){
        offsetPositionRecordsForRemove(start,count,true);
        mItemsAddedOrRemoved=true;
        mState.mDeletedInvisibleItemCountSincePreviousLayout+=count;
      }
      public void offsetPositionsForRemovingLaidOutOrNewView(      int positionStart,      int itemCount){
        offsetPositionRecordsForRemove(positionStart,itemCount,false);
        mItemsAddedOrRemoved=true;
      }
      public void markViewHoldersUpdated(      int positionStart,      int itemCount,      Object payload){
        viewRangeUpdate(positionStart,itemCount,payload);
        mItemsChanged=true;
      }
      public void onDispatchFirstPass(      AdapterHelper.UpdateOp op){
        dispatchUpdate(op);
      }
      void dispatchUpdate(      AdapterHelper.UpdateOp op){
switch (op.cmd) {
case AdapterHelper.UpdateOp.ADD:
          mLayout.onItemsAdded(RecyclerView.this,op.positionStart,op.itemCount);
        break;
case AdapterHelper.UpdateOp.REMOVE:
      mLayout.onItemsRemoved(RecyclerView.this,op.positionStart,op.itemCount);
    break;
case AdapterHelper.UpdateOp.UPDATE:
  mLayout.onItemsUpdated(RecyclerView.this,op.positionStart,op.itemCount,op.payload);
break;
case AdapterHelper.UpdateOp.MOVE:
mLayout.onItemsMoved(RecyclerView.this,op.positionStart,op.itemCount,1);
break;
}
}
public void onDispatchSecondPass(AdapterHelper.UpdateOp op){
dispatchUpdate(op);
}
public void offsetPositionsForAdd(int positionStart,int itemCount){
offsetPositionRecordsForInsert(positionStart,itemCount);
mItemsAddedOrRemoved=true;
}
public void offsetPositionsForMove(int from,int to){
offsetPositionRecordsForMove(from,to);
mItemsAddedOrRemoved=true;
}
}
);
}
public void setAdapter(Adapter adapter){
setLayoutFrozen(false);
setAdapterInternal(adapter,false,true);
processDataSetCompletelyChanged(false);
requestLayout();
}
void removeAndRecycleViews(){
if (mItemAnimator != null) {
mItemAnimator.endAnimations();
}
if (mLayout != null) {
mLayout.removeAndRecycleAllViews(mRecycler);
mLayout.removeAndRecycleScrapInt(mRecycler);
}
mRecycler.clear();
}
private void setAdapterInternal(Adapter adapter,boolean compatibleWithPrevious,boolean removeAndRecycleViews){
if (mAdapter != null) {
mAdapter.unregisterAdapterDataObserver(mObserver);
mAdapter.onDetachedFromRecyclerView(this);
}
if (!compatibleWithPrevious || removeAndRecycleViews) {
removeAndRecycleViews();
}
mAdapterHelper.reset();
final Adapter oldAdapter=mAdapter;
mAdapter=adapter;
if (adapter != null) {
adapter.registerAdapterDataObserver(mObserver);
adapter.onAttachedToRecyclerView(this);
}
if (mLayout != null) {
mLayout.onAdapterChanged(oldAdapter,mAdapter);
}
mRecycler.onAdapterChanged(oldAdapter,mAdapter,compatibleWithPrevious);
mState.mStructureChanged=true;
}
public Adapter getAdapter(){
return mAdapter;
}
public void setLayoutManager(LayoutManager layout){
if (layout == mLayout) {
return;
}
stopScroll();
if (mLayout != null) {
if (mItemAnimator != null) {
mItemAnimator.endAnimations();
}
mLayout.removeAndRecycleAllViews(mRecycler);
mLayout.removeAndRecycleScrapInt(mRecycler);
mRecycler.clear();
if (mIsAttached) {
mLayout.dispatchDetachedFromWindow(this,mRecycler);
}
mLayout.setRecyclerView(null);
mLayout=null;
}
 else {
mRecycler.clear();
}
mChildHelper.removeAllViewsUnfiltered();
mLayout=layout;
if (layout != null) {
if (layout.mRecyclerView != null) {
throw new IllegalArgumentException("LayoutManager " + layout + " is already attached to a RecyclerView:"+ layout.mRecyclerView.exceptionLabel());
}
mLayout.setRecyclerView(this);
if (mIsAttached) {
mLayout.dispatchAttachedToWindow(this);
}
}
mRecycler.updateViewCacheSize();
requestLayout();
}
private void addAnimatingView(ViewHolder viewHolder){
final View view=viewHolder.itemView;
final boolean alreadyParented=view.getParent() == this;
mRecycler.unscrapView(getChildViewHolder(view));
if (viewHolder.isTmpDetached()) {
mChildHelper.attachViewToParent(view,-1,view.getLayoutParams(),true);
}
 else if (!alreadyParented) {
mChildHelper.addView(view,true);
}
 else {
mChildHelper.hide(view);
}
}
public LayoutManager getLayoutManager(){
return mLayout;
}
public RecycledViewPool getRecycledViewPool(){
return mRecycler.getRecycledViewPool();
}
public int getScrollState(){
return mScrollState;
}
public void addItemDecoration(ItemDecoration decor,int index){
if (mLayout != null) {
mLayout.assertNotInLayoutOrScroll("Cannot add item decoration during a scroll  or" + " layout");
}
if (mItemDecorations.isEmpty()) {
setWillNotDraw(false);
}
if (index < 0) {
mItemDecorations.add(decor);
}
 else {
mItemDecorations.add(index,decor);
}
markItemDecorInsetsDirty();
requestLayout();
}
public void addItemDecoration(ItemDecoration decor){
addItemDecoration(decor,-1);
}
public void setOnScrollListener(OnScrollListener listener){
mScrollListener=listener;
}
public void scrollToPosition(int position){
if (mLayoutSuppressed) {
return;
}
stopScroll();
if (mLayout == null) {
Log.e(TAG,"Cannot scroll to position a LayoutManager set. " + "Call setLayoutManager with a non-null argument.");
return;
}
mLayout.scrollToPosition(position);
awakenScrollBars();
}
public void scrollBy(int x,int y){
if (mLayout == null) {
Log.e(TAG,"Cannot scroll without a LayoutManager set. " + "Call setLayoutManager with a non-null argument.");
return;
}
if (mLayoutSuppressed) {
return;
}
final boolean canScrollHorizontal=mLayout.canScrollHorizontally();
final boolean canScrollVertical=mLayout.canScrollVertically();
if (canScrollHorizontal || canScrollVertical) {
scrollByInternal(canScrollHorizontal ? x : 0,canScrollVertical ? y : 0,null,TYPE_TOUCH);
}
}
void scrollStep(int dx,int dy,int[] consumed){
startInterceptRequestLayout();
onEnterLayoutOrScroll();
TraceCompat.beginSection(TRACE_SCROLL_TAG);
fillRemainingScrollValues(mState);
int consumedX=0;
int consumedY=0;
if (dx != 0) {
consumedX=mLayout.scrollHorizontallyBy(dx,mRecycler,mState);
}
if (dy != 0) {
consumedY=mLayout.scrollVerticallyBy(dy,mRecycler,mState);
}
TraceCompat.endSection();
repositionShadowingViews();
onExitLayoutOrScroll();
stopInterceptRequestLayout(false);
if (consumed != null) {
consumed[0]=consumedX;
consumed[1]=consumedY;
}
}
void consumePendingUpdateOperations(){
if (!mFirstLayoutComplete || mDataSetHasChangedAfterLayout) {
TraceCompat.beginSection(TRACE_ON_DATA_SET_CHANGE_LAYOUT_TAG);
dispatchLayout();
TraceCompat.endSection();
return;
}
if (!mAdapterHelper.hasPendingUpdates()) {
return;
}
if (mAdapterHelper.hasAnyUpdateTypes(AdapterHelper.UpdateOp.UPDATE) && !mAdapterHelper.hasAnyUpdateTypes(AdapterHelper.UpdateOp.ADD | AdapterHelper.UpdateOp.REMOVE | AdapterHelper.UpdateOp.MOVE)) {
TraceCompat.beginSection(TRACE_HANDLE_ADAPTER_UPDATES_TAG);
startInterceptRequestLayout();
onEnterLayoutOrScroll();
mAdapterHelper.preProcess();
if (!mLayoutWasDefered) {
if (hasUpdatedView()) {
dispatchLayout();
}
 else {
mAdapterHelper.consumePostponedUpdates();
}
}
stopInterceptRequestLayout(true);
onExitLayoutOrScroll();
TraceCompat.endSection();
}
 else if (mAdapterHelper.hasPendingUpdates()) {
TraceCompat.beginSection(TRACE_ON_DATA_SET_CHANGE_LAYOUT_TAG);
dispatchLayout();
TraceCompat.endSection();
}
}
private boolean hasUpdatedView(){
final int childCount=mChildHelper.getChildCount();
for (int i=0; i < childCount; i++) {
final ViewHolder holder=getChildViewHolderInt(mChildHelper.getChildAt(i));
if (holder == null || holder.shouldIgnore()) {
continue;
}
if (holder.isUpdated()) {
return true;
}
}
return false;
}
boolean scrollByInternal(int x,int y,MotionEvent ev,int type){
int unconsumedX=0;
int unconsumedY=0;
int consumedX=0;
int consumedY=0;
consumePendingUpdateOperations();
if (mAdapter != null) {
mReusableIntPair[0]=0;
mReusableIntPair[1]=0;
scrollStep(x,y,mReusableIntPair);
consumedX=mReusableIntPair[0];
consumedY=mReusableIntPair[1];
unconsumedX=x - consumedX;
unconsumedY=y - consumedY;
}
if (!mItemDecorations.isEmpty()) {
invalidate();
}
mReusableIntPair[0]=0;
mReusableIntPair[1]=0;
dispatchNestedScroll(consumedX,consumedY,unconsumedX,unconsumedY,mScrollOffset,type,mReusableIntPair);
unconsumedX-=mReusableIntPair[0];
unconsumedY-=mReusableIntPair[1];
boolean consumedNestedScroll=mReusableIntPair[0] != 0 || mReusableIntPair[1] != 0;
mLastTouchX-=mScrollOffset[0];
mLastTouchY-=mScrollOffset[1];
mNestedOffsets[0]+=mScrollOffset[0];
mNestedOffsets[1]+=mScrollOffset[1];
if (getOverScrollMode() != View.OVER_SCROLL_NEVER) {
if (ev != null && !MotionEventCompat.isFromSource(ev,InputDevice.SOURCE_MOUSE)) {
pullGlows(ev.getX(),unconsumedX,ev.getY(),unconsumedY);
}
considerReleasingGlowsOnScroll(x,y);
}
if (consumedX != 0 || consumedY != 0) {
dispatchOnScrolled(consumedX,consumedY);
}
if (!awakenScrollBars()) {
invalidate();
}
return consumedNestedScroll || consumedX != 0 || consumedY != 0;
}
public int computeHorizontalScrollOffset(){
if (mLayout == null) {
return 0;
}
return mLayout.canScrollHorizontally() ? mLayout.computeHorizontalScrollOffset(mState) : 0;
}
public int computeHorizontalScrollExtent(){
if (mLayout == null) {
return 0;
}
return mLayout.canScrollHorizontally() ? mLayout.computeHorizontalScrollExtent(mState) : 0;
}
public int computeHorizontalScrollRange(){
if (mLayout == null) {
return 0;
}
return mLayout.canScrollHorizontally() ? mLayout.computeHorizontalScrollRange(mState) : 0;
}
public int computeVerticalScrollOffset(){
if (mLayout == null) {
return 0;
}
return mLayout.canScrollVertically() ? mLayout.computeVerticalScrollOffset(mState) : 0;
}
public int computeVerticalScrollExtent(){
if (mLayout == null) {
return 0;
}
return mLayout.canScrollVertically() ? mLayout.computeVerticalScrollExtent(mState) : 0;
}
public int computeVerticalScrollRange(){
if (mLayout == null) {
return 0;
}
return mLayout.canScrollVertically() ? mLayout.computeVerticalScrollRange(mState) : 0;
}
void startInterceptRequestLayout(){
mInterceptRequestLayoutDepth++;
if (mInterceptRequestLayoutDepth == 1 && !mLayoutSuppressed) {
mLayoutWasDefered=false;
}
}
void stopInterceptRequestLayout(boolean performLayoutChildren){
if (mInterceptRequestLayoutDepth < 1) {
if (DEBUG) {
throw new IllegalStateException("stopInterceptRequestLayout was called more " + "times than startInterceptRequestLayout." + exceptionLabel());
}
mInterceptRequestLayoutDepth=1;
}
if (!performLayoutChildren && !mLayoutSuppressed) {
mLayoutWasDefered=false;
}
if (mInterceptRequestLayoutDepth == 1) {
if (performLayoutChildren && mLayoutWasDefered && !mLayoutSuppressed&& mLayout != null && mAdapter != null) {
dispatchLayout();
}
if (!mLayoutSuppressed) {
mLayoutWasDefered=false;
}
}
mInterceptRequestLayoutDepth--;
}
public final void suppressLayout(boolean suppress){
if (suppress != mLayoutSuppressed) {
assertNotInLayoutOrScroll("Do not suppressLayout in layout or scroll");
if (!suppress) {
mLayoutSuppressed=false;
if (mLayoutWasDefered && mLayout != null && mAdapter != null) {
requestLayout();
}
mLayoutWasDefered=false;
}
 else {
//final long now=SystemClock.uptimeMillis();
//MotionEvent cancelEvent=MotionEvent.obtain(now,now,MotionEvent.ACTION_CANCEL,0.0f,0.0f,0);
//onTouchEvent(cancelEvent);
mLayoutSuppressed=true;
mIgnoreMotionEventTillDown=true;
stopScroll();
}
}
}
public void setLayoutFrozen(boolean frozen){
suppressLayout(frozen);
}
void assertInLayoutOrScroll(String message){
if (!isComputingLayout()) {
if (message == null) {
throw new IllegalStateException("Cannot call this method unless RecyclerView is " + "computing a layout or scrolling" + exceptionLabel());
}
throw new IllegalStateException(message + exceptionLabel());
}
}
void assertNotInLayoutOrScroll(String message){
if (isComputingLayout()) {
if (message == null) {
throw new IllegalStateException("Cannot call this method while RecyclerView is " + "computing a layout or scrolling" + exceptionLabel());
}
throw new IllegalStateException(message);
}
if (mDispatchScrollCounter > 0) {
Log.w(TAG,"Cannot call this method in a scroll callback. Scroll callbacks might" + "be run during a measure & layout pass where you cannot change the" + "RecyclerView data. Any method call that might change the structure"+ "of the RecyclerView or the adapter contents should be postponed to"+ "the next frame.",new IllegalStateException("" + exceptionLabel()));
}
}
protected void onMeasure(int widthSpec,int heightSpec){
if (mLayout == null) {
defaultOnMeasure(widthSpec,heightSpec);
return;
}
if (mLayout.isAutoMeasureEnabled()) {
final int widthMode=MeasureSpec.getMode(widthSpec);
final int heightMode=MeasureSpec.getMode(heightSpec);
mLayout.onMeasure(mRecycler,mState,widthSpec,heightSpec);
mLastAutoMeasureSkippedDueToExact=widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY;
if (mLastAutoMeasureSkippedDueToExact || mAdapter == null) {
return;
}
if (mState.mLayoutStep == State.STEP_START) {
dispatchLayoutStep1();
}
mLayout.setMeasureSpecs(widthSpec,heightSpec);
mState.mIsMeasuring=true;
dispatchLayoutStep2();
mLayout.setMeasuredDimensionFromChildren(widthSpec,heightSpec);
if (mLayout.shouldMeasureTwice()) {
mLayout.setMeasureSpecs(MeasureSpec.makeMeasureSpec(getMeasuredWidth(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(getMeasuredHeight(),MeasureSpec.EXACTLY));
mState.mIsMeasuring=true;
dispatchLayoutStep2();
mLayout.setMeasuredDimensionFromChildren(widthSpec,heightSpec);
}
mLastAutoMeasureNonExactMeasuredWidth=getMeasuredWidth();
mLastAutoMeasureNonExactMeasuredHeight=getMeasuredHeight();
}
 else {
if (mHasFixedSize) {
mLayout.onMeasure(mRecycler,mState,widthSpec,heightSpec);
return;
}
if (mAdapterUpdateDuringMeasure) {
startInterceptRequestLayout();
onEnterLayoutOrScroll();
processAdapterUpdatesAndSetAnimationFlags();
onExitLayoutOrScroll();
if (mState.mRunPredictiveAnimations) {
mState.mInPreLayout=true;
}
 else {
mAdapterHelper.consumeUpdatesInOnePass();
mState.mInPreLayout=false;
}
mAdapterUpdateDuringMeasure=false;
stopInterceptRequestLayout(false);
}
 else if (mState.mRunPredictiveAnimations) {
setMeasuredDimension(getMeasuredWidth(),getMeasuredHeight());
return;
}
if (mAdapter != null) {
mState.mItemCount=mAdapter.getItemCount();
}
 else {
mState.mItemCount=0;
}
startInterceptRequestLayout();
mLayout.onMeasure(mRecycler,mState,widthSpec,heightSpec);
stopInterceptRequestLayout(false);
mState.mInPreLayout=false;
}
}
void defaultOnMeasure(int widthSpec,int heightSpec){
final int width=LayoutManager.chooseSize(widthSpec,getPaddingLeft() + getPaddingRight(),ViewCompat.getMinimumWidth(this));
final int height=LayoutManager.chooseSize(heightSpec,getPaddingTop() + getPaddingBottom(),ViewCompat.getMinimumHeight(this));
setMeasuredDimension(width,height);
}
void onEnterLayoutOrScroll(){
mLayoutOrScrollCounter++;
}
void onExitLayoutOrScroll(){
onExitLayoutOrScroll(true);
}
void onExitLayoutOrScroll(boolean enableChangeEvents){
mLayoutOrScrollCounter--;
if (mLayoutOrScrollCounter < 1) {
if (DEBUG && mLayoutOrScrollCounter < 0) {
throw new IllegalStateException("layout or scroll counter cannot go below zero." + "Some calls are not matching" + exceptionLabel());
}
mLayoutOrScrollCounter=0;
if (enableChangeEvents) {
dispatchContentChangedIfNecessary();
dispatchPendingImportantForAccessibilityChanges();
}
}
}
public boolean isComputingLayout(){
return mLayoutOrScrollCounter > 0;
}
private void processAdapterUpdatesAndSetAnimationFlags(){
if (mDataSetHasChangedAfterLayout) {
mAdapterHelper.reset();
if (mDispatchItemsChangedEvent) {
mLayout.onItemsChanged(this);
}
}
if (predictiveItemAnimationsEnabled()) {
mAdapterHelper.preProcess();
}
 else {
mAdapterHelper.consumeUpdatesInOnePass();
}
boolean animationTypeSupported=mItemsAddedOrRemoved || mItemsChanged;
mState.mRunSimpleAnimations=mFirstLayoutComplete && mItemAnimator != null && (mDataSetHasChangedAfterLayout || animationTypeSupported || mLayout.mRequestedSimpleAnimations) && (!mDataSetHasChangedAfterLayout || mAdapter.hasStableIds());
mState.mRunPredictiveAnimations=mState.mRunSimpleAnimations && animationTypeSupported && !mDataSetHasChangedAfterLayout&& predictiveItemAnimationsEnabled();
}
void dispatchLayout(){
if (mAdapter == null) {
Log.w(TAG,"No adapter attached; skipping layout");
return;
}
if (mLayout == null) {
Log.e(TAG,"No layout manager attached; skipping layout");
return;
}
mState.mIsMeasuring=false;
boolean needsRemeasureDueToExactSkip=mLastAutoMeasureSkippedDueToExact && (mLastAutoMeasureNonExactMeasuredWidth != getWidth() || mLastAutoMeasureNonExactMeasuredHeight != getHeight());
mLastAutoMeasureNonExactMeasuredWidth=0;
mLastAutoMeasureNonExactMeasuredHeight=0;
mLastAutoMeasureSkippedDueToExact=false;
if (mState.mLayoutStep == State.STEP_START) {
dispatchLayoutStep1();
mLayout.setExactMeasureSpecsFrom(this);
dispatchLayoutStep2();
}
 else if (mAdapterHelper.hasUpdates() || needsRemeasureDueToExactSkip || mLayout.getWidth() != getWidth() || mLayout.getHeight() != getHeight()) {
mLayout.setExactMeasureSpecsFrom(this);
dispatchLayoutStep2();
}
 else {
mLayout.setExactMeasureSpecsFrom(this);
}
dispatchLayoutStep3();
}
private void dispatchLayoutStep1(){
mState.assertLayoutStep(State.STEP_START);
fillRemainingScrollValues(mState);
mState.mIsMeasuring=false;
startInterceptRequestLayout();
mViewInfoStore.clear();
onEnterLayoutOrScroll();
processAdapterUpdatesAndSetAnimationFlags();
saveFocusInfo();
mState.mTrackOldChangeHolders=mState.mRunSimpleAnimations && mItemsChanged;
mItemsAddedOrRemoved=mItemsChanged=false;
mState.mInPreLayout=mState.mRunPredictiveAnimations;
mState.mItemCount=mAdapter.getItemCount();
findMinMaxChildLayoutPositions(mMinMaxLayoutPositions);
if (mState.mRunSimpleAnimations) {
int count=mChildHelper.getChildCount();
for (int i=0; i < count; ++i) {
final ViewHolder holder=getChildViewHolderInt(mChildHelper.getChildAt(i));
if (holder.shouldIgnore() || (holder.isInvalid() && !mAdapter.hasStableIds())) {
continue;
}
final ItemHolderInfo animationInfo=mItemAnimator.recordPreLayoutInformation(mState,holder,ItemAnimator.buildAdapterChangeFlagsForAnimations(holder),holder.getUnmodifiedPayloads());
mViewInfoStore.addToPreLayout(holder,animationInfo);
if (mState.mTrackOldChangeHolders && holder.isUpdated() && !holder.isRemoved()&& !holder.shouldIgnore()&& !holder.isInvalid()) {
long key=getChangedHolderKey(holder);
mViewInfoStore.addToOldChangeHolders(key,holder);
}
}
}
if (mState.mRunPredictiveAnimations) {
saveOldPositions();
final boolean didStructureChange=mState.mStructureChanged;
mState.mStructureChanged=false;
mLayout.onLayoutChildren(mRecycler,mState);
mState.mStructureChanged=didStructureChange;
for (int i=0; i < mChildHelper.getChildCount(); ++i) {
final View child=mChildHelper.getChildAt(i);
final ViewHolder viewHolder=getChildViewHolderInt(child);
if (viewHolder.shouldIgnore()) {
continue;
}
if (!mViewInfoStore.isInPreLayout(viewHolder)) {
int flags=ItemAnimator.buildAdapterChangeFlagsForAnimations(viewHolder);
boolean wasHidden=viewHolder.hasAnyOfTheFlags(ViewHolder.FLAG_BOUNCED_FROM_HIDDEN_LIST);
if (!wasHidden) {
flags|=ItemAnimator.FLAG_APPEARED_IN_PRE_LAYOUT;
}
final ItemHolderInfo animationInfo=mItemAnimator.recordPreLayoutInformation(mState,viewHolder,flags,viewHolder.getUnmodifiedPayloads());
if (wasHidden) {
recordAnimationInfoIfBouncedHiddenView(viewHolder,animationInfo);
}
 else {
mViewInfoStore.addToAppearedInPreLayoutHolders(viewHolder,animationInfo);
}
}
}
clearOldPositions();
}
 else {
clearOldPositions();
}
onExitLayoutOrScroll();
stopInterceptRequestLayout(false);
mState.mLayoutStep=State.STEP_LAYOUT;
}
private void dispatchLayoutStep2(){
startInterceptRequestLayout();
onEnterLayoutOrScroll();
mState.assertLayoutStep(State.STEP_LAYOUT | State.STEP_ANIMATIONS);
mAdapterHelper.consumeUpdatesInOnePass();
mState.mItemCount=mAdapter.getItemCount();
mState.mDeletedInvisibleItemCountSincePreviousLayout=0;
if (mPendingSavedState != null && false) {
if (mPendingSavedState.mLayoutState != null) {
mLayout.onRestoreInstanceState(mPendingSavedState.mLayoutState);
}
mPendingSavedState=null;
}
mState.mInPreLayout=false;
mLayout.onLayoutChildren(mRecycler,mState);
mState.mStructureChanged=false;
mState.mRunSimpleAnimations=mState.mRunSimpleAnimations && mItemAnimator != null;
mState.mLayoutStep=State.STEP_ANIMATIONS;
onExitLayoutOrScroll();
stopInterceptRequestLayout(false);
}
private void dispatchLayoutStep3(){
mState.assertLayoutStep(State.STEP_ANIMATIONS);
startInterceptRequestLayout();
onEnterLayoutOrScroll();
mState.mLayoutStep=State.STEP_START;
if (mState.mRunSimpleAnimations) {
for (int i=mChildHelper.getChildCount() - 1; i >= 0; i--) {
ViewHolder holder=getChildViewHolderInt(mChildHelper.getChildAt(i));
if (holder.shouldIgnore()) {
continue;
}
long key=getChangedHolderKey(holder);
final ItemHolderInfo animationInfo=mItemAnimator.recordPostLayoutInformation(mState,holder);
ViewHolder oldChangeViewHolder=mViewInfoStore.getFromOldChangeHolders(key);
if (oldChangeViewHolder != null && !oldChangeViewHolder.shouldIgnore()) {
final boolean oldDisappearing=mViewInfoStore.isDisappearing(oldChangeViewHolder);
final boolean newDisappearing=mViewInfoStore.isDisappearing(holder);
if (oldDisappearing && oldChangeViewHolder == holder) {
mViewInfoStore.addToPostLayout(holder,animationInfo);
}
 else {
final ItemHolderInfo preInfo=mViewInfoStore.popFromPreLayout(oldChangeViewHolder);
mViewInfoStore.addToPostLayout(holder,animationInfo);
ItemHolderInfo postInfo=mViewInfoStore.popFromPostLayout(holder);
if (preInfo == null) {
handleMissingPreInfoForChangeError(key,holder,oldChangeViewHolder);
}
 else {
animateChange(oldChangeViewHolder,holder,preInfo,postInfo,oldDisappearing,newDisappearing);
}
}
}
 else {
mViewInfoStore.addToPostLayout(holder,animationInfo);
}
}
mViewInfoStore.process(mViewInfoProcessCallback);
}
mLayout.removeAndRecycleScrapInt(mRecycler);
mState.mPreviousLayoutItemCount=mState.mItemCount;
mDataSetHasChangedAfterLayout=false;
mDispatchItemsChangedEvent=false;
mState.mRunSimpleAnimations=false;
mState.mRunPredictiveAnimations=false;
mLayout.mRequestedSimpleAnimations=false;
if (mRecycler.mChangedScrap != null) {
mRecycler.mChangedScrap.clear();
}
if (mLayout.mPrefetchMaxObservedInInitialPrefetch) {
mLayout.mPrefetchMaxCountObserved=0;
mLayout.mPrefetchMaxObservedInInitialPrefetch=false;
mRecycler.updateViewCacheSize();
}
mLayout.onLayoutCompleted(mState);
onExitLayoutOrScroll();
stopInterceptRequestLayout(false);
mViewInfoStore.clear();
if (didChildRangeChange(mMinMaxLayoutPositions[0],mMinMaxLayoutPositions[1])) {
dispatchOnScrolled(0,0);
}
recoverFocusFromState();
resetFocusInfo();
}
private void handleMissingPreInfoForChangeError(long key,ViewHolder holder,ViewHolder oldChangeViewHolder){
final int childCount=mChildHelper.getChildCount();
for (int i=0; i < childCount; i++) {
View view=mChildHelper.getChildAt(i);
ViewHolder other=getChildViewHolderInt(view);
if (other == holder) {
continue;
}
final long otherKey=getChangedHolderKey(other);
if (otherKey == key) {
if (mAdapter != null && mAdapter.hasStableIds()) {
throw new IllegalStateException("Two different ViewHolders have the same stable" + " ID. Stable IDs in your adapter MUST BE unique and SHOULD NOT" + " change.\n ViewHolder 1:" + other + " \n View Holder 2:"+ holder+ exceptionLabel());
}
 else {
throw new IllegalStateException("Two different ViewHolders have the same change" + " ID. This might happen due to inconsistent Adapter update events or" + " if the LayoutManager lays out the same View multiple times."+ "\n ViewHolder 1:" + other + " \n View Holder 2:"+ holder+ exceptionLabel());
}
}
}
Log.e(TAG,"Problem while matching changed view holders with the new" + "ones. The pre-layout information for the change holder " + oldChangeViewHolder + " cannot be found but it is necessary for "+ holder+ exceptionLabel());
}
void recordAnimationInfoIfBouncedHiddenView(ViewHolder viewHolder,ItemHolderInfo animationInfo){
viewHolder.setFlags(0,ViewHolder.FLAG_BOUNCED_FROM_HIDDEN_LIST);
if (mState.mTrackOldChangeHolders && viewHolder.isUpdated() && !viewHolder.isRemoved()&& !viewHolder.shouldIgnore()) {
long key=getChangedHolderKey(viewHolder);
mViewInfoStore.addToOldChangeHolders(key,viewHolder);
}
mViewInfoStore.addToPreLayout(viewHolder,animationInfo);
}
private void findMinMaxChildLayoutPositions(int[] into){
final int count=mChildHelper.getChildCount();
if (count == 0) {
into[0]=NO_POSITION;
into[1]=NO_POSITION;
return;
}
int minPositionPreLayout=Integer.MAX_VALUE;
int maxPositionPreLayout=Integer.MIN_VALUE;
for (int i=0; i < count; ++i) {
final ViewHolder holder=getChildViewHolderInt(mChildHelper.getChildAt(i));
if (holder.shouldIgnore()) {
continue;
}
final int pos=holder.getLayoutPosition();
if (pos < minPositionPreLayout) {
minPositionPreLayout=pos;
}
if (pos > maxPositionPreLayout) {
maxPositionPreLayout=pos;
}
}
into[0]=minPositionPreLayout;
into[1]=maxPositionPreLayout;
}
private boolean didChildRangeChange(int minPositionPreLayout,int maxPositionPreLayout){
findMinMaxChildLayoutPositions(mMinMaxLayoutPositions);
return mMinMaxLayoutPositions[0] != minPositionPreLayout || mMinMaxLayoutPositions[1] != maxPositionPreLayout;
}
protected void removeDetachedView(View child,boolean animate){
ViewHolder vh=getChildViewHolderInt(child);
if (vh != null) {
if (vh.isTmpDetached()) {
vh.clearTmpDetachFlag();
}
 else if (!vh.shouldIgnore()) {
throw new IllegalArgumentException("Called removeDetachedView with a view which" + " is not flagged as tmp detached." + vh + exceptionLabel());
}
}
//child.clearAnimation();
dispatchChildDetached(child);
//super.removeDetachedView(child,animate);
}
long getChangedHolderKey(ViewHolder holder){
return mAdapter.hasStableIds() ? holder.getItemId() : holder.mPosition;
}
void animateAppearance(ViewHolder itemHolder,ItemHolderInfo preLayoutInfo,ItemHolderInfo postLayoutInfo){
itemHolder.setIsRecyclable(false);
if (mItemAnimator.animateAppearance(itemHolder,preLayoutInfo,postLayoutInfo)) {
postAnimationRunner();
}
}
void animateDisappearance(ViewHolder holder,ItemHolderInfo preLayoutInfo,ItemHolderInfo postLayoutInfo){
addAnimatingView(holder);
holder.setIsRecyclable(false);
if (mItemAnimator.animateDisappearance(holder,preLayoutInfo,postLayoutInfo)) {
postAnimationRunner();
}
}
private void animateChange(ViewHolder oldHolder,ViewHolder newHolder,ItemHolderInfo preInfo,ItemHolderInfo postInfo,boolean oldHolderDisappearing,boolean newHolderDisappearing){
oldHolder.setIsRecyclable(false);
if (oldHolderDisappearing) {
addAnimatingView(oldHolder);
}
if (oldHolder != newHolder) {
if (newHolderDisappearing) {
addAnimatingView(newHolder);
}
oldHolder.mShadowedHolder=newHolder;
addAnimatingView(oldHolder);
mRecycler.unscrapView(oldHolder);
newHolder.setIsRecyclable(false);
newHolder.mShadowingHolder=oldHolder;
}
if (mItemAnimator.animateChange(oldHolder,newHolder,preInfo,postInfo)) {
postAnimationRunner();
}
}
protected void onLayout(boolean changed,int l,int t,int r,int b){
TraceCompat.beginSection(TRACE_ON_LAYOUT_TAG);
dispatchLayout();
TraceCompat.endSection();
mFirstLayoutComplete=true;
}
void markItemDecorInsetsDirty(){
final int childCount=mChildHelper.getUnfilteredChildCount();
for (int i=0; i < childCount; i++) {
final View child=mChildHelper.getUnfilteredChildAt(i);
((LayoutParams)child.getLayoutParams()).mInsetsDirty=true;
}
mRecycler.markItemDecorInsetsDirty();
}
void saveOldPositions(){
final int childCount=mChildHelper.getUnfilteredChildCount();
for (int i=0; i < childCount; i++) {
final ViewHolder holder=getChildViewHolderInt(mChildHelper.getUnfilteredChildAt(i));
if (DEBUG && holder.mPosition == -1 && !holder.isRemoved()) {
throw new IllegalStateException("view holder cannot have position -1 unless it" + " is removed" + exceptionLabel());
}
if (!holder.shouldIgnore()) {
holder.saveOldPosition();
}
}
}
void clearOldPositions(){
final int childCount=mChildHelper.getUnfilteredChildCount();
for (int i=0; i < childCount; i++) {
final ViewHolder holder=getChildViewHolderInt(mChildHelper.getUnfilteredChildAt(i));
if (!holder.shouldIgnore()) {
holder.clearOldPosition();
}
}
mRecycler.clearOldPositions();
}
void offsetPositionRecordsForMove(int from,int to){
final int childCount=mChildHelper.getUnfilteredChildCount();
final int start, end, inBetweenOffset;
if (from < to) {
start=from;
end=to;
inBetweenOffset=-1;
}
 else {
start=to;
end=from;
inBetweenOffset=1;
}
for (int i=0; i < childCount; i++) {
final ViewHolder holder=getChildViewHolderInt(mChildHelper.getUnfilteredChildAt(i));
if (holder == null || holder.mPosition < start || holder.mPosition > end) {
continue;
}
if (DEBUG) {
Log.d(TAG,"offsetPositionRecordsForMove attached child " + i + " holder "+ holder);
}
if (holder.mPosition == from) {
holder.offsetPosition(to - from,false);
}
 else {
holder.offsetPosition(inBetweenOffset,false);
}
mState.mStructureChanged=true;
}
mRecycler.offsetPositionRecordsForMove(from,to);
requestLayout();
}
void offsetPositionRecordsForInsert(int positionStart,int itemCount){
final int childCount=mChildHelper.getUnfilteredChildCount();
for (int i=0; i < childCount; i++) {
final ViewHolder holder=getChildViewHolderInt(mChildHelper.getUnfilteredChildAt(i));
if (holder != null && !holder.shouldIgnore() && holder.mPosition >= positionStart) {
if (DEBUG) {
Log.d(TAG,"offsetPositionRecordsForInsert attached child " + i + " holder "+ holder+ " now at position "+ (holder.mPosition + itemCount));
}
holder.offsetPosition(itemCount,false);
mState.mStructureChanged=true;
}
}
mRecycler.offsetPositionRecordsForInsert(positionStart,itemCount);
requestLayout();
}
void offsetPositionRecordsForRemove(int positionStart,int itemCount,boolean applyToPreLayout){
final int positionEnd=positionStart + itemCount;
final int childCount=mChildHelper.getUnfilteredChildCount();
for (int i=0; i < childCount; i++) {
final ViewHolder holder=getChildViewHolderInt(mChildHelper.getUnfilteredChildAt(i));
if (holder != null && !holder.shouldIgnore()) {
if (holder.mPosition >= positionEnd) {
if (DEBUG) {
Log.d(TAG,"offsetPositionRecordsForRemove attached child " + i + " holder "+ holder+ " now at position "+ (holder.mPosition - itemCount));
}
holder.offsetPosition(-itemCount,applyToPreLayout);
mState.mStructureChanged=true;
}
 else if (holder.mPosition >= positionStart) {
if (DEBUG) {
Log.d(TAG,"offsetPositionRecordsForRemove attached child " + i + " holder "+ holder+ " now REMOVED");
}
holder.flagRemovedAndOffsetPosition(positionStart - 1,-itemCount,applyToPreLayout);
mState.mStructureChanged=true;
}
}
}
mRecycler.offsetPositionRecordsForRemove(positionStart,itemCount,applyToPreLayout);
requestLayout();
}
void viewRangeUpdate(int positionStart,int itemCount,Object payload){
final int childCount=mChildHelper.getUnfilteredChildCount();
final int positionEnd=positionStart + itemCount;
for (int i=0; i < childCount; i++) {
final View child=mChildHelper.getUnfilteredChildAt(i);
final ViewHolder holder=getChildViewHolderInt(child);
if (holder == null || holder.shouldIgnore()) {
continue;
}
if (holder.mPosition >= positionStart && holder.mPosition < positionEnd) {
holder.addFlags(ViewHolder.FLAG_UPDATE);
holder.addChangePayload(payload);
((LayoutParams)child.getLayoutParams()).mInsetsDirty=true;
}
}
mRecycler.viewRangeUpdate(positionStart,itemCount);
}
boolean canReuseUpdatedViewHolder(ViewHolder viewHolder){
return mItemAnimator == null || mItemAnimator.canReuseUpdatedViewHolder(viewHolder,viewHolder.getUnmodifiedPayloads());
}
void processDataSetCompletelyChanged(boolean dispatchItemsChanged){
mDispatchItemsChangedEvent|=dispatchItemsChanged;
mDataSetHasChangedAfterLayout=true;
markKnownViewsInvalid();
}
void markKnownViewsInvalid(){
final int childCount=mChildHelper.getUnfilteredChildCount();
for (int i=0; i < childCount; i++) {
final ViewHolder holder=getChildViewHolderInt(mChildHelper.getUnfilteredChildAt(i));
if (holder != null && !holder.shouldIgnore()) {
holder.addFlags(ViewHolder.FLAG_UPDATE | ViewHolder.FLAG_INVALID);
}
}
markItemDecorInsetsDirty();
mRecycler.markKnownViewsInvalid();
}
public ViewHolder getChildViewHolder(View child){
final ViewParent parent=child.getParent();
if (parent != null && parent != this) {
throw new IllegalArgumentException("View " + child + " is not a direct child of "+ this);
}
return getChildViewHolderInt(child);
}
public View findContainingItemView(View view){
ViewParent parent=view.getParent();
while (parent != null && parent != this && parent instanceof View) {
view=(View)parent;
parent=view.getParent();
}
return parent == this ? view : null;
}
static ViewHolder getChildViewHolderInt(View child){
if (child == null) {
return null;
}
return ((LayoutParams)child.getLayoutParams()).mViewHolder;
}
public ViewHolder findViewHolderForPosition(int position){
return findViewHolderForPosition(position,false);
}
ViewHolder findViewHolderForPosition(int position,boolean checkNewPosition){
final int childCount=mChildHelper.getUnfilteredChildCount();
ViewHolder hidden=null;
for (int i=0; i < childCount; i++) {
final ViewHolder holder=getChildViewHolderInt(mChildHelper.getUnfilteredChildAt(i));
if (holder != null && !holder.isRemoved()) {
if (checkNewPosition) {
if (holder.mPosition != position) {
continue;
}
}
 else if (holder.getLayoutPosition() != position) {
continue;
}
if (mChildHelper.isHidden(holder.itemView)) {
hidden=holder;
}
 else {
return holder;
}
}
}
return hidden;
}
public void offsetChildrenVertical(int dy){
final int childCount=mChildHelper.getChildCount();
for (int i=0; i < childCount; i++) {
mChildHelper.getChildAt(i).offsetTopAndBottom(dy);
}
}
public void onChildAttachedToWindow(View child){
}
public void onChildDetachedFromWindow(View child){
}
public void offsetChildrenHorizontal(int dx){
final int childCount=mChildHelper.getChildCount();
for (int i=0; i < childCount; i++) {
mChildHelper.getChildAt(i).offsetLeftAndRight(dx);
}
}
static void getDecoratedBoundsWithMarginsInt(View view,Rect outBounds){
final LayoutParams lp=(LayoutParams)view.getLayoutParams();
final Rect insets=lp.mDecorInsets;
outBounds.set(view.getLeft() - insets.left - lp.leftMargin,view.getTop() - insets.top - lp.topMargin,view.getRight() + insets.right + lp.rightMargin,view.getBottom() + insets.bottom + lp.bottomMargin);
}
Rect getItemDecorInsetsForChild(View child){
final LayoutParams lp=(LayoutParams)child.getLayoutParams();
if (!lp.mInsetsDirty) {
return lp.mDecorInsets;
}
if (mState.isPreLayout() && (lp.isItemChanged() || lp.isViewInvalid())) {
return lp.mDecorInsets;
}
final Rect insets=lp.mDecorInsets;
insets.set(0,0,0,0);
final int decorCount=mItemDecorations.size();
for (int i=0; i < decorCount; i++) {
mTempRect.set(0,0,0,0);
mItemDecorations.get(i).getItemOffsets(mTempRect,child,this,mState);
insets.left+=mTempRect.left;
insets.top+=mTempRect.top;
insets.right+=mTempRect.right;
insets.bottom+=mTempRect.bottom;
}
lp.mInsetsDirty=false;
return insets;
}
void dispatchOnScrolled(int hresult,int vresult){
mDispatchScrollCounter++;
final int scrollX=getScrollX();
final int scrollY=getScrollY();
onScrollChanged(scrollX,scrollY,scrollX - hresult,scrollY - vresult);
onScrolled(hresult,vresult);
if (mScrollListener != null) {
mScrollListener.onScrolled(this,hresult,vresult);
}
if (mScrollListeners != null) {
for (int i=mScrollListeners.size() - 1; i >= 0; i--) {
mScrollListeners.get(i).onScrolled(this,hresult,vresult);
}
}
mDispatchScrollCounter--;
}
public boolean hasPendingAdapterUpdates(){
return !mFirstLayoutComplete || mDataSetHasChangedAfterLayout || mAdapterHelper.hasPendingUpdates();
}
void repositionShadowingViews(){
int count=mChildHelper.getChildCount();
for (int i=0; i < count; i++) {
View view=mChildHelper.getChildAt(i);
ViewHolder holder=getChildViewHolder(view);
if (holder != null && holder.mShadowingHolder != null) {
View shadowingView=holder.mShadowingHolder.itemView;
int left=view.getLeft();
int top=view.getTop();
if (left != shadowingView.getLeft() || top != shadowingView.getTop()) {
shadowingView.layout(left,top,left + shadowingView.getWidth(),top + shadowingView.getHeight());
}
}
}
}
private class RecyclerViewDataObserver extends AdapterDataObserver {
RecyclerViewDataObserver(){
}
public void onChanged(){
assertNotInLayoutOrScroll(null);
mState.mStructureChanged=true;
processDataSetCompletelyChanged(true);
if (!mAdapterHelper.hasPendingUpdates()) {
requestLayout();
}
}
public void onItemRangeChanged(int positionStart,int itemCount,Object payload){
assertNotInLayoutOrScroll(null);
if (mAdapterHelper.onItemRangeChanged(positionStart,itemCount,payload)) {
triggerUpdateProcessor();
}
}
public void onItemRangeInserted(int positionStart,int itemCount){
assertNotInLayoutOrScroll(null);
if (mAdapterHelper.onItemRangeInserted(positionStart,itemCount)) {
triggerUpdateProcessor();
}
}
public void onItemRangeRemoved(int positionStart,int itemCount){
assertNotInLayoutOrScroll(null);
if (mAdapterHelper.onItemRangeRemoved(positionStart,itemCount)) {
triggerUpdateProcessor();
}
}
public void onItemRangeMoved(int fromPosition,int toPosition,int itemCount){
assertNotInLayoutOrScroll(null);
if (mAdapterHelper.onItemRangeMoved(fromPosition,toPosition,itemCount)) {
triggerUpdateProcessor();
}
}
void triggerUpdateProcessor(){
if (POST_UPDATES_ON_ANIMATION && mHasFixedSize && mIsAttached) {
//ViewCompat.postOnAnimation(RecyclerView.this,mUpdateChildViewsRunnable);
}
 else {
mAdapterUpdateDuringMeasure=true;
requestLayout();
}
}
public void onStateRestorationPolicyChanged(){
if (mPendingSavedState == null) {
return;
}
Adapter<?> adapter=mAdapter;
if (adapter != null && false) {
requestLayout();
}
}
}
public static class RecycledViewPool {
private static final int DEFAULT_MAX_SCRAP=5;
static class ScrapData {
final ArrayList<ViewHolder> mScrapHeap=new ArrayList<>();
int mMaxScrap=DEFAULT_MAX_SCRAP;
long mCreateRunningAverageNs=0;
long mBindRunningAverageNs=0;
}
SparseArray<ScrapData> mScrap=new SparseArray<>();
private int mAttachCount=0;
public void clear(){
for (int i=0; i < mScrap.size(); i++) {
ScrapData data=mScrap.valueAt(i);
data.mScrapHeap.clear();
}
}
public void setMaxRecycledViews(int viewType,int max){
ScrapData scrapData=getScrapDataForType(viewType);
scrapData.mMaxScrap=max;
final ArrayList<ViewHolder> scrapHeap=scrapData.mScrapHeap;
while (scrapHeap.size() > max) {
scrapHeap.remove(scrapHeap.size() - 1);
}
}
public int getRecycledViewCount(int viewType){
return getScrapDataForType(viewType).mScrapHeap.size();
}
public ViewHolder getRecycledView(int viewType){
final ScrapData scrapData=mScrap.get(viewType);
if (scrapData != null && !scrapData.mScrapHeap.isEmpty()) {
final ArrayList<ViewHolder> scrapHeap=scrapData.mScrapHeap;
for (int i=scrapHeap.size() - 1; i >= 0; i--) {
if (!scrapHeap.get(i).isAttachedToTransitionOverlay()) {
return scrapHeap.remove(i);
}
}
}
return null;
}
int size(){
int count=0;
for (int i=0; i < mScrap.size(); i++) {
ArrayList<ViewHolder> viewHolders=mScrap.valueAt(i).mScrapHeap;
if (viewHolders != null) {
count+=viewHolders.size();
}
}
return count;
}
public void putRecycledView(ViewHolder scrap){
final int viewType=scrap.getItemViewType();
final ArrayList<ViewHolder> scrapHeap=getScrapDataForType(viewType).mScrapHeap;
if (mScrap.get(viewType).mMaxScrap <= scrapHeap.size()) {
return;
}
if (DEBUG && scrapHeap.contains(scrap)) {
throw new IllegalArgumentException("this scrap item already exists");
}
scrap.resetInternal();
scrapHeap.add(scrap);
}
long runningAverage(long oldAverage,long newValue){
if (oldAverage == 0) {
return newValue;
}
return (oldAverage / 4 * 3) + (newValue / 4);
}
void factorInCreateTime(int viewType,long createTimeNs){
ScrapData scrapData=getScrapDataForType(viewType);
scrapData.mCreateRunningAverageNs=runningAverage(scrapData.mCreateRunningAverageNs,createTimeNs);
}
void factorInBindTime(int viewType,long bindTimeNs){
ScrapData scrapData=getScrapDataForType(viewType);
scrapData.mBindRunningAverageNs=runningAverage(scrapData.mBindRunningAverageNs,bindTimeNs);
}
boolean willCreateInTime(int viewType,long approxCurrentNs,long deadlineNs){
long expectedDurationNs=getScrapDataForType(viewType).mCreateRunningAverageNs;
return expectedDurationNs == 0 || (approxCurrentNs + expectedDurationNs < deadlineNs);
}
boolean willBindInTime(int viewType,long approxCurrentNs,long deadlineNs){
long expectedDurationNs=getScrapDataForType(viewType).mBindRunningAverageNs;
return expectedDurationNs == 0 || (approxCurrentNs + expectedDurationNs < deadlineNs);
}
void attach(){
mAttachCount++;
}
void detach(){
mAttachCount--;
}
void onAdapterChanged(Adapter oldAdapter,Adapter newAdapter,boolean compatibleWithPrevious){
if (oldAdapter != null) {
detach();
}
if (!compatibleWithPrevious && mAttachCount == 0) {
clear();
}
if (newAdapter != null) {
attach();
}
}
private ScrapData getScrapDataForType(int viewType){
ScrapData scrapData=mScrap.get(viewType);
if (scrapData == null) {
scrapData=new ScrapData();
mScrap.put(viewType,scrapData);
}
return scrapData;
}
}
static RecyclerView findNestedRecyclerView(View view){
if (!(view instanceof ViewGroup)) {
return null;
}
if (view instanceof RecyclerView) {
return (RecyclerView)view;
}
final ViewGroup parent=(ViewGroup)view;
final int count=parent.getChildCount();
for (int i=0; i < count; i++) {
final View child=parent.getChildAt(i);
final RecyclerView descendant=findNestedRecyclerView(child);
if (descendant != null) {
return descendant;
}
}
return null;
}
static void clearNestedRecyclerViewIfNotNested(ViewHolder holder){
if (holder.mNestedRecyclerView != null) {
View item=holder.mNestedRecyclerView.get();
while (item != null) {
if (item == holder.itemView) {
return;
}
ViewParent parent=item.getParent();
if (parent instanceof View) {
item=(View)parent;
}
 else {
item=null;
}
}
holder.mNestedRecyclerView=null;
}
}
long getNanoTime(){
if (ALLOW_THREAD_GAP_WORK) {
return System.nanoTime();
}
 else {
return 0;
}
}
public final class Recycler {
final ArrayList<ViewHolder> mAttachedScrap=new ArrayList<>();
ArrayList<ViewHolder> mChangedScrap=null;
final ArrayList<ViewHolder> mCachedViews=new ArrayList<ViewHolder>();
private final List<ViewHolder> mUnmodifiableAttachedScrap=Collections.unmodifiableList(mAttachedScrap);
private int mRequestedCacheMax=DEFAULT_CACHE_SIZE;
int mViewCacheMax=DEFAULT_CACHE_SIZE;
RecycledViewPool mRecyclerPool;
private ViewCacheExtension mViewCacheExtension;
static final int DEFAULT_CACHE_SIZE=2;
public void clear(){
mAttachedScrap.clear();
recycleAndClearCachedViews();
}
public void setViewCacheSize(int viewCount){
mRequestedCacheMax=viewCount;
updateViewCacheSize();
}
void updateViewCacheSize(){
int extraCache=mLayout != null ? mLayout.mPrefetchMaxCountObserved : 0;
mViewCacheMax=mRequestedCacheMax + extraCache;
for (int i=mCachedViews.size() - 1; i >= 0 && mCachedViews.size() > mViewCacheMax; i--) {
recycleCachedViewAt(i);
}
}
public List<ViewHolder> getScrapList(){
return mUnmodifiableAttachedScrap;
}
boolean validateViewHolderForOffsetPosition(ViewHolder holder){
if (holder.isRemoved()) {
if (DEBUG && !mState.isPreLayout()) {
throw new IllegalStateException("should not receive a removed view unless it" + " is pre layout" + exceptionLabel());
}
return mState.isPreLayout();
}
if (holder.mPosition < 0 || holder.mPosition >= mAdapter.getItemCount()) {
throw new IndexOutOfBoundsException("Inconsistency detected. Invalid view holder " + "adapter position" + holder + exceptionLabel());
}
if (!mState.isPreLayout()) {
final int type=mAdapter.getItemViewType(holder.mPosition);
if (type != holder.getItemViewType()) {
return false;
}
}
if (mAdapter.hasStableIds()) {
return holder.getItemId() == mAdapter.getItemId(holder.mPosition);
}
return true;
}
private boolean tryBindViewHolderByDeadline(ViewHolder holder,int offsetPosition,int position,long deadlineNs){
holder.mBindingAdapter=null;
holder.mOwnerRecyclerView=RecyclerView.this;
final int viewType=holder.getItemViewType();
long startBindNs=getNanoTime();
if (deadlineNs != FOREVER_NS && !mRecyclerPool.willBindInTime(viewType,startBindNs,deadlineNs)) {
return false;
}
mAdapter.bindViewHolder(holder,offsetPosition);
long endBindNs=getNanoTime();
mRecyclerPool.factorInBindTime(holder.getItemViewType(),endBindNs - startBindNs);
//attachAccessibilityDelegateOnBind(holder);
if (mState.isPreLayout()) {
holder.mPreLayoutPosition=position;
}
return true;
}
public void bindViewToPosition(View view,int position){
ViewHolder holder=getChildViewHolderInt(view);
if (holder == null) {
throw new IllegalArgumentException("The view does not have a ViewHolder. You cannot" + " pass arbitrary views to this method, they should be created by the " + "Adapter"+ exceptionLabel());
}
final int offsetPosition=mAdapterHelper.findPositionOffset(position);
if (offsetPosition < 0 || offsetPosition >= mAdapter.getItemCount()) {
throw new IndexOutOfBoundsException("Inconsistency detected. Invalid item " + "position " + position + "(offset:"+ offsetPosition+ ")."+ "state:"+ mState.getItemCount()+ exceptionLabel());
}
tryBindViewHolderByDeadline(holder,offsetPosition,position,FOREVER_NS);
final ViewGroup.LayoutParams lp=holder.itemView.getLayoutParams();
final LayoutParams rvLayoutParams;
if (lp == null) {
rvLayoutParams=(LayoutParams)generateDefaultLayoutParams();
holder.itemView.setLayoutParams(rvLayoutParams);
}
 else if (!checkLayoutParams(lp)) {
rvLayoutParams=(LayoutParams)generateLayoutParams(lp);
holder.itemView.setLayoutParams(rvLayoutParams);
}
 else {
rvLayoutParams=(LayoutParams)lp;
}
rvLayoutParams.mInsetsDirty=true;
rvLayoutParams.mViewHolder=holder;
rvLayoutParams.mPendingInvalidate=holder.itemView.getParent() == null;
}
public int convertPreLayoutPositionToPostLayout(int position){
if (position < 0 || position >= mState.getItemCount()) {
throw new IndexOutOfBoundsException("invalid position " + position + ". State "+ "item count is "+ mState.getItemCount()+ exceptionLabel());
}
if (!mState.isPreLayout()) {
return position;
}
return mAdapterHelper.findPositionOffset(position);
}
public View getViewForPosition(int position){
return getViewForPosition(position,false);
}
View getViewForPosition(int position,boolean dryRun){
return tryGetViewHolderForPositionByDeadline(position,dryRun,FOREVER_NS).itemView;
}
ViewHolder tryGetViewHolderForPositionByDeadline(int position,boolean dryRun,long deadlineNs){
if (position < 0 || position >= mState.getItemCount()) {
throw new IndexOutOfBoundsException("Invalid item position " + position + "("+ position+ "). Item count:"+ mState.getItemCount()+ exceptionLabel());
}
boolean fromScrapOrHiddenOrCache=false;
ViewHolder holder=null;
if (mState.isPreLayout()) {
holder=getChangedScrapViewForPosition(position);
fromScrapOrHiddenOrCache=holder != null;
}
if (holder == null) {
holder=getScrapOrHiddenOrCachedHolderForPosition(position,dryRun);
if (holder != null) {
if (!validateViewHolderForOffsetPosition(holder)) {
if (!dryRun) {
holder.addFlags(ViewHolder.FLAG_INVALID);
if (holder.isScrap()) {
removeDetachedView(holder.itemView,false);
holder.unScrap();
}
 else if (holder.wasReturnedFromScrap()) {
holder.clearReturnedFromScrapFlag();
}
recycleViewHolderInternal(holder);
}
holder=null;
}
 else {
fromScrapOrHiddenOrCache=true;
}
}
}
if (holder == null) {
final int offsetPosition=mAdapterHelper.findPositionOffset(position);
if (offsetPosition < 0 || offsetPosition >= mAdapter.getItemCount()) {
throw new IndexOutOfBoundsException("Inconsistency detected. Invalid item " + "position " + position + "(offset:"+ offsetPosition+ ")."+ "state:"+ mState.getItemCount()+ exceptionLabel());
}
final int type=mAdapter.getItemViewType(offsetPosition);
if (mAdapter.hasStableIds()) {
holder=getScrapOrCachedViewForId(mAdapter.getItemId(offsetPosition),type,dryRun);
if (holder != null) {
holder.mPosition=offsetPosition;
fromScrapOrHiddenOrCache=true;
}
}
if (holder == null && mViewCacheExtension != null) {
final View view=mViewCacheExtension.getViewForPositionAndType(this,position,type);
if (view != null) {
holder=getChildViewHolder(view);
if (holder == null) {
throw new IllegalArgumentException("getViewForPositionAndType returned" + " a view which does not have a ViewHolder" + exceptionLabel());
}
 else if (holder.shouldIgnore()) {
throw new IllegalArgumentException("getViewForPositionAndType returned" + " a view that is ignored. You must call stopIgnoring before" + " returning this view."+ exceptionLabel());
}
}
}
if (holder == null) {
if (DEBUG) {
Log.d(TAG,"tryGetViewHolderForPositionByDeadline(" + position + ") fetching from shared pool");
}
holder=getRecycledViewPool().getRecycledView(type);
if (holder != null) {
holder.resetInternal();
if (FORCE_INVALIDATE_DISPLAY_LIST) {
invalidateDisplayListInt(holder);
}
}
}
if (holder == null) {
long start=getNanoTime();
if (deadlineNs != FOREVER_NS && !mRecyclerPool.willCreateInTime(type,start,deadlineNs)) {
return null;
}
holder=mAdapter.createViewHolder(RecyclerView.this,type);
if (ALLOW_THREAD_GAP_WORK) {
RecyclerView innerView=findNestedRecyclerView(holder.itemView);
if (innerView != null) {
holder.mNestedRecyclerView=new WeakReference<>(innerView);
}
}
long end=getNanoTime();
mRecyclerPool.factorInCreateTime(type,end - start);
if (DEBUG) {
Log.d(TAG,"tryGetViewHolderForPositionByDeadline created new ViewHolder");
}
}
}
if (fromScrapOrHiddenOrCache && !mState.isPreLayout() && holder.hasAnyOfTheFlags(ViewHolder.FLAG_BOUNCED_FROM_HIDDEN_LIST)) {
holder.setFlags(0,ViewHolder.FLAG_BOUNCED_FROM_HIDDEN_LIST);
if (mState.mRunSimpleAnimations) {
int changeFlags=ItemAnimator.buildAdapterChangeFlagsForAnimations(holder);
changeFlags|=ItemAnimator.FLAG_APPEARED_IN_PRE_LAYOUT;
final ItemHolderInfo info=mItemAnimator.recordPreLayoutInformation(mState,holder,changeFlags,holder.getUnmodifiedPayloads());
recordAnimationInfoIfBouncedHiddenView(holder,info);
}
}
boolean bound=false;
if (mState.isPreLayout() && holder.isBound()) {
holder.mPreLayoutPosition=position;
}
 else if (!holder.isBound() || holder.needsUpdate() || holder.isInvalid()) {
if (DEBUG && holder.isRemoved()) {
throw new IllegalStateException("Removed holder should be bound and it should" + " come here only in pre-layout. Holder: " + holder + exceptionLabel());
}
final int offsetPosition=mAdapterHelper.findPositionOffset(position);
bound=tryBindViewHolderByDeadline(holder,offsetPosition,position,deadlineNs);
}
final ViewGroup.LayoutParams lp=holder.itemView.getLayoutParams();
final LayoutParams rvLayoutParams;
if (lp == null) {
rvLayoutParams=(LayoutParams)generateDefaultLayoutParams();
holder.itemView.setLayoutParams(rvLayoutParams);
}
 else if (!checkLayoutParams(lp)) {
rvLayoutParams=(LayoutParams)generateLayoutParams(lp);
holder.itemView.setLayoutParams(rvLayoutParams);
}
 else {
rvLayoutParams=(LayoutParams)lp;
}
rvLayoutParams.mViewHolder=holder;
rvLayoutParams.mPendingInvalidate=fromScrapOrHiddenOrCache && bound;
return holder;
}
private void invalidateDisplayListInt(ViewHolder holder){
if (holder.itemView instanceof ViewGroup) {
invalidateDisplayListInt((ViewGroup)holder.itemView,false);
}
}
private void invalidateDisplayListInt(ViewGroup viewGroup,boolean invalidateThis){
for (int i=viewGroup.getChildCount() - 1; i >= 0; i--) {
final View view=viewGroup.getChildAt(i);
if (view instanceof ViewGroup) {
invalidateDisplayListInt((ViewGroup)view,true);
}
}
if (!invalidateThis) {
return;
}
if (viewGroup.getVisibility() == View.INVISIBLE) {
viewGroup.setVisibility(View.VISIBLE);
viewGroup.setVisibility(View.INVISIBLE);
}
 else {
final int visibility=viewGroup.getVisibility();
viewGroup.setVisibility(View.INVISIBLE);
viewGroup.setVisibility(visibility);
}
}
public void recycleView(View view){
ViewHolder holder=getChildViewHolderInt(view);
if (holder.isTmpDetached()) {
removeDetachedView(view,false);
}
if (holder.isScrap()) {
holder.unScrap();
}
 else if (holder.wasReturnedFromScrap()) {
holder.clearReturnedFromScrapFlag();
}
recycleViewHolderInternal(holder);
if (mItemAnimator != null && !holder.isRecyclable()) {
mItemAnimator.endAnimation(holder);
}
}
void recycleAndClearCachedViews(){
final int count=mCachedViews.size();
for (int i=count - 1; i >= 0; i--) {
recycleCachedViewAt(i);
}
mCachedViews.clear();
if (ALLOW_THREAD_GAP_WORK) {
mPrefetchRegistry.clearPrefetchPositions();
}
}
void recycleCachedViewAt(int cachedViewIndex){
if (DEBUG) {
Log.d(TAG,"Recycling cached view at index " + cachedViewIndex);
}
ViewHolder viewHolder=mCachedViews.get(cachedViewIndex);
if (DEBUG) {
Log.d(TAG,"CachedViewHolder to be recycled: " + viewHolder);
}
addViewHolderToRecycledViewPool(viewHolder,true);
mCachedViews.remove(cachedViewIndex);
}
void recycleViewHolderInternal(ViewHolder holder){
if (holder.isScrap() || holder.itemView.getParent() != null) {
throw new IllegalArgumentException("Scrapped or attached views may not be recycled. isScrap:" + holder.isScrap() + " isAttached:"+ (holder.itemView.getParent() != null)+ exceptionLabel());
}
if (holder.isTmpDetached()) {
throw new IllegalArgumentException("Tmp detached view should be removed " + "from RecyclerView before it can be recycled: " + holder + exceptionLabel());
}
if (holder.shouldIgnore()) {
throw new IllegalArgumentException("Trying to recycle an ignored view holder. You" + " should first call stopIgnoringView(view) before calling recycle." + exceptionLabel());
}
final boolean transientStatePreventsRecycling=holder.doesTransientStatePreventRecycling();
final boolean forceRecycle=mAdapter != null && transientStatePreventsRecycling && mAdapter.onFailedToRecycleView(holder);
boolean cached=false;
boolean recycled=false;
if (DEBUG && mCachedViews.contains(holder)) {
throw new IllegalArgumentException("cached view received recycle internal? " + holder + exceptionLabel());
}
if (forceRecycle || holder.isRecyclable()) {
if (mViewCacheMax > 0 && !holder.hasAnyOfTheFlags(ViewHolder.FLAG_INVALID | ViewHolder.FLAG_REMOVED | ViewHolder.FLAG_UPDATE| ViewHolder.FLAG_ADAPTER_POSITION_UNKNOWN)) {
int cachedViewSize=mCachedViews.size();
if (cachedViewSize >= mViewCacheMax && cachedViewSize > 0) {
recycleCachedViewAt(0);
cachedViewSize--;
}
int targetCacheIndex=cachedViewSize;
if (ALLOW_THREAD_GAP_WORK && cachedViewSize > 0 && !mPrefetchRegistry.lastPrefetchIncludedPosition(holder.mPosition)) {
int cacheIndex=cachedViewSize - 1;
while (cacheIndex >= 0) {
int cachedPos=mCachedViews.get(cacheIndex).mPosition;
if (!mPrefetchRegistry.lastPrefetchIncludedPosition(cachedPos)) {
break;
}
cacheIndex--;
}
targetCacheIndex=cacheIndex + 1;
}
mCachedViews.add(targetCacheIndex,holder);
cached=true;
}
if (!cached) {
addViewHolderToRecycledViewPool(holder,true);
recycled=true;
}
}
 else {
if (DEBUG) {
Log.d(TAG,"trying to recycle a non-recycleable holder. Hopefully, it will " + "re-visit here. We are still removing it from animation lists" + exceptionLabel());
}
}
mViewInfoStore.removeViewHolder(holder);
if (!cached && !recycled && transientStatePreventsRecycling) {
holder.mBindingAdapter=null;
holder.mOwnerRecyclerView=null;
}
}
void addViewHolderToRecycledViewPool(ViewHolder holder,boolean dispatchRecycled){
clearNestedRecyclerViewIfNotNested(holder);
View itemView=holder.itemView;
if (false) {
//AccessibilityDelegateCompat itemDelegate=mAccessibilityDelegate.getItemDelegate();
//AccessibilityDelegateCompat //originalDelegate=null;
if (false) {
//originalDelegate=((RecyclerViewAccessibilityDelegate.ItemDelegate)itemDelegate).getAndRemoveOriginalDelegateForItem(itemView);
}
//ViewCompat.setAccessibilityDelegate(itemView,originalDelegate);
}
if (dispatchRecycled) {
dispatchViewRecycled(holder);
}
holder.mBindingAdapter=null;
holder.mOwnerRecyclerView=null;
getRecycledViewPool().putRecycledView(holder);
}
void quickRecycleScrapView(View view){
final ViewHolder holder=getChildViewHolderInt(view);
holder.mScrapContainer=null;
holder.mInChangeScrap=false;
holder.clearReturnedFromScrapFlag();
recycleViewHolderInternal(holder);
}
void scrapView(View view){
final ViewHolder holder=getChildViewHolderInt(view);
if (holder.hasAnyOfTheFlags(ViewHolder.FLAG_REMOVED | ViewHolder.FLAG_INVALID) || !holder.isUpdated() || canReuseUpdatedViewHolder(holder)) {
if (holder.isInvalid() && !holder.isRemoved() && !mAdapter.hasStableIds()) {
throw new IllegalArgumentException("Called scrap view with an invalid view." + " Invalid views cannot be reused from scrap, they should rebound from" + " recycler pool."+ exceptionLabel());
}
holder.setScrapContainer(this,false);
mAttachedScrap.add(holder);
}
 else {
if (mChangedScrap == null) {
mChangedScrap=new ArrayList<ViewHolder>();
}
holder.setScrapContainer(this,true);
mChangedScrap.add(holder);
}
}
void unscrapView(ViewHolder holder){
if (holder.mInChangeScrap) {
mChangedScrap.remove(holder);
}
 else {
mAttachedScrap.remove(holder);
}
holder.mScrapContainer=null;
holder.mInChangeScrap=false;
holder.clearReturnedFromScrapFlag();
}
int getScrapCount(){
return mAttachedScrap.size();
}
View getScrapViewAt(int index){
return mAttachedScrap.get(index).itemView;
}
void clearScrap(){
mAttachedScrap.clear();
if (mChangedScrap != null) {
mChangedScrap.clear();
}
}
ViewHolder getChangedScrapViewForPosition(int position){
final int changedScrapSize;
if (mChangedScrap == null || (changedScrapSize=mChangedScrap.size()) == 0) {
return null;
}
for (int i=0; i < changedScrapSize; i++) {
final ViewHolder holder=mChangedScrap.get(i);
if (!holder.wasReturnedFromScrap() && holder.getLayoutPosition() == position) {
holder.addFlags(ViewHolder.FLAG_RETURNED_FROM_SCRAP);
return holder;
}
}
if (mAdapter.hasStableIds()) {
final int offsetPosition=mAdapterHelper.findPositionOffset(position);
if (offsetPosition > 0 && offsetPosition < mAdapter.getItemCount()) {
final long id=mAdapter.getItemId(offsetPosition);
for (int i=0; i < changedScrapSize; i++) {
final ViewHolder holder=mChangedScrap.get(i);
if (!holder.wasReturnedFromScrap() && holder.getItemId() == id) {
holder.addFlags(ViewHolder.FLAG_RETURNED_FROM_SCRAP);
return holder;
}
}
}
}
return null;
}
ViewHolder getScrapOrHiddenOrCachedHolderForPosition(int position,boolean dryRun){
final int scrapCount=mAttachedScrap.size();
for (int i=0; i < scrapCount; i++) {
final ViewHolder holder=mAttachedScrap.get(i);
if (!holder.wasReturnedFromScrap() && holder.getLayoutPosition() == position && !holder.isInvalid() && (mState.mInPreLayout || !holder.isRemoved())) {
holder.addFlags(ViewHolder.FLAG_RETURNED_FROM_SCRAP);
return holder;
}
}
if (!dryRun) {
View view=mChildHelper.findHiddenNonRemovedView(position);
if (view != null) {
final ViewHolder vh=getChildViewHolderInt(view);
mChildHelper.unhide(view);
int layoutIndex=mChildHelper.indexOfChild(view);
if (layoutIndex == RecyclerView.NO_POSITION) {
throw new IllegalStateException("layout index should not be -1 after " + "unhiding a view:" + vh + exceptionLabel());
}
mChildHelper.detachViewFromParent(layoutIndex);
scrapView(view);
vh.addFlags(ViewHolder.FLAG_RETURNED_FROM_SCRAP | ViewHolder.FLAG_BOUNCED_FROM_HIDDEN_LIST);
return vh;
}
}
final int cacheSize=mCachedViews.size();
for (int i=0; i < cacheSize; i++) {
final ViewHolder holder=mCachedViews.get(i);
if (!holder.isInvalid() && holder.getLayoutPosition() == position && !holder.isAttachedToTransitionOverlay()) {
if (!dryRun) {
mCachedViews.remove(i);
}
if (DEBUG) {
Log.d(TAG,"getScrapOrHiddenOrCachedHolderForPosition(" + position + ") found match in cache: "+ holder);
}
return holder;
}
}
return null;
}
ViewHolder getScrapOrCachedViewForId(long id,int type,boolean dryRun){
final int count=mAttachedScrap.size();
for (int i=count - 1; i >= 0; i--) {
final ViewHolder holder=mAttachedScrap.get(i);
if (holder.getItemId() == id && !holder.wasReturnedFromScrap()) {
if (type == holder.getItemViewType()) {
holder.addFlags(ViewHolder.FLAG_RETURNED_FROM_SCRAP);
if (holder.isRemoved()) {
if (!mState.isPreLayout()) {
holder.setFlags(ViewHolder.FLAG_UPDATE,ViewHolder.FLAG_UPDATE | ViewHolder.FLAG_INVALID | ViewHolder.FLAG_REMOVED);
}
}
return holder;
}
 else if (!dryRun) {
mAttachedScrap.remove(i);
removeDetachedView(holder.itemView,false);
quickRecycleScrapView(holder.itemView);
}
}
}
final int cacheSize=mCachedViews.size();
for (int i=cacheSize - 1; i >= 0; i--) {
final ViewHolder holder=mCachedViews.get(i);
if (holder.getItemId() == id && !holder.isAttachedToTransitionOverlay()) {
if (type == holder.getItemViewType()) {
if (!dryRun) {
mCachedViews.remove(i);
}
return holder;
}
 else if (!dryRun) {
recycleCachedViewAt(i);
return null;
}
}
}
return null;
}
void dispatchViewRecycled(ViewHolder holder){
if (mRecyclerListener != null) {
mRecyclerListener.onViewRecycled(holder);
}
final int listenerCount=mRecyclerListeners.size();
for (int i=0; i < listenerCount; i++) {
mRecyclerListeners.get(i).onViewRecycled(holder);
}
if (mAdapter != null) {
mAdapter.onViewRecycled(holder);
}
if (mState != null) {
mViewInfoStore.removeViewHolder(holder);
}
if (DEBUG) Log.d(TAG,"dispatchViewRecycled: " + holder);
}
void onAdapterChanged(Adapter oldAdapter,Adapter newAdapter,boolean compatibleWithPrevious){
clear();
getRecycledViewPool().onAdapterChanged(oldAdapter,newAdapter,compatibleWithPrevious);
}
void offsetPositionRecordsForMove(int from,int to){
final int start, end, inBetweenOffset;
if (from < to) {
start=from;
end=to;
inBetweenOffset=-1;
}
 else {
start=to;
end=from;
inBetweenOffset=1;
}
final int cachedCount=mCachedViews.size();
for (int i=0; i < cachedCount; i++) {
final ViewHolder holder=mCachedViews.get(i);
if (holder == null || holder.mPosition < start || holder.mPosition > end) {
continue;
}
if (holder.mPosition == from) {
holder.offsetPosition(to - from,false);
}
 else {
holder.offsetPosition(inBetweenOffset,false);
}
if (DEBUG) {
Log.d(TAG,"offsetPositionRecordsForMove cached child " + i + " holder "+ holder);
}
}
}
void offsetPositionRecordsForInsert(int insertedAt,int count){
final int cachedCount=mCachedViews.size();
for (int i=0; i < cachedCount; i++) {
final ViewHolder holder=mCachedViews.get(i);
if (holder != null && holder.mPosition >= insertedAt) {
if (DEBUG) {
Log.d(TAG,"offsetPositionRecordsForInsert cached " + i + " holder "+ holder+ " now at position "+ (holder.mPosition + count));
}
holder.offsetPosition(count,false);
}
}
}
void offsetPositionRecordsForRemove(int removedFrom,int count,boolean applyToPreLayout){
final int removedEnd=removedFrom + count;
final int cachedCount=mCachedViews.size();
for (int i=cachedCount - 1; i >= 0; i--) {
final ViewHolder holder=mCachedViews.get(i);
if (holder != null) {
if (holder.mPosition >= removedEnd) {
if (DEBUG) {
Log.d(TAG,"offsetPositionRecordsForRemove cached " + i + " holder "+ holder+ " now at position "+ (holder.mPosition - count));
}
holder.offsetPosition(-count,applyToPreLayout);
}
 else if (holder.mPosition >= removedFrom) {
holder.addFlags(ViewHolder.FLAG_REMOVED);
recycleCachedViewAt(i);
}
}
}
}
void setViewCacheExtension(ViewCacheExtension extension){
mViewCacheExtension=extension;
}
void setRecycledViewPool(RecycledViewPool pool){
if (mRecyclerPool != null) {
mRecyclerPool.detach();
}
mRecyclerPool=pool;
if (mRecyclerPool != null && getAdapter() != null) {
mRecyclerPool.attach();
}
}
RecycledViewPool getRecycledViewPool(){
if (mRecyclerPool == null) {
mRecyclerPool=new RecycledViewPool();
}
return mRecyclerPool;
}
void viewRangeUpdate(int positionStart,int itemCount){
final int positionEnd=positionStart + itemCount;
final int cachedCount=mCachedViews.size();
for (int i=cachedCount - 1; i >= 0; i--) {
final ViewHolder holder=mCachedViews.get(i);
if (holder == null) {
continue;
}
final int pos=holder.mPosition;
if (pos >= positionStart && pos < positionEnd) {
holder.addFlags(ViewHolder.FLAG_UPDATE);
recycleCachedViewAt(i);
}
}
}
void markKnownViewsInvalid(){
final int cachedCount=mCachedViews.size();
for (int i=0; i < cachedCount; i++) {
final ViewHolder holder=mCachedViews.get(i);
if (holder != null) {
holder.addFlags(ViewHolder.FLAG_UPDATE | ViewHolder.FLAG_INVALID);
holder.addChangePayload(null);
}
}
if (mAdapter == null || !mAdapter.hasStableIds()) {
recycleAndClearCachedViews();
}
}
void clearOldPositions(){
final int cachedCount=mCachedViews.size();
for (int i=0; i < cachedCount; i++) {
final ViewHolder holder=mCachedViews.get(i);
holder.clearOldPosition();
}
final int scrapCount=mAttachedScrap.size();
for (int i=0; i < scrapCount; i++) {
mAttachedScrap.get(i).clearOldPosition();
}
if (mChangedScrap != null) {
final int changedScrapCount=mChangedScrap.size();
for (int i=0; i < changedScrapCount; i++) {
mChangedScrap.get(i).clearOldPosition();
}
}
}
void markItemDecorInsetsDirty(){
final int cachedCount=mCachedViews.size();
for (int i=0; i < cachedCount; i++) {
final ViewHolder holder=mCachedViews.get(i);
LayoutParams layoutParams=(LayoutParams)holder.itemView.getLayoutParams();
if (layoutParams != null) {
layoutParams.mInsetsDirty=true;
}
}
}
}
public abstract static class ViewCacheExtension {
public abstract View getViewForPositionAndType(Recycler recycler,int position,int type);
}
public abstract static class Adapter<VH extends ViewHolder> {
private final AdapterDataObservable mObservable=new AdapterDataObservable();
private boolean mHasStableIds=false;
public abstract VH onCreateViewHolder(ViewGroup parent,int viewType);
public abstract void onBindViewHolder(VH holder,int position);
public void onBindViewHolder(VH holder,int position,List<Object> payloads){
onBindViewHolder(holder,position);
}
public int findRelativeAdapterPositionIn(Adapter<? extends ViewHolder> adapter,ViewHolder viewHolder,int localPosition){
if (adapter == this) {
return localPosition;
}
return NO_POSITION;
}
public final VH createViewHolder(ViewGroup parent,int viewType){
try {
TraceCompat.beginSection(TRACE_CREATE_VIEW_TAG);
final VH holder=onCreateViewHolder(parent,viewType);
if (holder.itemView.getParent() != null) {
//throw new IllegalStateException("ViewHolder views must not be attached when" + " created. Ensure that you are not passing 'true' to the attachToRoot" + " parameter of LayoutInflater.inflate(..., boolean attachToRoot)");
}
holder.mItemViewType=viewType;
return holder;
}
  finally {
TraceCompat.endSection();
}
}
public final void bindViewHolder(VH holder,int position){
boolean rootBind=holder.mBindingAdapter == null;
if (rootBind) {
holder.mPosition=position;
if (hasStableIds()) {
holder.mItemId=getItemId(position);
}
holder.setFlags(ViewHolder.FLAG_BOUND,ViewHolder.FLAG_BOUND | ViewHolder.FLAG_UPDATE | ViewHolder.FLAG_INVALID| ViewHolder.FLAG_ADAPTER_POSITION_UNKNOWN);
TraceCompat.beginSection(TRACE_BIND_VIEW_TAG);
}
holder.mBindingAdapter=this;
onBindViewHolder(holder,position,holder.getUnmodifiedPayloads());
if (rootBind) {
holder.clearPayload();
final ViewGroup.LayoutParams layoutParams=holder.itemView.getLayoutParams();
if (layoutParams instanceof RecyclerView.LayoutParams) {
((LayoutParams)layoutParams).mInsetsDirty=true;
}
TraceCompat.endSection();
}
}
public int getItemViewType(int position){
return 0;
}
public void setHasStableIds(boolean hasStableIds){
if (hasObservers()) {
throw new IllegalStateException("Cannot change whether this adapter has " + "stable IDs while the adapter has registered observers.");
}
mHasStableIds=hasStableIds;
}
public long getItemId(int position){
return NO_ID;
}
public abstract int getItemCount();
public final boolean hasStableIds(){
return mHasStableIds;
}
public void onViewRecycled(VH holder){
}
public boolean onFailedToRecycleView(VH holder){
return false;
}
public void onViewAttachedToWindow(VH holder){
}
public void onViewDetachedFromWindow(VH holder){
}
public final boolean hasObservers(){
return mObservable.hasObservers();
}
public void registerAdapterDataObserver(AdapterDataObserver observer){
mObservable.registerObserver(observer);
}
public void unregisterAdapterDataObserver(AdapterDataObserver observer){
mObservable.unregisterObserver(observer);
}
public void onAttachedToRecyclerView(RecyclerView recyclerView){
}
public void onDetachedFromRecyclerView(RecyclerView recyclerView){
}
public final void notifyDataSetChanged(){
mObservable.notifyChanged();
}
public final void notifyItemChanged(int position){
mObservable.notifyItemRangeChanged(position,1);
}
public final void notifyItemChanged(int position,Object payload){
mObservable.notifyItemRangeChanged(position,1,payload);
}
public final void notifyItemRangeChanged(int positionStart,int itemCount){
mObservable.notifyItemRangeChanged(positionStart,itemCount);
}
public final void notifyItemRangeChanged(int positionStart,int itemCount,Object payload){
mObservable.notifyItemRangeChanged(positionStart,itemCount,payload);
}
public final void notifyItemInserted(int position){
mObservable.notifyItemRangeInserted(position,1);
}
public final void notifyItemMoved(int fromPosition,int toPosition){
mObservable.notifyItemMoved(fromPosition,toPosition);
}
public final void notifyItemRangeInserted(int positionStart,int itemCount){
mObservable.notifyItemRangeInserted(positionStart,itemCount);
}
public final void notifyItemRemoved(int position){
mObservable.notifyItemRangeRemoved(position,1);
}
public final void notifyItemRangeRemoved(int positionStart,int itemCount){
mObservable.notifyItemRangeRemoved(positionStart,itemCount);
}
public enum StateRestorationPolicy {ALLOW, PREVENT_WHEN_EMPTY, PREVENT}
}
void dispatchChildDetached(View child){
final ViewHolder viewHolder=getChildViewHolderInt(child);
onChildDetachedFromWindow(child);
if (mAdapter != null && viewHolder != null) {
mAdapter.onViewDetachedFromWindow(viewHolder);
}
if (mOnChildAttachStateListeners != null) {
final int cnt=mOnChildAttachStateListeners.size();
for (int i=cnt - 1; i >= 0; i--) {
mOnChildAttachStateListeners.get(i).onChildViewDetachedFromWindow(child);
}
}
}
void dispatchChildAttached(View child){
final ViewHolder viewHolder=getChildViewHolderInt(child);
onChildAttachedToWindow(child);
if (mAdapter != null && viewHolder != null) {
mAdapter.onViewAttachedToWindow(viewHolder);
}
if (mOnChildAttachStateListeners != null) {
final int cnt=mOnChildAttachStateListeners.size();
for (int i=cnt - 1; i >= 0; i--) {
mOnChildAttachStateListeners.get(i).onChildViewAttachedToWindow(child);
}
}
}
public abstract static class LayoutManager {
ChildHelper mChildHelper;
RecyclerView mRecyclerView;
private final ViewBoundsCheck.Callback mHorizontalBoundCheckCallback=new ViewBoundsCheck.Callback(){
public View getChildAt(int index){
return LayoutManager.this.getChildAt(index);
}
public int getParentStart(){
return LayoutManager.this.getPaddingLeft();
}
public int getParentEnd(){
return LayoutManager.this.getWidth() - LayoutManager.this.getPaddingRight();
}
public int getChildStart(View view){
final RecyclerView.LayoutParams params=(RecyclerView.LayoutParams)view.getLayoutParams();
return LayoutManager.this.getDecoratedLeft(view) - params.leftMargin;
}
public int getChildEnd(View view){
final RecyclerView.LayoutParams params=(RecyclerView.LayoutParams)view.getLayoutParams();
return LayoutManager.this.getDecoratedRight(view) + params.rightMargin;
}
}
;
private final ViewBoundsCheck.Callback mVerticalBoundCheckCallback=new ViewBoundsCheck.Callback(){
public View getChildAt(int index){
return LayoutManager.this.getChildAt(index);
}
public int getParentStart(){
return LayoutManager.this.getPaddingTop();
}
public int getParentEnd(){
return LayoutManager.this.getHeight() - LayoutManager.this.getPaddingBottom();
}
public int getChildStart(View view){
final RecyclerView.LayoutParams params=(RecyclerView.LayoutParams)view.getLayoutParams();
return LayoutManager.this.getDecoratedTop(view) - params.topMargin;
}
public int getChildEnd(View view){
final RecyclerView.LayoutParams params=(RecyclerView.LayoutParams)view.getLayoutParams();
return LayoutManager.this.getDecoratedBottom(view) + params.bottomMargin;
}
}
;
ViewBoundsCheck mHorizontalBoundCheck=new ViewBoundsCheck(mHorizontalBoundCheckCallback);
ViewBoundsCheck mVerticalBoundCheck=new ViewBoundsCheck(mVerticalBoundCheckCallback);
boolean mRequestedSimpleAnimations=false;
boolean mIsAttachedToWindow=false;
boolean mAutoMeasure=false;
private boolean mMeasurementCacheEnabled=true;
private boolean mItemPrefetchEnabled=true;
int mPrefetchMaxCountObserved;
boolean mPrefetchMaxObservedInInitialPrefetch;
private int mWidthMode, mHeightMode;
private int mWidth, mHeight;
public interface LayoutPrefetchRegistry {
void addPosition(int layoutPosition,int pixelDistance);
}
void setRecyclerView(RecyclerView recyclerView){
if (recyclerView == null) {
mRecyclerView=null;
mChildHelper=null;
mWidth=0;
mHeight=0;
}
 else {
mRecyclerView=recyclerView;
mChildHelper=recyclerView.mChildHelper;
mWidth=recyclerView.getWidth();
mHeight=recyclerView.getHeight();
}
mWidthMode=MeasureSpec.EXACTLY;
mHeightMode=MeasureSpec.EXACTLY;
}
void setMeasureSpecs(int wSpec,int hSpec){
mWidth=MeasureSpec.getSize(wSpec);
mWidthMode=MeasureSpec.getMode(wSpec);
if (mWidthMode == MeasureSpec.UNSPECIFIED && !ALLOW_SIZE_IN_UNSPECIFIED_SPEC) {
mWidth=0;
}
mHeight=MeasureSpec.getSize(hSpec);
mHeightMode=MeasureSpec.getMode(hSpec);
if (mHeightMode == MeasureSpec.UNSPECIFIED && !ALLOW_SIZE_IN_UNSPECIFIED_SPEC) {
mHeight=0;
}
}
void setMeasuredDimensionFromChildren(int widthSpec,int heightSpec){
final int count=getChildCount();
if (count == 0) {
mRecyclerView.defaultOnMeasure(widthSpec,heightSpec);
return;
}
int minX=Integer.MAX_VALUE;
int minY=Integer.MAX_VALUE;
int maxX=Integer.MIN_VALUE;
int maxY=Integer.MIN_VALUE;
for (int i=0; i < count; i++) {
View child=getChildAt(i);
final Rect bounds=mRecyclerView.mTempRect;
getDecoratedBoundsWithMargins(child,bounds);
if (bounds.left < minX) {
minX=bounds.left;
}
if (bounds.right > maxX) {
maxX=bounds.right;
}
if (bounds.top < minY) {
minY=bounds.top;
}
if (bounds.bottom > maxY) {
maxY=bounds.bottom;
}
}
mRecyclerView.mTempRect.set(minX,minY,maxX,maxY);
setMeasuredDimension(mRecyclerView.mTempRect,widthSpec,heightSpec);
}
public void setMeasuredDimension(Rect childrenBounds,int wSpec,int hSpec){
int usedWidth=childrenBounds.width() + getPaddingLeft() + getPaddingRight();
int usedHeight=childrenBounds.height() + getPaddingTop() + getPaddingBottom();
int width=chooseSize(wSpec,usedWidth,getMinimumWidth());
int height=chooseSize(hSpec,usedHeight,getMinimumHeight());
setMeasuredDimension(width,height);
}
public void requestLayout(){
if (mRecyclerView != null) {
mRecyclerView.requestLayout();
}
}
public void assertInLayoutOrScroll(String message){
if (mRecyclerView != null) {
mRecyclerView.assertInLayoutOrScroll(message);
}
}
public static int chooseSize(int spec,int desired,int min){
final int mode=View.MeasureSpec.getMode(spec);
final int size=View.MeasureSpec.getSize(spec);
switch (mode) {
case View.MeasureSpec.EXACTLY:
return size;
case View.MeasureSpec.AT_MOST:
return Math.min(size,Math.max(desired,min));
case View.MeasureSpec.UNSPECIFIED:
default :
return Math.max(desired,min);
}
}
public void assertNotInLayoutOrScroll(String message){
if (mRecyclerView != null) {
mRecyclerView.assertNotInLayoutOrScroll(message);
}
}
public void setAutoMeasureEnabled(boolean enabled){
mAutoMeasure=enabled;
}
public boolean isAutoMeasureEnabled(){
return mAutoMeasure;
}
public boolean supportsPredictiveItemAnimations(){
return false;
}
public final void setItemPrefetchEnabled(boolean enabled){
if (enabled != mItemPrefetchEnabled) {
mItemPrefetchEnabled=enabled;
mPrefetchMaxCountObserved=0;
if (mRecyclerView != null) {
mRecyclerView.mRecycler.updateViewCacheSize();
}
}
}
public final boolean isItemPrefetchEnabled(){
return mItemPrefetchEnabled;
}
public void collectAdjacentPrefetchPositions(int dx,int dy,State state,LayoutPrefetchRegistry layoutPrefetchRegistry){
}
public void collectInitialPrefetchPositions(int adapterItemCount,LayoutPrefetchRegistry layoutPrefetchRegistry){
}
void dispatchAttachedToWindow(RecyclerView view){
mIsAttachedToWindow=true;
onAttachedToWindow(view);
}
void dispatchDetachedFromWindow(RecyclerView view,Recycler recycler){
mIsAttachedToWindow=false;
onDetachedFromWindow(view,recycler);
}
public boolean isAttachedToWindow(){
return mIsAttachedToWindow;
}
public void postOnAnimation(Runnable action){
if (mRecyclerView != null) {
//ViewCompat.postOnAnimation(mRecyclerView,action);
}
}
public boolean removeCallbacks(Runnable action){
if (mRecyclerView != null) {
//return mRecyclerView.removeCallbacks(action);
}
return false;
}
public void onAttachedToWindow(RecyclerView view){
}
public void onDetachedFromWindow(RecyclerView view){
}
public void onDetachedFromWindow(RecyclerView view,Recycler recycler){
onDetachedFromWindow(view);
}
public boolean getClipToPadding(){
return mRecyclerView != null && mRecyclerView.mClipToPadding;
}
public void onLayoutChildren(Recycler recycler,State state){
Log.e(TAG,"You must override onLayoutChildren(Recycler recycler, State state) ");
}
public void onLayoutCompleted(State state){
}
public abstract LayoutParams generateDefaultLayoutParams();
public boolean checkLayoutParams(LayoutParams lp){
return lp != null;
}
public int scrollHorizontallyBy(int dx,Recycler recycler,State state){
return 0;
}
public int scrollVerticallyBy(int dy,Recycler recycler,State state){
return 0;
}
public boolean canScrollHorizontally(){
return false;
}
public boolean canScrollVertically(){
return false;
}
public void scrollToPosition(int position){
if (DEBUG) {
Log.e(TAG,"You MUST implement scrollToPosition. It will soon become abstract");
}
}
public void smoothScrollToPosition(RecyclerView recyclerView,State state,int position){
Log.e(TAG,"You must override smoothScrollToPosition to support smooth scrolling");
}
public int getLayoutDirection(){
return ViewCompat.getLayoutDirection(mRecyclerView);
}
public void endAnimation(View view){
if (mRecyclerView.mItemAnimator != null) {
mRecyclerView.mItemAnimator.endAnimation(getChildViewHolderInt(view));
}
}
public void addDisappearingView(View child){
addDisappearingView(child,-1);
}
public void addDisappearingView(View child,int index){
addViewInt(child,index,true);
}
public void addView(View child){
addView(child,-1);
}
public void addView(View child,int index){
addViewInt(child,index,false);
}
private void addViewInt(View child,int index,boolean disappearing){
final ViewHolder holder=getChildViewHolderInt(child);
if (disappearing || holder.isRemoved()) {
mRecyclerView.mViewInfoStore.addToDisappearedInLayout(holder);
}
 else {
mRecyclerView.mViewInfoStore.removeFromDisappearedInLayout(holder);
}
final LayoutParams lp=(LayoutParams)child.getLayoutParams();
if (holder.wasReturnedFromScrap() || holder.isScrap()) {
if (holder.isScrap()) {
holder.unScrap();
}
 else {
holder.clearReturnedFromScrapFlag();
}
mChildHelper.attachViewToParent(child,index,child.getLayoutParams(),false);
if (DISPATCH_TEMP_DETACH) {
ViewCompat.dispatchFinishTemporaryDetach(child);
}
}
 else if (child.getParent() == mRecyclerView) {
int currentIndex=mChildHelper.indexOfChild(child);
if (index == -1) {
index=mChildHelper.getChildCount();
}
if (currentIndex == -1) {
throw new IllegalStateException("Added View has RecyclerView as parent but" + " view is not a real child. Unfiltered index:" + mRecyclerView.indexOfChild(child) + mRecyclerView.exceptionLabel());
}
if (currentIndex != index) {
mRecyclerView.mLayout.moveView(currentIndex,index);
}
}
 else {
mChildHelper.addView(child,index,false);
lp.mInsetsDirty=true;
if (false) {
//mSmoothScroller.onChildAttachedToWindow(child);
}
}
if (lp.mPendingInvalidate) {
if (DEBUG) {
Log.d(TAG,"consuming pending invalidate on child " + lp.mViewHolder);
}
holder.itemView.invalidate();
lp.mPendingInvalidate=false;
}
}
public void removeView(View child){
mChildHelper.removeView(child);
}
public void removeViewAt(int index){
final View child=getChildAt(index);
if (child != null) {
mChildHelper.removeViewAt(index);
}
}
public void removeAllViews(){
final int childCount=getChildCount();
for (int i=childCount - 1; i >= 0; i--) {
mChildHelper.removeViewAt(i);
}
}
public int getBaseline(){
return -1;
}
public int getPosition(View view){
return ((RecyclerView.LayoutParams)view.getLayoutParams()).getViewLayoutPosition();
}
public int getItemViewType(View view){
return getChildViewHolderInt(view).getItemViewType();
}
public View findContainingItemView(View view){
if (mRecyclerView == null) {
return null;
}
View found=mRecyclerView.findContainingItemView(view);
if (found == null) {
return null;
}
if (mChildHelper.isHidden(found)) {
return null;
}
return found;
}
public View findViewByPosition(int position){
final int childCount=getChildCount();
for (int i=0; i < childCount; i++) {
View child=getChildAt(i);
ViewHolder vh=getChildViewHolderInt(child);
if (vh == null) {
continue;
}
if (vh.getLayoutPosition() == position && !vh.shouldIgnore() && (mRecyclerView.mState.isPreLayout() || !vh.isRemoved())) {
return child;
}
}
return null;
}
public void detachView(View child){
final int ind=mChildHelper.indexOfChild(child);
if (ind >= 0) {
detachViewInternal(ind,child);
}
}
public void detachViewAt(int index){
detachViewInternal(index,getChildAt(index));
}
private void detachViewInternal(int index,View view){
if (DISPATCH_TEMP_DETACH) {
ViewCompat.dispatchStartTemporaryDetach(view);
}
mChildHelper.detachViewFromParent(index);
}
public void attachView(View child,int index,LayoutParams lp){
ViewHolder vh=getChildViewHolderInt(child);
if (vh.isRemoved()) {
mRecyclerView.mViewInfoStore.addToDisappearedInLayout(vh);
}
 else {
mRecyclerView.mViewInfoStore.removeFromDisappearedInLayout(vh);
}
mChildHelper.attachViewToParent(child,index,lp,vh.isRemoved());
if (DISPATCH_TEMP_DETACH) {
ViewCompat.dispatchFinishTemporaryDetach(child);
}
}
public void attachView(View child,int index){
attachView(child,index,(LayoutParams)child.getLayoutParams());
}
public void attachView(View child){
attachView(child,-1);
}
public void removeDetachedView(View child){
mRecyclerView.removeDetachedView(child,false);
}
public void moveView(int fromIndex,int toIndex){
View view=getChildAt(fromIndex);
if (view == null) {
throw new IllegalArgumentException("Cannot move a child from non-existing index:" + fromIndex + mRecyclerView.toString());
}
detachViewAt(fromIndex);
attachView(view,toIndex);
}
public void detachAndScrapView(View child,Recycler recycler){
int index=mChildHelper.indexOfChild(child);
scrapOrRecycleView(recycler,index,child);
}
public void detachAndScrapViewAt(int index,Recycler recycler){
final View child=getChildAt(index);
scrapOrRecycleView(recycler,index,child);
}
public void removeAndRecycleView(View child,Recycler recycler){
removeView(child);
recycler.recycleView(child);
}
public void removeAndRecycleViewAt(int index,Recycler recycler){
final View view=getChildAt(index);
removeViewAt(index);
recycler.recycleView(view);
}
public int getChildCount(){
return mChildHelper != null ? mChildHelper.getChildCount() : 0;
}
public View getChildAt(int index){
return mChildHelper != null ? mChildHelper.getChildAt(index) : null;
}
public int getWidthMode(){
return mWidthMode;
}
public int getHeightMode(){
return mHeightMode;
}
public int getWidth(){
return mWidth;
}
public int getHeight(){
return mHeight;
}
public int getPaddingLeft(){
return mRecyclerView != null ? mRecyclerView.getPaddingLeft() : 0;
}
public int getPaddingTop(){
return mRecyclerView != null ? mRecyclerView.getPaddingTop() : 0;
}
public int getPaddingRight(){
return mRecyclerView != null ? mRecyclerView.getPaddingRight() : 0;
}
public int getPaddingBottom(){
return mRecyclerView != null ? mRecyclerView.getPaddingBottom() : 0;
}
public int getPaddingStart(){
return mRecyclerView != null ? ViewCompat.getPaddingStart(mRecyclerView) : 0;
}
public int getPaddingEnd(){
return mRecyclerView != null ? ViewCompat.getPaddingEnd(mRecyclerView) : 0;
}
public boolean isFocused(){
return mRecyclerView != null && mRecyclerView.isFocused();
}
public boolean hasFocus(){
return mRecyclerView != null && mRecyclerView.hasFocus();
}
public int getItemCount(){
final Adapter a=mRecyclerView != null ? mRecyclerView.getAdapter() : null;
return a != null ? a.getItemCount() : 0;
}
public void offsetChildrenHorizontal(int dx){
if (mRecyclerView != null) {
mRecyclerView.offsetChildrenHorizontal(dx);
}
}
public void offsetChildrenVertical(int dy){
if (mRecyclerView != null) {
mRecyclerView.offsetChildrenVertical(dy);
}
}
public void ignoreView(View view){
if (view.getParent() != mRecyclerView || mRecyclerView.indexOfChild(view) == -1) {
throw new IllegalArgumentException("View should be fully attached to be ignored" + mRecyclerView.exceptionLabel());
}
final ViewHolder vh=getChildViewHolderInt(view);
vh.addFlags(ViewHolder.FLAG_IGNORE);
mRecyclerView.mViewInfoStore.removeViewHolder(vh);
}
public void stopIgnoringView(View view){
final ViewHolder vh=getChildViewHolderInt(view);
vh.stopIgnoring();
vh.resetInternal();
vh.addFlags(ViewHolder.FLAG_INVALID);
}
public void detachAndScrapAttachedViews(Recycler recycler){
final int childCount=getChildCount();
for (int i=childCount - 1; i >= 0; i--) {
final View v=getChildAt(i);
scrapOrRecycleView(recycler,i,v);
}
}
private void scrapOrRecycleView(Recycler recycler,int index,View view){
final ViewHolder viewHolder=getChildViewHolderInt(view);
if (viewHolder.shouldIgnore()) {
if (DEBUG) {
Log.d(TAG,"ignoring view " + viewHolder);
}
return;
}
if (viewHolder.isInvalid() && !viewHolder.isRemoved() && !mRecyclerView.mAdapter.hasStableIds()) {
removeViewAt(index);
recycler.recycleViewHolderInternal(viewHolder);
}
 else {
detachViewAt(index);
recycler.scrapView(view);
mRecyclerView.mViewInfoStore.onViewDetached(viewHolder);
}
}
void removeAndRecycleScrapInt(Recycler recycler){
final int scrapCount=recycler.getScrapCount();
for (int i=scrapCount - 1; i >= 0; i--) {
final View scrap=recycler.getScrapViewAt(i);
final ViewHolder vh=getChildViewHolderInt(scrap);
if (vh.shouldIgnore()) {
continue;
}
vh.setIsRecyclable(false);
if (vh.isTmpDetached()) {
mRecyclerView.removeDetachedView(scrap,false);
}
if (mRecyclerView.mItemAnimator != null) {
mRecyclerView.mItemAnimator.endAnimation(vh);
}
vh.setIsRecyclable(true);
recycler.quickRecycleScrapView(scrap);
}
recycler.clearScrap();
if (scrapCount > 0) {
mRecyclerView.invalidate();
}
}
public void measureChild(View child,int widthUsed,int heightUsed){
final LayoutParams lp=(LayoutParams)child.getLayoutParams();
final Rect insets=mRecyclerView.getItemDecorInsetsForChild(child);
widthUsed+=insets.left + insets.right;
heightUsed+=insets.top + insets.bottom;
final int widthSpec=getChildMeasureSpec(getWidth(),getWidthMode(),getPaddingLeft() + getPaddingRight() + widthUsed,lp.width,canScrollHorizontally());
final int heightSpec=getChildMeasureSpec(getHeight(),getHeightMode(),getPaddingTop() + getPaddingBottom() + heightUsed,lp.height,canScrollVertically());
if (shouldMeasureChild(child,widthSpec,heightSpec,lp)) {
child.measure(widthSpec,heightSpec);
}
}
boolean shouldReMeasureChild(View child,int widthSpec,int heightSpec,LayoutParams lp){
return !mMeasurementCacheEnabled || !isMeasurementUpToDate(child.getMeasuredWidth(),widthSpec,lp.width) || !isMeasurementUpToDate(child.getMeasuredHeight(),heightSpec,lp.height);
}
boolean shouldMeasureChild(View child,int widthSpec,int heightSpec,LayoutParams lp){
return child.isLayoutRequested() || !mMeasurementCacheEnabled || !isMeasurementUpToDate(child.getWidth(),widthSpec,lp.width)|| !isMeasurementUpToDate(child.getHeight(),heightSpec,lp.height);
}
public boolean isMeasurementCacheEnabled(){
return mMeasurementCacheEnabled;
}
public void setMeasurementCacheEnabled(boolean measurementCacheEnabled){
mMeasurementCacheEnabled=measurementCacheEnabled;
}
private static boolean isMeasurementUpToDate(int childSize,int spec,int dimension){
final int specMode=MeasureSpec.getMode(spec);
final int specSize=MeasureSpec.getSize(spec);
if (dimension > 0 && childSize != dimension) {
return false;
}
switch (specMode) {
case MeasureSpec.UNSPECIFIED:
return true;
case MeasureSpec.AT_MOST:
return specSize >= childSize;
case MeasureSpec.EXACTLY:
return specSize == childSize;
}
return false;
}
public void measureChildWithMargins(View child,int widthUsed,int heightUsed){
final LayoutParams lp=(LayoutParams)child.getLayoutParams();
final Rect insets=mRecyclerView.getItemDecorInsetsForChild(child);
widthUsed+=insets.left + insets.right;
heightUsed+=insets.top + insets.bottom;
final int widthSpec=getChildMeasureSpec(getWidth(),getWidthMode(),getPaddingLeft() + getPaddingRight() + lp.leftMargin+ lp.rightMargin+ widthUsed,lp.width,canScrollHorizontally());
final int heightSpec=getChildMeasureSpec(getHeight(),getHeightMode(),getPaddingTop() + getPaddingBottom() + lp.topMargin+ lp.bottomMargin+ heightUsed,lp.height,canScrollVertically());
if (shouldMeasureChild(child,widthSpec,heightSpec,lp)) {
child.measure(widthSpec,heightSpec);
}
}
public static int getChildMeasureSpec(int parentSize,int padding,int childDimension,boolean canScroll){
int size=Math.max(0,parentSize - padding);
int resultSize=0;
int resultMode=0;
if (canScroll) {
if (childDimension >= 0) {
resultSize=childDimension;
resultMode=MeasureSpec.EXACTLY;
}
 else {
resultSize=0;
resultMode=MeasureSpec.UNSPECIFIED;
}
}
 else {
if (childDimension >= 0) {
resultSize=childDimension;
resultMode=MeasureSpec.EXACTLY;
}
 else if (childDimension == LayoutParams.MATCH_PARENT) {
resultSize=size;
resultMode=MeasureSpec.EXACTLY;
}
 else if (childDimension == LayoutParams.WRAP_CONTENT) {
resultSize=size;
resultMode=MeasureSpec.AT_MOST;
}
}
return MeasureSpec.makeMeasureSpec(resultSize,resultMode);
}
public static int getChildMeasureSpec(int parentSize,int parentMode,int padding,int childDimension,boolean canScroll){
int size=Math.max(0,parentSize - padding);
int resultSize=0;
int resultMode=0;
if (canScroll) {
if (childDimension >= 0) {
resultSize=childDimension;
resultMode=MeasureSpec.EXACTLY;
}
 else if (childDimension == LayoutParams.MATCH_PARENT) {
switch (parentMode) {
case MeasureSpec.AT_MOST:
case MeasureSpec.EXACTLY:
resultSize=size;
resultMode=parentMode;
break;
case MeasureSpec.UNSPECIFIED:
resultSize=0;
resultMode=MeasureSpec.UNSPECIFIED;
break;
}
}
 else if (childDimension == LayoutParams.WRAP_CONTENT) {
resultSize=0;
resultMode=MeasureSpec.UNSPECIFIED;
}
}
 else {
if (childDimension >= 0) {
resultSize=childDimension;
resultMode=MeasureSpec.EXACTLY;
}
 else if (childDimension == LayoutParams.MATCH_PARENT) {
resultSize=size;
resultMode=parentMode;
}
 else if (childDimension == LayoutParams.WRAP_CONTENT) {
resultSize=size;
if (parentMode == MeasureSpec.AT_MOST || parentMode == MeasureSpec.EXACTLY) {
resultMode=MeasureSpec.AT_MOST;
}
 else {
resultMode=MeasureSpec.UNSPECIFIED;
}
}
}
return MeasureSpec.makeMeasureSpec(resultSize,resultMode);
}
public int getDecoratedMeasuredWidth(View child){
final Rect insets=((LayoutParams)child.getLayoutParams()).mDecorInsets;
return child.getMeasuredWidth() + insets.left + insets.right;
}
public int getDecoratedMeasuredHeight(View child){
final Rect insets=((LayoutParams)child.getLayoutParams()).mDecorInsets;
return child.getMeasuredHeight() + insets.top + insets.bottom;
}
public void layoutDecorated(View child,int left,int top,int right,int bottom){
final Rect insets=((LayoutParams)child.getLayoutParams()).mDecorInsets;
child.layout(left + insets.left,top + insets.top,right - insets.right,bottom - insets.bottom);
}
public void layoutDecoratedWithMargins(View child,int left,int top,int right,int bottom){
final LayoutParams lp=(LayoutParams)child.getLayoutParams();
final Rect insets=lp.mDecorInsets;
child.layout(left + insets.left + lp.leftMargin,top + insets.top + lp.topMargin,right - insets.right - lp.rightMargin,bottom - insets.bottom - lp.bottomMargin);
}
public void getTransformedBoundingBox(View child,boolean includeDecorInsets,Rect out){
if (includeDecorInsets) {
Rect insets=((LayoutParams)child.getLayoutParams()).mDecorInsets;
out.set(-insets.left,-insets.top,child.getWidth() + insets.right,child.getHeight() + insets.bottom);
}
 else {
out.set(0,0,child.getWidth(),child.getHeight());
}
if (mRecyclerView != null) {
//final Matrix childMatrix=child.getMatrix();
if (false) {
final RectF tempRectF=mRecyclerView.mTempRectF;
tempRectF.set(out);
//childMatrix.mapRect(tempRectF);
out.set((int)Math.floor(tempRectF.left),(int)Math.floor(tempRectF.top),(int)Math.ceil(tempRectF.right),(int)Math.ceil(tempRectF.bottom));
}
}
out.offset(child.getLeft(),child.getTop());
}
public void getDecoratedBoundsWithMargins(View view,Rect outBounds){
RecyclerView.getDecoratedBoundsWithMarginsInt(view,outBounds);
}
public int getDecoratedLeft(View child){
return child.getLeft() - getLeftDecorationWidth(child);
}
public int getDecoratedTop(View child){
return child.getTop() - getTopDecorationHeight(child);
}
public int getDecoratedRight(View child){
return child.getRight() + getRightDecorationWidth(child);
}
public int getDecoratedBottom(View child){
return child.getBottom() + getBottomDecorationHeight(child);
}
public void calculateItemDecorationsForChild(View child,Rect outRect){
if (mRecyclerView == null) {
outRect.set(0,0,0,0);
return;
}
Rect insets=mRecyclerView.getItemDecorInsetsForChild(child);
outRect.set(insets);
}
public int getTopDecorationHeight(View child){
return ((LayoutParams)child.getLayoutParams()).mDecorInsets.top;
}
public int getBottomDecorationHeight(View child){
return ((LayoutParams)child.getLayoutParams()).mDecorInsets.bottom;
}
public int getLeftDecorationWidth(View child){
return ((LayoutParams)child.getLayoutParams()).mDecorInsets.left;
}
public int getRightDecorationWidth(View child){
return ((LayoutParams)child.getLayoutParams()).mDecorInsets.right;
}
public View onFocusSearchFailed(View focused,int direction,Recycler recycler,State state){
return null;
}
public View onInterceptFocusSearch(View focused,int direction){
return null;
}
public void onAdapterChanged(Adapter oldAdapter,Adapter newAdapter){
}
public boolean onAddFocusables(RecyclerView recyclerView,ArrayList<View> views,int direction,int focusableMode){
return false;
}
public void onItemsChanged(RecyclerView recyclerView){
}
public void onItemsAdded(RecyclerView recyclerView,int positionStart,int itemCount){
}
public void onItemsRemoved(RecyclerView recyclerView,int positionStart,int itemCount){
}
public void onItemsUpdated(RecyclerView recyclerView,int positionStart,int itemCount){
}
public void onItemsUpdated(RecyclerView recyclerView,int positionStart,int itemCount,Object payload){
onItemsUpdated(recyclerView,positionStart,itemCount);
}
public void onItemsMoved(RecyclerView recyclerView,int from,int to,int itemCount){
}
public int computeHorizontalScrollExtent(State state){
return 0;
}
public int computeHorizontalScrollOffset(State state){
return 0;
}
public int computeHorizontalScrollRange(State state){
return 0;
}
public int computeVerticalScrollExtent(State state){
return 0;
}
public int computeVerticalScrollOffset(State state){
return 0;
}
public int computeVerticalScrollRange(State state){
return 0;
}
public void onMeasure(Recycler recycler,State state,int widthSpec,int heightSpec){
mRecyclerView.defaultOnMeasure(widthSpec,heightSpec);
}
public void setMeasuredDimension(int widthSize,int heightSize){
mRecyclerView.setMeasuredDimension(widthSize,heightSize);
}
public int getMinimumWidth(){
return ViewCompat.getMinimumWidth(mRecyclerView);
}
public int getMinimumHeight(){
return ViewCompat.getMinimumHeight(mRecyclerView);
}
public Parcelable onSaveInstanceState(){
return null;
}
public void onRestoreInstanceState(Parcelable state){
}
public void onScrollStateChanged(int state){
}
public void removeAndRecycleAllViews(Recycler recycler){
for (int i=getChildCount() - 1; i >= 0; i--) {
final View view=getChildAt(i);
if (!getChildViewHolderInt(view).shouldIgnore()) {
removeAndRecycleViewAt(i,recycler);
}
}
}
public void requestSimpleAnimationsInNextLayout(){
mRequestedSimpleAnimations=true;
}
public int getRowCountForAccessibility(Recycler recycler,State state){
return -1;
}
public int getColumnCountForAccessibility(Recycler recycler,State state){
return -1;
}
public boolean isLayoutHierarchical(Recycler recycler,State state){
return false;
}
void setExactMeasureSpecsFrom(RecyclerView recyclerView){
setMeasureSpecs(MeasureSpec.makeMeasureSpec(recyclerView.getWidth(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(recyclerView.getHeight(),MeasureSpec.EXACTLY));
}
boolean shouldMeasureTwice(){
return false;
}
boolean hasFlexibleChildInBothOrientations(){
final int childCount=getChildCount();
for (int i=0; i < childCount; i++) {
final View child=getChildAt(i);
final ViewGroup.LayoutParams lp=child.getLayoutParams();
if (lp.width < 0 && lp.height < 0) {
return true;
}
}
return false;
}
}
public abstract static class ItemDecoration {
public void getItemOffsets(Rect outRect,int itemPosition,RecyclerView parent){
outRect.set(0,0,0,0);
}
public void getItemOffsets(Rect outRect,View view,RecyclerView parent,State state){
getItemOffsets(outRect,((LayoutParams)view.getLayoutParams()).getViewLayoutPosition(),parent);
}
}
public abstract static class OnScrollListener {
public void onScrollStateChanged(RecyclerView recyclerView,int newState){
}
public void onScrolled(RecyclerView recyclerView,int dx,int dy){
}
}
public interface RecyclerListener {
void onViewRecycled(ViewHolder holder);
}
public interface OnChildAttachStateChangeListener {
void onChildViewAttachedToWindow(View view);
void onChildViewDetachedFromWindow(View view);
}
public abstract static class ViewHolder {
public final View itemView;
WeakReference<RecyclerView> mNestedRecyclerView;
int mPosition=NO_POSITION;
int mOldPosition=NO_POSITION;
long mItemId=NO_ID;
int mItemViewType=INVALID_TYPE;
int mPreLayoutPosition=NO_POSITION;
ViewHolder mShadowedHolder=null;
ViewHolder mShadowingHolder=null;
static final int FLAG_BOUND=1 << 0;
static final int FLAG_UPDATE=1 << 1;
static final int FLAG_INVALID=1 << 2;
static final int FLAG_REMOVED=1 << 3;
static final int FLAG_NOT_RECYCLABLE=1 << 4;
static final int FLAG_RETURNED_FROM_SCRAP=1 << 5;
static final int FLAG_IGNORE=1 << 7;
static final int FLAG_TMP_DETACHED=1 << 8;
static final int FLAG_ADAPTER_POSITION_UNKNOWN=1 << 9;
static final int FLAG_ADAPTER_FULLUPDATE=1 << 10;
static final int FLAG_MOVED=1 << 11;
static final int FLAG_APPEARED_IN_PRE_LAYOUT=1 << 12;
static final int PENDING_ACCESSIBILITY_STATE_NOT_SET=-1;
static final int FLAG_BOUNCED_FROM_HIDDEN_LIST=1 << 13;
int mFlags;
private static final List<Object> FULLUPDATE_PAYLOADS=Collections.emptyList();
List<Object> mPayloads=null;
List<Object> mUnmodifiedPayloads=null;
private int mIsRecyclableCount=0;
Recycler mScrapContainer=null;
boolean mInChangeScrap=false;
private int mWasImportantForAccessibilityBeforeHidden=ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
int mPendingAccessibilityState=PENDING_ACCESSIBILITY_STATE_NOT_SET;
RecyclerView mOwnerRecyclerView;
Adapter<? extends ViewHolder> mBindingAdapter;
public ViewHolder(View itemView){
if (itemView == null) {
throw new IllegalArgumentException("itemView may not be null");
}
this.itemView=itemView;
}
void flagRemovedAndOffsetPosition(int mNewPosition,int offset,boolean applyToPreLayout){
addFlags(ViewHolder.FLAG_REMOVED);
offsetPosition(offset,applyToPreLayout);
mPosition=mNewPosition;
}
void offsetPosition(int offset,boolean applyToPreLayout){
if (mOldPosition == NO_POSITION) {
mOldPosition=mPosition;
}
if (mPreLayoutPosition == NO_POSITION) {
mPreLayoutPosition=mPosition;
}
if (applyToPreLayout) {
mPreLayoutPosition+=offset;
}
mPosition+=offset;
if (itemView.getLayoutParams() != null) {
((LayoutParams)itemView.getLayoutParams()).mInsetsDirty=true;
}
}
void clearOldPosition(){
mOldPosition=NO_POSITION;
mPreLayoutPosition=NO_POSITION;
}
void saveOldPosition(){
if (mOldPosition == NO_POSITION) {
mOldPosition=mPosition;
}
}
boolean shouldIgnore(){
return (mFlags & FLAG_IGNORE) != 0;
}
public final int getPosition(){
return mPreLayoutPosition == NO_POSITION ? mPosition : mPreLayoutPosition;
}
public final int getLayoutPosition(){
return mPreLayoutPosition == NO_POSITION ? mPosition : mPreLayoutPosition;
}
public final int getAdapterPosition(){
return getBindingAdapterPosition();
}
public final int getBindingAdapterPosition(){
if (mBindingAdapter == null) {
return NO_POSITION;
}
if (mOwnerRecyclerView == null) {
return NO_POSITION;
}
Adapter<? extends ViewHolder> rvAdapter=mOwnerRecyclerView.getAdapter();
if (rvAdapter == null) {
return NO_POSITION;
}
int globalPosition=mOwnerRecyclerView.getAdapterPositionInRecyclerView(this);
if (globalPosition == NO_POSITION) {
return NO_POSITION;
}
return rvAdapter.findRelativeAdapterPositionIn(mBindingAdapter,this,globalPosition);
}
public final int getAbsoluteAdapterPosition(){
if (mOwnerRecyclerView == null) {
return NO_POSITION;
}
return mOwnerRecyclerView.getAdapterPositionInRecyclerView(this);
}
public final Adapter<? extends ViewHolder> getBindingAdapter(){
return mBindingAdapter;
}
public final int getOldPosition(){
return mOldPosition;
}
public final long getItemId(){
return mItemId;
}
public final int getItemViewType(){
return mItemViewType;
}
boolean isScrap(){
return mScrapContainer != null;
}
void unScrap(){
mScrapContainer.unscrapView(this);
}
boolean wasReturnedFromScrap(){
return (mFlags & FLAG_RETURNED_FROM_SCRAP) != 0;
}
void clearReturnedFromScrapFlag(){
mFlags=mFlags & ~FLAG_RETURNED_FROM_SCRAP;
}
void clearTmpDetachFlag(){
mFlags=mFlags & ~FLAG_TMP_DETACHED;
}
void stopIgnoring(){
mFlags=mFlags & ~FLAG_IGNORE;
}
void setScrapContainer(Recycler recycler,boolean isChangeScrap){
mScrapContainer=recycler;
mInChangeScrap=isChangeScrap;
}
boolean isInvalid(){
return (mFlags & FLAG_INVALID) != 0;
}
boolean needsUpdate(){
return (mFlags & FLAG_UPDATE) != 0;
}
boolean isBound(){
return (mFlags & FLAG_BOUND) != 0;
}
boolean isRemoved(){
return (mFlags & FLAG_REMOVED) != 0;
}
boolean hasAnyOfTheFlags(int flags){
return (mFlags & flags) != 0;
}
boolean isTmpDetached(){
return (mFlags & FLAG_TMP_DETACHED) != 0;
}
boolean isAttachedToTransitionOverlay(){
return itemView.getParent() != null && itemView.getParent() != mOwnerRecyclerView;
}
boolean isAdapterPositionUnknown(){
return (mFlags & FLAG_ADAPTER_POSITION_UNKNOWN) != 0 || isInvalid();
}
void setFlags(int flags,int mask){
mFlags=(mFlags & ~mask) | (flags & mask);
}
void addFlags(int flags){
mFlags|=flags;
}
void addChangePayload(Object payload){
if (payload == null) {
addFlags(FLAG_ADAPTER_FULLUPDATE);
}
 else if ((mFlags & FLAG_ADAPTER_FULLUPDATE) == 0) {
createPayloadsIfNeeded();
mPayloads.add(payload);
}
}
private void createPayloadsIfNeeded(){
if (mPayloads == null) {
mPayloads=new ArrayList<Object>();
mUnmodifiedPayloads=Collections.unmodifiableList(mPayloads);
}
}
void clearPayload(){
if (mPayloads != null) {
mPayloads.clear();
}
mFlags=mFlags & ~FLAG_ADAPTER_FULLUPDATE;
}
List<Object> getUnmodifiedPayloads(){
if ((mFlags & FLAG_ADAPTER_FULLUPDATE) == 0) {
if (mPayloads == null || mPayloads.size() == 0) {
return FULLUPDATE_PAYLOADS;
}
return mUnmodifiedPayloads;
}
 else {
return FULLUPDATE_PAYLOADS;
}
}
void resetInternal(){
mFlags=0;
mPosition=NO_POSITION;
mOldPosition=NO_POSITION;
mItemId=NO_ID;
mPreLayoutPosition=NO_POSITION;
mIsRecyclableCount=0;
mShadowedHolder=null;
mShadowingHolder=null;
clearPayload();
mWasImportantForAccessibilityBeforeHidden=ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
mPendingAccessibilityState=PENDING_ACCESSIBILITY_STATE_NOT_SET;
clearNestedRecyclerViewIfNotNested(this);
}
void onEnteredHiddenState(RecyclerView parent){
if (mPendingAccessibilityState != PENDING_ACCESSIBILITY_STATE_NOT_SET) {
mWasImportantForAccessibilityBeforeHidden=mPendingAccessibilityState;
}
 else {
mWasImportantForAccessibilityBeforeHidden=ViewCompat.getImportantForAccessibility(itemView);
}
//parent.setChildImportantForAccessibilityInternal(this,ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
}
void onLeftHiddenState(RecyclerView parent){
//parent.setChildImportantForAccessibilityInternal(this,mWasImportantForAccessibilityBeforeHidden);
mWasImportantForAccessibilityBeforeHidden=ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
}
public String toString(){
String className=false ? "ViewHolder" : getClass().getSimpleName();
final StringBuilder sb=new StringBuilder(className + "{" + Integer.toHexString(hashCode())+ " position="+ mPosition+ " id="+ mItemId+ ", oldPos="+ mOldPosition+ ", pLpos:"+ mPreLayoutPosition);
if (isScrap()) {
sb.append(" scrap ").append(mInChangeScrap ? "[changeScrap]" : "[attachedScrap]");
}
if (isInvalid()) sb.append(" invalid");
if (!isBound()) sb.append(" unbound");
if (needsUpdate()) sb.append(" update");
if (isRemoved()) sb.append(" removed");
if (shouldIgnore()) sb.append(" ignored");
if (isTmpDetached()) sb.append(" tmpDetached");
if (!isRecyclable()) sb.append(" not recyclable(" + mIsRecyclableCount + ")");
if (isAdapterPositionUnknown()) sb.append(" undefined adapter position");
if (itemView.getParent() == null) sb.append(" no parent");
sb.append("}");
return sb.toString();
}
public final void setIsRecyclable(boolean recyclable){
mIsRecyclableCount=recyclable ? mIsRecyclableCount - 1 : mIsRecyclableCount + 1;
if (mIsRecyclableCount < 0) {
mIsRecyclableCount=0;
if (DEBUG) {
throw new RuntimeException("isRecyclable decremented below 0: " + "unmatched pair of setIsRecyable() calls for " + this);
}
Log.e(VIEW_LOG_TAG,"isRecyclable decremented below 0: " + "unmatched pair of setIsRecyable() calls for " + this);
}
 else if (!recyclable && mIsRecyclableCount == 1) {
mFlags|=FLAG_NOT_RECYCLABLE;
}
 else if (recyclable && mIsRecyclableCount == 0) {
mFlags&=~FLAG_NOT_RECYCLABLE;
}
if (DEBUG) {
Log.d(TAG,"setIsRecyclable val:" + recyclable + ":"+ this);
}
}
public final boolean isRecyclable(){
return (mFlags & FLAG_NOT_RECYCLABLE) == 0 && !ViewCompat.hasTransientState(itemView);
}
boolean shouldBeKeptAsChild(){
return (mFlags & FLAG_NOT_RECYCLABLE) != 0;
}
boolean doesTransientStatePreventRecycling(){
return (mFlags & FLAG_NOT_RECYCLABLE) == 0 && ViewCompat.hasTransientState(itemView);
}
boolean isUpdated(){
return (mFlags & FLAG_UPDATE) != 0;
}
}
int getAdapterPositionInRecyclerView(ViewHolder viewHolder){
if (viewHolder.hasAnyOfTheFlags(ViewHolder.FLAG_INVALID | ViewHolder.FLAG_REMOVED | ViewHolder.FLAG_ADAPTER_POSITION_UNKNOWN) || !viewHolder.isBound()) {
return RecyclerView.NO_POSITION;
}
return mAdapterHelper.applyPendingUpdatesToPosition(viewHolder.mPosition);
}
public boolean dispatchNestedScroll(int dxConsumed,int dyConsumed,int dxUnconsumed,int dyUnconsumed,int[] offsetInWindow){
return getScrollingChildHelper().dispatchNestedScroll(dxConsumed,dyConsumed,dxUnconsumed,dyUnconsumed,offsetInWindow);
}
public boolean dispatchNestedScroll(int dxConsumed,int dyConsumed,int dxUnconsumed,int dyUnconsumed,int[] offsetInWindow,int type){
return getScrollingChildHelper().dispatchNestedScroll(dxConsumed,dyConsumed,dxUnconsumed,dyUnconsumed,offsetInWindow,type);
}
public final void dispatchNestedScroll(int dxConsumed,int dyConsumed,int dxUnconsumed,int dyUnconsumed,int[] offsetInWindow,int type,int[] consumed){
getScrollingChildHelper().dispatchNestedScroll(dxConsumed,dyConsumed,dxUnconsumed,dyUnconsumed,offsetInWindow,type,consumed);
}
public static class LayoutParams extends r.android.view.ViewGroup.MarginLayoutParams {
ViewHolder mViewHolder;
final Rect mDecorInsets=new Rect();
boolean mInsetsDirty=true;
boolean mPendingInvalidate=false;
public LayoutParams(int width,int height){
super(width,height);
}
public LayoutParams(ViewGroup.LayoutParams source){
super(source);
}
public LayoutParams(LayoutParams source){
super((ViewGroup.LayoutParams)source);
}
public boolean isViewInvalid(){
return mViewHolder.isInvalid();
}
public boolean isItemRemoved(){
return mViewHolder.isRemoved();
}
public boolean isItemChanged(){
return mViewHolder.isUpdated();
}
public int getViewLayoutPosition(){
return mViewHolder.getLayoutPosition();
}
}
public abstract static class AdapterDataObserver {
public void onChanged(){
}
public void onItemRangeChanged(int positionStart,int itemCount){
}
public void onItemRangeChanged(int positionStart,int itemCount,Object payload){
onItemRangeChanged(positionStart,itemCount);
}
public void onItemRangeInserted(int positionStart,int itemCount){
}
public void onItemRangeRemoved(int positionStart,int itemCount){
}
public void onItemRangeMoved(int fromPosition,int toPosition,int itemCount){
}
public void onStateRestorationPolicyChanged(){
}
}
static class AdapterDataObservable extends Observable<AdapterDataObserver> {
public boolean hasObservers(){
return !mObservers.isEmpty();
}
public void notifyChanged(){
for (int i=mObservers.size() - 1; i >= 0; i--) {
mObservers.get(i).onChanged();
}
}
public void notifyStateRestorationPolicyChanged(){
for (int i=mObservers.size() - 1; i >= 0; i--) {
mObservers.get(i).onStateRestorationPolicyChanged();
}
}
public void notifyItemRangeChanged(int positionStart,int itemCount){
notifyItemRangeChanged(positionStart,itemCount,null);
}
public void notifyItemRangeChanged(int positionStart,int itemCount,Object payload){
for (int i=mObservers.size() - 1; i >= 0; i--) {
mObservers.get(i).onItemRangeChanged(positionStart,itemCount,payload);
}
}
public void notifyItemRangeInserted(int positionStart,int itemCount){
for (int i=mObservers.size() - 1; i >= 0; i--) {
mObservers.get(i).onItemRangeInserted(positionStart,itemCount);
}
}
public void notifyItemRangeRemoved(int positionStart,int itemCount){
for (int i=mObservers.size() - 1; i >= 0; i--) {
mObservers.get(i).onItemRangeRemoved(positionStart,itemCount);
}
}
public void notifyItemMoved(int fromPosition,int toPosition){
for (int i=mObservers.size() - 1; i >= 0; i--) {
mObservers.get(i).onItemRangeMoved(fromPosition,toPosition,1);
}
}
}
public static class State {
static final int STEP_START=1;
static final int STEP_LAYOUT=1 << 1;
static final int STEP_ANIMATIONS=1 << 2;
void assertLayoutStep(int accepted){
if ((accepted & mLayoutStep) == 0) {
throw new IllegalStateException("Layout state should be one of " + Integer.toBinaryString(accepted) + " but it is "+ Integer.toBinaryString(mLayoutStep));
}
}
int mTargetPosition=RecyclerView.NO_POSITION;
private SparseArray<Object> mData;
int mPreviousLayoutItemCount=0;
int mDeletedInvisibleItemCountSincePreviousLayout=0;
int mLayoutStep=STEP_START;
int mItemCount=0;
boolean mStructureChanged=false;
boolean mInPreLayout=false;
boolean mTrackOldChangeHolders=false;
boolean mIsMeasuring=false;
boolean mRunSimpleAnimations=false;
boolean mRunPredictiveAnimations=false;
int mFocusedItemPosition;
int mFocusedSubChildId;
int mRemainingScrollHorizontal;
int mRemainingScrollVertical;
void prepareForNestedPrefetch(Adapter adapter){
mLayoutStep=STEP_START;
mItemCount=adapter.getItemCount();
mInPreLayout=false;
mTrackOldChangeHolders=false;
mIsMeasuring=false;
}
public boolean isMeasuring(){
return mIsMeasuring;
}
public boolean isPreLayout(){
return mInPreLayout;
}
public boolean willRunPredictiveAnimations(){
return mRunPredictiveAnimations;
}
public boolean willRunSimpleAnimations(){
return mRunSimpleAnimations;
}
public void remove(int resourceId){
if (mData == null) {
return;
}
mData.remove(resourceId);
}
public <T>T get(int resourceId){
if (mData == null) {
return null;
}
return (T)mData.get(resourceId);
}
public void put(int resourceId,Object data){
if (mData == null) {
mData=new SparseArray<Object>();
}
mData.put(resourceId,data);
}
public int getTargetScrollPosition(){
return mTargetPosition;
}
public boolean hasTargetScrollPosition(){
return mTargetPosition != RecyclerView.NO_POSITION;
}
public boolean didStructureChange(){
return mStructureChanged;
}
public int getItemCount(){
return mInPreLayout ? (mPreviousLayoutItemCount - mDeletedInvisibleItemCountSincePreviousLayout) : mItemCount;
}
public int getRemainingScrollHorizontal(){
return mRemainingScrollHorizontal;
}
public int getRemainingScrollVertical(){
return mRemainingScrollVertical;
}
public String toString(){
return "State{" + "mTargetPosition=" + mTargetPosition + ", mData="+ mData+ ", mItemCount="+ mItemCount+ ", mIsMeasuring="+ mIsMeasuring+ ", mPreviousLayoutItemCount="+ mPreviousLayoutItemCount+ ", mDeletedInvisibleItemCountSincePreviousLayout="+ mDeletedInvisibleItemCountSincePreviousLayout+ ", mStructureChanged="+ mStructureChanged+ ", mInPreLayout="+ mInPreLayout+ ", mRunSimpleAnimations="+ mRunSimpleAnimations+ ", mRunPredictiveAnimations="+ mRunPredictiveAnimations+ '}';
}
}
public abstract static class ItemAnimator {
public static final int FLAG_CHANGED=ViewHolder.FLAG_UPDATE;
public static final int FLAG_REMOVED=ViewHolder.FLAG_REMOVED;
public static final int FLAG_INVALIDATED=ViewHolder.FLAG_INVALID;
public static final int FLAG_MOVED=ViewHolder.FLAG_MOVED;
public static final int FLAG_APPEARED_IN_PRE_LAYOUT=ViewHolder.FLAG_APPEARED_IN_PRE_LAYOUT;
private long mAddDuration=120;
private long mRemoveDuration=120;
private long mMoveDuration=250;
private long mChangeDuration=250;
public long getMoveDuration(){
return mMoveDuration;
}
public void setMoveDuration(long moveDuration){
mMoveDuration=moveDuration;
}
public long getAddDuration(){
return mAddDuration;
}
public void setAddDuration(long addDuration){
mAddDuration=addDuration;
}
public long getRemoveDuration(){
return mRemoveDuration;
}
public void setRemoveDuration(long removeDuration){
mRemoveDuration=removeDuration;
}
public long getChangeDuration(){
return mChangeDuration;
}
public void setChangeDuration(long changeDuration){
mChangeDuration=changeDuration;
}
public ItemHolderInfo recordPreLayoutInformation(State state,ViewHolder viewHolder,int changeFlags,List<Object> payloads){
return obtainHolderInfo().setFrom(viewHolder);
}
public ItemHolderInfo recordPostLayoutInformation(State state,ViewHolder viewHolder){
return obtainHolderInfo().setFrom(viewHolder);
}
public abstract boolean animateDisappearance(ViewHolder viewHolder,ItemHolderInfo preLayoutInfo,ItemHolderInfo postLayoutInfo);
public abstract boolean animateAppearance(ViewHolder viewHolder,ItemHolderInfo preLayoutInfo,ItemHolderInfo postLayoutInfo);
public abstract boolean animatePersistence(ViewHolder viewHolder,ItemHolderInfo preLayoutInfo,ItemHolderInfo postLayoutInfo);
public abstract boolean animateChange(ViewHolder oldHolder,ViewHolder newHolder,ItemHolderInfo preLayoutInfo,ItemHolderInfo postLayoutInfo);
static int buildAdapterChangeFlagsForAnimations(ViewHolder viewHolder){
int flags=viewHolder.mFlags & (FLAG_INVALIDATED | FLAG_REMOVED | FLAG_CHANGED);
if (viewHolder.isInvalid()) {
return FLAG_INVALIDATED;
}
if ((flags & FLAG_INVALIDATED) == 0) {
final int oldPos=viewHolder.getOldPosition();
final int pos=viewHolder.getAbsoluteAdapterPosition();
if (oldPos != NO_POSITION && pos != NO_POSITION && oldPos != pos) {
flags|=FLAG_MOVED;
}
}
return flags;
}
public abstract void runPendingAnimations();
public abstract void endAnimation(ViewHolder item);
public abstract void endAnimations();
public void onAnimationFinished(ViewHolder viewHolder){
}
public void onAnimationStarted(ViewHolder viewHolder){
}
public boolean canReuseUpdatedViewHolder(ViewHolder viewHolder){
return true;
}
public boolean canReuseUpdatedViewHolder(ViewHolder viewHolder,List<Object> payloads){
return canReuseUpdatedViewHolder(viewHolder);
}
public ItemHolderInfo obtainHolderInfo(){
return new ItemHolderInfo();
}
public static class ItemHolderInfo {
public int left;
public int top;
public int right;
public int bottom;
public int changeFlags;
public ItemHolderInfo(){
}
public ItemHolderInfo setFrom(RecyclerView.ViewHolder holder){
return setFrom(holder,0);
}
public ItemHolderInfo setFrom(RecyclerView.ViewHolder holder,int flags){
final View view=holder.itemView;
this.left=view.getLeft();
this.top=view.getTop();
this.right=view.getRight();
this.bottom=view.getBottom();
return this;
}
}
}
private NestedScrollingChildHelper getScrollingChildHelper(){
if (mScrollingChildHelper == null) {
mScrollingChildHelper=new NestedScrollingChildHelper(this);
}
return mScrollingChildHelper;
}
SavedState mPendingSavedState;
public static final int TYPE_TOUCH=0;
public static class SavedState {
Parcelable mLayoutState;
}
ItemAnimator mItemAnimator;
public RecyclerView(){
mMaxFlingVelocity=0;
mMinFlingVelocity=0;
initAdapterManager();
initChildrenHelper();
}
void dispatchPendingImportantForAccessibilityChanges(){
}
private void dispatchContentChangedIfNecessary(){
}
private boolean predictiveItemAnimationsEnabled(){
return false;
}
private void saveFocusInfo(){
}
void postAnimationRunner(){
}
private void recoverFocusFromState(){
}
private void resetFocusInfo(){
}
public int getScrollY(){
return 0;
}
public int getScrollX(){
return 0;
}
private void onScrollChanged(int scrollX,int scrollY,int i,int j){
}
private void onScrolled(int hresult,int vresult){
}
final void fillRemainingScrollValues(State state){
}
boolean isAccessibilityEnabled(){
return false;
}
public long getDrawingTime(){
return 0;
}
public void stopScroll(){
}
static class Observable<T> {
protected final ArrayList<T> mObservers=new ArrayList<T>();
public void unregisterObserver(T observer){
mObservers.remove(observer);
}
public void registerObserver(T observer){
mObservers.add(observer);
}
}
public void registerObserver(AdapterDataObserver observer){
}
protected boolean awakenScrollBars(){
return false;
}
void considerReleasingGlowsOnScroll(int dx,int dy){
}
private void pullGlows(float x,float overscrollX,float y,float overscrollY){
}
int getOverScrollMode(){
return View.OVER_SCROLL_NEVER;
}
static class MotionEventCompat {
public static boolean isFromSource(MotionEvent ev,int source){
return false;
}
}
static class InputDevice {
public static final int SOURCE_MOUSE=0;
}
static class NestedScrollingChildHelper {
public NestedScrollingChildHelper(View view){
}
public boolean dispatchNestedScroll(int dxConsumed,int dyConsumed,int dxUnconsumed,int dyUnconsumed,int[] offsetInWindow){
return false;
}
public void dispatchNestedScroll(int dxConsumed,int dyConsumed,int dxUnconsumed,int dyUnconsumed,int[] offsetInWindow,int type,int[] consumed){
}
public boolean dispatchNestedScroll(int dxConsumed,int dyConsumed,int dxUnconsumed,int dyUnconsumed,int[] offsetInWindow,int type){
return false;
}
}
}
