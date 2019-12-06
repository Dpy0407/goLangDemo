package com.demo.pushtotalk;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class VoiceListAdapter extends BaseAdapter {
    static private String TAG = "[*** VADPT]";
    static public int TYPE_LEFT = 0;
    static public int TYPE_RIGHT = 1;
    private MainActivity context = null;
    private List<VoiceBean> voiceList = null;
    private LayoutInflater inflater = null;

    VoiceListAdapter(MainActivity context, List<VoiceBean> list) {
        this.context = context;
        this.voiceList = list;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return voiceList.size();
    }

    @Override
    public Object getItem(int posithion) {
        return voiceList.get(posithion);
    }


    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position){
        if (voiceList.get(position).ori == Common.VoiceOrientation.SEND){
            return TYPE_RIGHT;
        }else{
            return TYPE_LEFT;
        }
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            holder = new ViewHolder();

            if (getItemViewType(position) == TYPE_RIGHT) {
                convertView = inflater.inflate(R.layout.voice_list_item_right, null);
            } else {
                convertView = inflater.inflate(R.layout.voice_list_item_left, null);
            }


            holder.textTime = (TextView) convertView.findViewById(R.id.text_time);
            holder.viewSpeaker = (View) convertView.findViewById(R.id.speaker);
            holder.viewButton = (View) convertView.findViewById(R.id.play_view);
            convertView.setTag(holder);

        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.textTime.setText(String.format("%.1f\"", voiceList.get(position).duration * 1.0 / 1000));
        holder.viewButton.setTag(holder);
        holder.viewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                context.voicePlayStop();
                ViewHolder h = (ViewHolder) v.getTag();
                Log.d(TAG, h.textTime.getText().toString());
                context.voicePlay(voiceList.get(position).getFilePath(), h);
            }
        });

        return convertView;
    }


    public class ViewHolder {
        public TextView textTime;
        public View viewSpeaker;
        public View viewButton;
    }

}
