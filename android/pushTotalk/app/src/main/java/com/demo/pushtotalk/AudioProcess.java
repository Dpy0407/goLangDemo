package com.demo.pushtotalk;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import android.content.Context;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class AudioProcess {
    static private String TAG = "[*** AUDIO]";

    private MainActivity context;

    private ExecutorService mExecutorService;
    private MediaRecorder mMediaRecorder;
    private MediaPlayer mediaPlayer;

    private long startTime, endTime;
    private File mAudioFile;

    public String mFilePath = Environment.getExternalStorageDirectory().toString() + "/audio/";
    private volatile boolean isPlaying;
    private volatile boolean isRecording;

    public String lastRecordPath = null;
    public String lastSavePath = null;

    AudioProcess(MainActivity ctx) {
        context = ctx;
        // audiorecord run with single thread pool
        mExecutorService = Executors.newSingleThreadExecutor();
        isRecording = false;
        isPlaying = false;

    }

    public void PrintPaths() {
        String rootDir = Environment.getRootDirectory().toString();
        Log.d(TAG, "Environment.getRootDirectory()=:" + rootDir);

        //:/data 用户数据目录
        String dataDir = Environment.getDataDirectory().toString();
        Log.d(TAG, "Environment.getDataDirectory()=:" + dataDir);

        //:/cache 下载缓存内容目录
        String cacheDir = Environment.getDownloadCacheDirectory().toString();
        Log.d(TAG, "Environment.getDownloadCacheDirectory()=:" + cacheDir);

        String storageDir = Environment.getExternalStorageDirectory().toString();
        Log.d(TAG, "Environment.getExternalStorageDirectory()=:" + storageDir);


    }


    public void startRecord() {
        if (isRecording) {
            return;
        }
        lastRecordPath = null;
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "record start");
                releaseRecorder();
                recordOperation();
            }
        });
    }


    private void recordFail() {
        mAudioFile = null;
    }

    private void recordOperation() {
        mMediaRecorder = new MediaRecorder();
        mAudioFile = new File(mFilePath + System.currentTimeMillis() + "_s.amr");
        mAudioFile.getParentFile().mkdirs();

        try {
            // create file
            mAudioFile.createNewFile();
            // condif mic as record src
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            // format
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
            //sampling rate
            mMediaRecorder.setAudioSamplingRate(44100);
            //encoding format
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            //encoding rate
            mMediaRecorder.setAudioEncodingBitRate(96000);
            //audio restore path
            mMediaRecorder.setOutputFile(mAudioFile.getAbsolutePath());

            //start record
            mMediaRecorder.prepare();
            mMediaRecorder.start();

            startTime = System.currentTimeMillis();
            isRecording = true;
        } catch (Exception e) {
            e.printStackTrace();
            recordFail();
        }
    }

    synchronized public void stopRecord() {
        if (!isRecording) {
            return;
        }


        if (mMediaRecorder == null) {
            return;
        }

        mMediaRecorder.setOnErrorListener(null);
        mMediaRecorder.setOnInfoListener(null);
        mMediaRecorder.setPreviewDisplay(null);

        try {
            mMediaRecorder.stop();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (RuntimeException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        endTime = System.currentTimeMillis();

        float time = (float) ((endTime - startTime) * 1.0 / 1000);
        Log.d(TAG, "record time:" + time);

        if (time >= 1) {
//            File newFile = new File(mAudioFile.getAbsoluteFile().toString()+"_"+Math.round(time)+".amr");
//            mAudioFile.renameTo(newFile);
            lastRecordPath = mAudioFile.getAbsoluteFile().toString();
        } else {
            mAudioFile.delete();
            mAudioFile = null;
            lastRecordPath = null;
            //TODO: delete files
        }
        isRecording = false;
        releaseRecorder();
    }


    private void releaseRecorder() {
        if (null != mMediaRecorder) {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    public void playAudio(final String path) {
        if (null != path && !isPlaying) {
            isPlaying = true;
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    File f = new File(path);
                    startPlay(f);
                }
            });
        }
    }

    private void startPlay(File mFile) {
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

            FileInputStream fis = new FileInputStream(mFile.getAbsolutePath());

            mediaPlayer.setDataSource(fis.getFD());

            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    playEndOrFail(true);
                }
            });

            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                    playEndOrFail(false);
                    return true;
                }
            });

            mediaPlayer.setVolume(10, 10);

            mediaPlayer.setLooping(false);

            mediaPlayer.prepare();

            Log.d(TAG, "duration:" + mediaPlayer.getDuration());


            mediaPlayer.start();
        } catch (IOException e) {
            e.printStackTrace();

            playEndOrFail(false);
        }

    }

    private void playEndOrFail(boolean isEnd) {
        isPlaying = false;
        if (isEnd) {
            //todo: stop
            Log.d(TAG, "play end");
        } else {
            Log.d(TAG, "play error");
            //todo: unexpect stop
        }
        if (null != mediaPlayer) {
            mediaPlayer.setOnCompletionListener(null);
            mediaPlayer.setOnErrorListener(null);
            mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        context.onVoicePlayStop();
    }


    public void stopPlay() {
        if (isPlaying) {
            playEndOrFail(true);
        }
    }

    public void startSend(final String path) {
        Log.d(TAG, "start sending...");

        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    FileInputStream fis = new FileInputStream(path);
                    int size = fis.available();
                    byte[] data = new byte[size];
                    int len = 0;
                    if (fis.read(data) != -1) {
                        fis.close();
                        context.onSendVoice(data);
                    }

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    return;
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
        });

    }

    public void saveAudio(final byte[] data) {
        lastSavePath = null;
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                File file = new File(mFilePath + System.currentTimeMillis() + "_r.amr");
                file.getParentFile().mkdirs();
                try {
//                file.createNewFile();
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(data);
                    fos.close();
                    lastSavePath = file.getAbsoluteFile().toString();
                    context.onSaveVoiceResult(true);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    context.onSaveVoiceResult(false);
                    return;
                } catch (IOException e) {
                    e.printStackTrace();
                    context.onSaveVoiceResult(false);
                    return;
                }
            }
        });

    }


    public File[] getAllFiles() {
        File folder = new File(this.mFilePath);
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            return null;
        }

        Arrays.sort(files);

//        for (File f : files) {
//            Log.d(TAG, f.getAbsolutePath());
//        }

        return files;
    }

    public void clearFile(final String path) {
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                File file = new File(path);
                file.delete();
            }
        });
    }

    public void clearAllFiles() {
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                File folder = new File(mFilePath);
                File[] files = folder.listFiles();
                if (files == null || files.length == 0) {
                    return;
                }

                for (File f : files) {
                    f.delete();
                }
            }
        });
    }

    public void onExit(final List<VoiceBean> list) {

        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                for (VoiceBean item : list) {
                    String filePath = item.getFilePath();
                    if (!filePath.endsWith(".amr")) {
                        continue;
                    }
                    Log.d(TAG, "exit:" + filePath);
                    String tmp = filePath.substring(0, filePath.length() - 4);
                    if (item.status == Common.VoiceStatus.UNREAD) {
                        if (tmp.endsWith("_ur")) {
                            continue;
                        }
                        String newName = filePath.substring(0, filePath.length() - 4) + "_ur" + ".amr";
                        File newFile = new File(newName);
                        item.getFile().renameTo(newFile);
                    } else if (item.status == Common.VoiceStatus.SEND_FAILED) {
                        if (tmp.endsWith("_sf")) {
                            continue;
                        }
                        String newName = filePath.substring(0, filePath.length() - 4) + "_sf" + ".amr";
                        File newFile = new File(newName);
                        item.getFile().renameTo(newFile);
                    } else {
                        if (tmp.endsWith("_ur") || tmp.endsWith("_sf")) {
                            String newName = tmp.substring(0, tmp.length() - 3) + ".amr";
                            File newFile = new File(newName);
                            item.getFile().renameTo(newFile);
                        }
                    }
                }
            }
        });
    }


    @Override
    protected void finalize() {
        mExecutorService.shutdownNow();
    }
}
