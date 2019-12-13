package com.demo.pushtotalk;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

public class MenuPopWindow  extends PopupWindow {
    private LayoutInflater inflater;
    private View conentView;
    MainActivity context = null;

    private PopupWindow clearConfirm = null;
    public MenuPopWindow(final MainActivity context){
        this.context = context;
        inflater = LayoutInflater.from(context);

//                (LayoutInflater) context
//                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        conentView = inflater.inflate(R.layout.main_menu, null);
        int h = context.getWindowManager().getDefaultDisplay().getHeight();
        int w = context.getWindowManager().getDefaultDisplay().getWidth();

        this.setContentView(conentView);
        this.setWidth(w / 2 - 60);
        this.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        this.setFocusable(true);
        this.setOutsideTouchable(true);

        this.update();

        this.initConfirmPopupWindow(w / 3 * 2);


        conentView.findViewById(R.id.connect_settiong).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(context == null){
                    return;
                }
                dismiss();

                context.connPopWindow.showPopupWindow();

            }
        });

        conentView.findViewById(R.id.clear_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(context == null){
                    return;
                }
                dismiss();

//                context.clearAllVoice();
                showConfirm();
            }
        });

    }



    public void showPopupWindow(View parent) {
        if (!this.isShowing()) {
            this.showAsDropDown(parent, parent.getLayoutParams().width / 2, 5);
        } else {
            this.dismiss();
        }
    }



    public void initConfirmPopupWindow(int width){
        clearConfirm = new PopupWindow();
        View content = inflater.inflate(R.layout.pop_confirm, null);
        clearConfirm.setContentView(content);

        clearConfirm.setWidth(width);
        clearConfirm.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        clearConfirm.setFocusable(true);
        clearConfirm.setOutsideTouchable(true);
        clearConfirm.update();


        content.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearConfirm.dismiss();
            }
        });

        content.findViewById(R.id.confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearConfirm.dismiss();
                context.clearAllVoice();
            }
        });
    }

    public void showConfirm() {
        if (!clearConfirm.isShowing()) {
            clearConfirm.showAtLocation(conentView, Gravity.CENTER,0,0);

        } else {
            clearConfirm.dismiss();
        }
    }

}
