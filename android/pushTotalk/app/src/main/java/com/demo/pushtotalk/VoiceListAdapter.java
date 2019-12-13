package com.demo.pushtotalk;

import android.graphics.Rect;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class VoiceListAdapter extends BaseAdapter {
    static private String TAG = "[*** VADPT]";
    static public int TYPE_LEFT = 0;
    static public int TYPE_RIGHT = 1;
    private MainActivity context = null;
    private List<VoiceBean> voiceList = null;
    private LayoutInflater inflater = null;

    private SimpleDateFormat dataFormat;
    private int timeIdx = 0;

    private ItemPopupWindow itemMenu = null;
    private ListView mainList = null;

    VoiceListAdapter(MainActivity context, List<VoiceBean> list) {
        this.context = context;
        this.voiceList = list;
        this.inflater = LayoutInflater.from(context);
        String strDateFormat = "yyyy/MM/dd HH:mm";
        this.dataFormat = new SimpleDateFormat(strDateFormat);
        this.timeIdx = strDateFormat.indexOf("HH:mm");
        this.itemMenu = new ItemPopupWindow();
        mainList = context.findViewById(R.id.voice_list);
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
    public int getItemViewType(int position) {
        if (voiceList.get(position).type == Common.VoiceType.SEND) {
            return TYPE_RIGHT;
        } else {
            return TYPE_LEFT;
        }
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    private boolean isDisplayTime(int pos) {
        Log.d(TAG, pos + "--> data: " + new Date(voiceList.get(pos).unixTime));

        if (pos == 0) return true;

        if ((voiceList.get(pos).unixTime - voiceList.get(pos - 1).unixTime) > 3 * 60 * 1000) { // over 3min, display the time
            return true;
        }

        return false;
    }


    private String getTimeDisplay(int pos) {
        String curDate = dataFormat.format(new Date(voiceList.get(pos).unixTime));

        if (pos == 0) return curDate;

        String lastDate = dataFormat.format(new Date(voiceList.get(pos - 1).unixTime));

        if (curDate.substring(0, timeIdx).compareTo(lastDate.substring(0, timeIdx)) == 0) {
            return curDate.substring(timeIdx);
        }

        return curDate;
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
            holder.textDuration = (TextView) convertView.findViewById(R.id.text_duration);
            holder.viewSpeaker = (View) convertView.findViewById(R.id.speaker);
            holder.viewButton = (View) convertView.findViewById(R.id.play_view);
            holder.viewStatus = (ImageView) convertView.findViewById(R.id.status);

            convertView.setTag(holder);

        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.textDuration.setText(String.format("%.1f\"", voiceList.get(position).duration * 1.0 / 1000));
        holder.viewButton.setTag(holder);
        holder.viewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                context.voicePlayStop();
                ViewHolder h = (ViewHolder) v.getTag();
                if (voiceList.get(position).type == Common.VoiceType.RECEIVE) {
                    VoiceBean vb = voiceList.get(position);
                    if (vb.status == Common.VoiceStatus.UNREAD) {
                        h.viewStatus.setBackgroundResource(R.color.empty);
                        vb.status = Common.VoiceStatus.NONE;
                        voiceList.set(position, vb);
                    }
                }

                Log.d(TAG, h.textTime.getText().toString());
                context.voicePlay(voiceList.get(position).getFilePath(), h);
            }
        });

        holder.viewButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                itemMenu.showPopupWindow(v, position);
                Log.d(TAG, "long clicked");
                return true;
            }
        });


        if (isDisplayTime(position)) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.textTime.getLayoutParams();
            params.height = 60;
            params.topMargin = 15;
            params.bottomMargin = 15;
            holder.textTime.setLayoutParams(params);


            holder.textTime.setText(getTimeDisplay(position));

        } else {
            holder.textTime.setText("");

            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.textTime.getLayoutParams();
            params.height = 0;
            params.topMargin = 0;
            params.bottomMargin = 0;
            holder.textTime.setLayoutParams(params);
        }

        if (voiceList.get(position).type == Common.VoiceType.RECEIVE) {
            if (voiceList.get(position).status == Common.VoiceStatus.UNREAD) {
                holder.viewStatus.setBackgroundResource(R.drawable.circle_red);
            } else {
                holder.viewStatus.setBackgroundResource(R.color.empty);
            }
        } else if (voiceList.get(position).type == Common.VoiceType.SEND) {
            if (voiceList.get(position).status == Common.VoiceStatus.SEND_FAILED) {
                holder.viewStatus.setBackgroundResource(R.drawable.ic_warning);
            } else {
                holder.viewStatus.setBackgroundResource(R.color.empty);
            }
        }


        return convertView;
    }


    public class ViewHolder {
        public TextView textTime;
        public TextView textDuration;
        public View viewSpeaker;
        public View viewButton;
        public ImageView viewStatus;
    }


    public class ItemPopupWindow extends PopupWindow {
        private View content;
        private ListView listView;
        private ArrayAdapter<String> adapter = null;

        private int currentIdx = -1;

        final String DELETE = "Delete";
        final String RETRY = "Resend";

        public ItemPopupWindow() {
            this.content = LayoutInflater.from(context).inflate(R.layout.voice_menu, null);
            this.setContentView(this.content);

            listView = this.content.findViewById(R.id.menu_list);
            int w = context.getWindowManager().getDefaultDisplay().getWidth();
            this.setWidth(w / 4);
            this.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            this.setFocusable(true);
            this.setOutsideTouchable(true);
            this.update();

            this.adapter = new ArrayAdapter<String>(context, R.layout.menu_list_item);
            listView.setAdapter(adapter);

            listView.setOnItemClickListener(new ListView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                    String item = adapter.getItem(arg2);
                    if (currentIdx < 0 || currentIdx >= voiceList.size()) {
                        Log.e(TAG, "index error");
                        return;
                    }

                    if (item == DELETE) {
                        context.deleteVoice(currentIdx);
                    } else if (item == RETRY) {
                        context.retrySendVoice(currentIdx);
                    }

                    dismiss();
                }
            });
        }


        public void showPopupWindow(View parent, int index) {
            if (!this.isShowing()) {

                adapter.clear();
                currentIdx = index;

                if (voiceList.get(currentIdx).status == Common.VoiceStatus.SEND_FAILED) {
                    adapter.add(RETRY);
                }
                adapter.add(DELETE);

                int height = Utils.dip2px(context, adapter.getCount() * 40 + 2);

                this.setHeight(height);

                adapter.notifyDataSetChanged();

                int windowPos[] = calculatePopWindowPos(parent, height);

                this.showAtLocation(parent, Gravity.TOP | Gravity.START, windowPos[0], windowPos[1]);
            } else {
                this.dismiss();
            }
        }

        private int[] calculatePopWindowPos(final View parent, int vh) {
            int windowPos[] = new int[2];

            Rect mainRect = new Rect();
            Rect parentRect = new Rect();

            int offset = 20;
            parent.getGlobalVisibleRect(parentRect);
            mainList.getGlobalVisibleRect(mainRect);

            boolean isNeedShowDown = false;

            if (parentRect.top - vh - offset < mainRect.top) {
                isNeedShowDown = true;
            }


            if (isNeedShowDown) {
                windowPos[0] = parentRect.left;
                windowPos[1] = parentRect.bottom + offset;

            } else {
                windowPos[0] = parentRect.left;
                windowPos[1] = parentRect.top - vh - offset;
            }

            return windowPos;
        }

    }


}
