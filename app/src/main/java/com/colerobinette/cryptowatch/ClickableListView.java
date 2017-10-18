package com.colerobinette.cryptowatch;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ListView;

public class ClickableListView extends ListView {

    private OnNoItemClickListener mOnNoItemClickListener;

    public interface OnNoItemClickListener {
        void onNoItemClicked();
    }

    public ClickableListView(Context context) {
        super(context);
    }

    public ClickableListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ClickableListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        //check whether the touch hit any elements INCLUDING ListView footer
        if(pointToPosition((int) (ev.getX() * ev.getXPrecision()),
                (int) (ev.getY() * ev.getYPrecision())) == -1 && ev.getAction() == MotionEvent.ACTION_DOWN) {
            if(mOnNoItemClickListener != null) {
                mOnNoItemClickListener.onNoItemClicked();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    public void setOnNoItemClickListener(OnNoItemClickListener listener) {
        mOnNoItemClickListener = listener;
    }
}