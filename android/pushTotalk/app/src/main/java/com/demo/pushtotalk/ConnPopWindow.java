package com.demo.pushtotalk;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.PopupWindow;


public class ConnPopWindow extends PopupWindow {

    private MainActivity context;
    private View conentView;

    private CheckBox autoConnCheckBox = null;
    private EditText editTextIP = null;
    private  EditText editTextPort = null;

    public ConnPopWindow(MainActivity ctx){
        this.context = ctx;
        LayoutInflater inflater = LayoutInflater.from(context);

        conentView = inflater.inflate(R.layout.pop_connect_setting, null);
        int h = context.getWindowManager().getDefaultDisplay().getHeight();
        int w = context.getWindowManager().getDefaultDisplay().getWidth();

        this.setContentView(conentView);
        this.setWidth(w / 3 * 2 );
        this.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        this.setFocusable(true);
        this.setOutsideTouchable(false);

        this.setAnimationStyle(R.style.pop_window_anim_style);

        this.update();

        // enable focusable of TextEditor's parent view, otherwise, it's focuse status can't be cleared
        conentView.setFocusable(true);
        conentView.setFocusableInTouchMode(true);

        autoConnCheckBox = (CheckBox) conentView.findViewById(R.id.auto_checkbox);

        editTextIP = (EditText) conentView.findViewById(R.id.ip_editor);
        editTextPort = (EditText) conentView.findViewById(R.id.port_editor);
//        editTextIP.setFocusable(false);
//        editTextIP.setFocusableInTouchMode(true);
//        editTextPort.setFocusable(false);
//        editTextPort.setFocusableInTouchMode(true);
        conentView.findViewById(R.id.conn_setting_confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //todo: save configure
                context.mConfigModel.setServerIp(editTextIP.getText().toString());
                context.mConfigModel.setServerPort(editTextPort.getText().toString());
                context.mConfigModel.isAutoConnecting = autoConnCheckBox.isChecked();
                context.mConfigModel.saveConfig();

                context.connectServer();
                dismiss();
            }
        });


        conentView.findViewById(R.id.conn_setting_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // do nothing
                dismiss();
            }
        });


        autoConnCheckBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editTextIP.clearFocus();
                editTextPort.clearFocus();
                conentView.requestFocus();
            }
        });

    }


    public void showPopupWindow() {
        if(context.mConfigModel.isConfigReady){
            editTextIP.setText(context.mConfigModel.serverIp);
            editTextIP.setSelection(context.mConfigModel.serverIp.length());
            editTextIP.clearFocus();

            String port= String.format("%d", context.mConfigModel.serverPort);
            editTextPort.setText(port);
            editTextPort.setSelection(port.length());
            editTextPort.clearFocus();

            autoConnCheckBox.setChecked(context.mConfigModel.isAutoConnecting);
        }

        if (!this.isShowing()) {
//            this.showAsDropDown(parent, parent.getLayoutParams().width / 2, 5);
            this.showAtLocation(conentView, Gravity.CENTER,0,0);
        } else {
            this.dismiss();
        }
    }
}
