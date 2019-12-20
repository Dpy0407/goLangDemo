package com.demo.pushtotalk;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ProgressBar;

import static androidx.appcompat.widget.TintTypedArray.obtainStyledAttributes;

public class CircleProgress extends ProgressBar {

    static String TAG="[*** CPREOG]";
    private Paint mPaint = null;
    private  int strokeWidth;
    private int mRadius;

    public CircleProgress(Context context, AttributeSet attrs){
        super(context, attrs);

//        TypedArray a= context.getTheme().obtainStyledAttributes(attrs,new int[]{android.R.attr.layout_width,android.R.attr.layout_height}, 0, 0);
//        float width = a.getDimension(0, 0f);
//
//        Log.d(TAG, "typedarray layout_width: " + width);
//        float height = a.getDimension(1, 0f);
//        Log.d(TAG, "typedarray layout_width: " + height);
        mPaint = new Paint();
        strokeWidth = 20;
        mRadius = 120;

        super.setIndeterminate(false);


    }

//    CircleProgress(Context context, AttributeSet attrs, int defStyleAttr){
//        super(context, attrs, defStyleAttr);
//        mPaint = new Paint();
//
//    }

    @Override
    protected void onDraw(Canvas canvas) {

        int cx = getWidth()/2;
        int cy= getHeight()/2;

        canvas.drawColor(getResources().getColor(R.color.empty));

        mPaint.setColor(getResources().getColor(R.color.recordForeground));
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(strokeWidth);
        canvas.drawCircle(cx, cy, mRadius, mPaint);

        mPaint.setColor(getResources().getColor(R.color.recordProgress));
        mPaint.setStrokeWidth(strokeWidth+4);
        mPaint.setAntiAlias(true);

        float sweepAngle = getProgress() * 1.0f / getMax() * 360;

        RectF oval= new RectF(cx - mRadius,cy - mRadius, cx+mRadius,cy+mRadius);


        canvas.drawArc(oval, -90, sweepAngle, false, mPaint);

    }

    @Override
    protected synchronized void onMeasure(int widthMeasureSpec,  int heightMeasureSpec){
        int width =MeasureSpec.getSize(widthMeasureSpec);
        int height=MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(width, height);
    }

    @Override
    public synchronized void setProgress(int progress) {
        super.setProgress(progress);
        onSizeChanged(getWidth(), getHeight(), 0, 0);
    }

    public void setRadius(int r){
        this.mRadius = r;
    }

}
