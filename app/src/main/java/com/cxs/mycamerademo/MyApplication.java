package com.cxs.mycamerademo;

import android.annotation.TargetApi;
import android.app.Application;
import android.os.Build;
import android.util.DisplayMetrics;


import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;

/**
 * @author cuishuxiang
 * @date 2017/12/4.
 */

public class MyApplication extends Application {
    public static MyApplication myApplication;

    public DatagramSocket datagramSocket;

    public final int PORT = 8080;

    @Override
    public void onCreate() {
        super.onCreate();
        myApplication = this;

        initSocket();

    }


    private void initSocket() {
        try {
            datagramSocket=new DatagramSocket(PORT);

        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public static MyApplication getInstance() {
        if (myApplication != null) {
            return myApplication;
        }
        return null;
    }
}
