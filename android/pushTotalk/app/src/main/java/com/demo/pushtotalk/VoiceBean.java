package com.demo.pushtotalk;

import android.media.MediaPlayer;
import android.util.Log;

import java.io.File;
import java.io.IOException;

public class VoiceBean implements Common {
    static String TAG = "[*** VBEAN]";

    private File file;

    public VoiceType type = VoiceType.INVAID;

    public int duration = 0;

    public long unixTime = 0;

    public VoiceStatus status = VoiceStatus.NONE;

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public void setFile(String path) {
        this.file = new File(path);
    }

    public String getFilePath() {
        return this.file.getAbsolutePath().toString();
    }

    public boolean load(String path) {
        this.setFile(path);
        return doLoad();
    }

    public boolean load(File file) {
        this.setFile((file));
        return doLoad();
    }

    private boolean doLoad() {
        if (this.file == null) {
            return false;
        }

        if (!this.file.exists()) {
            Log.i(TAG, "file not exist, path = " + this.getFilePath());
            return false;
        }

        String fileName = this.file.getName();

        int idx = fileName.indexOf("_");

        if (idx > 0 && (fileName.length() > idx + 2)) {
            if (fileName.toCharArray()[idx + 1] == 's') {
                this.type = VoiceType.SEND;
            } else {
                this.type = VoiceType.RECEIVE;
            }
        } else {
            return false;
        }

        String time_s = fileName.substring(0, idx);
        try {
            this.unixTime = Long.parseLong(time_s);
        } catch (NumberFormatException e) {
            return false;
        }


        MediaPlayer player = new MediaPlayer();
        try {
            player.setDataSource(this.getFilePath());
            player.prepare();
            this.duration = player.getDuration();
            player.release();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
