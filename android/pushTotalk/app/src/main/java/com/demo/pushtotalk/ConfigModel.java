package com.demo.pushtotalk;

import android.content.Context;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.FileInputStream;
import java.io.FileOutputStream;


public class ConfigModel implements Common{
    static  String TAG="[*** CONFM]";
    static String configFileName = "user.conf";
    private MainActivity contex;
    private ExecutorService mExecutorService;

    public String serverIp;
    public int serverPort = -1;
    public boolean isAutoConnecting = false;

    public boolean isConfigReady = false;

    ConfigModel(MainActivity ctx) {
        this.contex = ctx;
        mExecutorService = Executors.newSingleThreadExecutor();
        this.loadConfig();
    }


    public void setServerIp(String ip){
        this.serverIp = ip;
    }

    public boolean setServerPort(String port){
        try {
          int v= Integer.parseInt(port);
          serverPort = v;
        }catch (NumberFormatException e){
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private void loadConfig(){
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    do{
                        FileInputStream fis = contex.openFileInput(configFileName);

                        byte[] buffer = new byte[64];
                        int len = fis.read(buffer);

                        int start=0,end =0;
                        while(end<len && buffer[end] != ':'){
                            end++;
                        }

                        if(end <= 15){ // xxx.xxx.xxx.xxx
                            serverIp = new String(Arrays.copyOfRange(buffer,start,end),"UTF-8");
                        }else{
                            Log.d(TAG, "config file [ip] error");
                            break;
                        }

                        start = end+1;
                        end = start + 4;

                        if(end>len || buffer[end]!= ':'){
                            Log.d(TAG, "config file [port] error");
                            break;
                        }

                        serverPort = DemoMessage.arr2int(Arrays.copyOfRange(buffer,start,end));

                        start = end + 1;
                        if(start > len){
                            Log.d(TAG, "config file [auto] error");
                            break;
                        }

                        if(buffer[start] > 0){
                            isAutoConnecting = true;
                        }else{
                            isAutoConnecting = false;
                        }

                        isConfigReady = true;
                        fis.close();
                    }while(false);

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }catch (IOException e){
                    e.printStackTrace();
                }

                if(!isConfigReady){
                    // config file is empty or load failed

                    serverIp = CONFIG_SERVER_IP;
                    serverPort = CONFIG_SERVER_PORT;

                    isAutoConnecting = false;
                    isConfigReady = true;
                }

                if(isAutoConnecting){
                    // waiting for tcpService ready
                    while(contex.getTcpService()==null);

                    contex.connectServer();
                }

            }
        });
    }


    public void saveConfig() {

        if(serverIp == null || serverPort < 0){
            return;
        }

        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    FileOutputStream fos = contex.openFileOutput(configFileName, Context.MODE_PRIVATE);
                    byte[] data = serverIp.getBytes();
                    int ipLen = data.length;
                    int offset = ipLen;
                    data = Arrays.copyOf(data, ipLen + 7);
                    data[offset++] = ':';

                    byte[] portBytes = DemoMessage.int2arr(serverPort);

                    for(int i =0; i< 4;i++){
                        data[offset++] = portBytes[i];
                    }

                    data[offset++] = ':';

                    data[offset] = isAutoConnecting?(byte)1:(byte)0;

                    fos.write(data);
                    fos.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }catch (IOException e){
                    e.printStackTrace();
                }

            }
        });
    }


}
