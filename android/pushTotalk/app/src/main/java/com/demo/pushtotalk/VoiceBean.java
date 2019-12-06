package com.demo.pushtotalk;

import android.media.MediaPlayer;
import android.util.Log;

import java.io.File;
import java.io.IOException;

public class VoiceBean implements Common {
    static String TAG = "[*** VBEAN]";

    private File file;

    public VoiceOrientation ori = VoiceOrientation.INVAID;

    public int duration = 0;

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public void setFile(String path){
        this.file = new File(path);
    }

    public String getFilePath(){
        return this.file.getAbsolutePath().toString();
    }

    public boolean load(String path){
        this.setFile(path);
        return doLoad();
    }

    public boolean load(File file){
        this.setFile((file));
        return doLoad();
    }

    private boolean doLoad(){
        if(this.file == null){
            return false;
        }

        if(!this.file.exists()){
            Log.i(TAG, "file not exist, path = "+this.getFilePath());
            return false;
        }

        String fileName = this.file.getName();

        if(fileName.indexOf("_s")>0){
            this.ori = VoiceOrientation.SEND;
        }else{
            this.ori = VoiceOrientation.RECEIVE;
        }

        MediaPlayer player = new MediaPlayer();
        try {
            player.setDataSource(this.getFilePath());
            player.prepare();
            this.duration = player.getDuration();
            player.release();
        }catch (IOException e){
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
