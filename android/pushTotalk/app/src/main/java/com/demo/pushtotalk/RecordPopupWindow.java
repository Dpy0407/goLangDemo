package com.demo.pushtotalk;

import android.animation.ValueAnimator;
import android.app.ProgressDialog;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.LogRecord;

public class RecordPopupWindow extends PopupWindow implements Common {
    static String TAG="[*** RPOPW]";
    private MainActivity context;
    private View conentView;

    private CircleProgress circleProgress;
    private TextView timeView;
    Timer timer;

    private int maxTimeMs = CONFIG_SPEAK_MAX_SECOND * 1000;

    public boolean isRecordStoped = false;

    public RecordPopupWindow(MainActivity ctx){
        super(ctx);
        this.context = ctx;
        LayoutInflater inflater = LayoutInflater.from(context);

        conentView = inflater.inflate(R.layout.pop_record_progress, null);
        int h = context.getWindowManager().getDefaultDisplay().getHeight();
        int w = context.getWindowManager().getDefaultDisplay().getWidth();


        this.setContentView(conentView);

        int size = w / 5 * 2;

        this.setWidth(size);
        this.setHeight(size);
        this.setFocusable(true);
        this.setOutsideTouchable(false);

//        this.setAnimationStyle(R.style.pop_window_anim_style);

        this.update();
        timeView = conentView.findViewById(R.id.record_time);
        circleProgress = conentView.findViewById(R.id.record_progress);
        circleProgress.setMax(maxTimeMs);
        circleProgress.setIndeterminate(false);
        circleProgress.setRadius(size/2 - 40);

        Log.e(TAG, "isIndeterminate: "+circleProgress.isIndeterminate());

        this.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss() {
                if(timer != null){
                    timer.cancel();
                    timer = null;
                }
            }
        });
    }

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg){
            Log.e(TAG, "msg.what"+msg.what);
            if(msg.what <= maxTimeMs ){
                circleProgress.setProgress(msg.what);
                if(msg.what % 100 == 0){
                    timeView.setText(String.format("%.1f",msg.what *1.0f/1000));
                }
            }else{
                if(timer != null){
                    timer.cancel();
                    timer = null;
                }
            }
        }
    };


    public void showPopupWindow() {

        if (!this.isShowing()) {
            isRecordStoped = false;
            this.startTimer();
            this.showAtLocation(conentView, Gravity.CENTER,0,0);

        } else {
            this.dismiss();
        }
    }


    private void startTimer(){
        this.timer = new Timer();
        final int step = 50;
        this.timer.schedule(new TimerTask() {
            int ms = 0;
            @Override
            public void run() {
                Message msg = new Message();
                msg.what = ms;
                mHandler.sendMessage(msg);

                ms += step;

                if(ms > maxTimeMs){
                    context.stopRecord();
                    isRecordStoped = true;
                }
            }
        }, 0, step);
    }

}
