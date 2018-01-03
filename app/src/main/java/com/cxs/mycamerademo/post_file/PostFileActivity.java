package com.cxs.mycamerademo.post_file;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.cxs.mycamerademo.MyApplication;
import com.cxs.mycamerademo.R;
import com.cxs.mycamerademo.utils.DigitalLoadingView;
import com.cxs.mycamerademo.utils.FTPManager;
import com.zhihu.matisse.Matisse;
import com.zhihu.matisse.MimeType;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * @author cuishuxiang
 * @date 2017/12/4.
 */

public class PostFileActivity extends AppCompatActivity {
    private static final String TAG = "PostFileActivity";
    @BindView(R.id.choose_video_btn)
    Button chooseVideoBtn;
    @BindView(R.id.loading_view)
    DigitalLoadingView loadingView;

    private static final int VIDEO_REQUEST_CODE = 1;

    private List<Uri> uriList;

    private FTPManager ftpManager;

    private String SERVER_IP;
    private int PORT = MyApplication.getInstance().PORT;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_file);
        ButterKnife.bind(this);
        SERVER_IP = getIntent().getStringExtra("SERVER_IP");
        Log.d(TAG, "onCreate: 接收到SERVER_IP = " + SERVER_IP);

    }

    @OnClick(R.id.choose_video_btn)
    public void onViewClicked() {
        Matisse.from(this)
                .choose(MimeType.ofVideo())
                .maxSelectable(1)
                .showSingleMediaType(true)
                .forResult(VIDEO_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VIDEO_REQUEST_CODE) {

            if (resultCode == RESULT_OK) {

                uriList = Matisse.obtainResult(data);
                Uri uri = uriList.get(0);
                final String path = getRealFilePath(this, uri);

                SendFileThread sendFileThread = new SendFileThread(path);
                sendFileThread.start();

            }
        }
    }


    class SendFileThread extends Thread {
        private String localFilePath;

        public SendFileThread(String localPath) {
            this.localFilePath = localPath;
        }

        @Override
        public void run() {
            try {
                Log.d(TAG, "run: 连接 ftp 服务器");
                if (ftpManager == null) {
                    ftpManager = new FTPManager();
                }

                Log.d(TAG, "run: 连接成功 ？ " + ftpManager.connect());
                if (ftpManager.connect()) {
                    if (ftpManager.uploadFile(localFilePath, "/video/")) {
                        ftpManager.closeFTP();
                    }
                }

                ftpManager.setProgressCallBack(new FTPManager.ProgressCallback() {
                    @Override
                    public void getProgressCallback(final int progress, final String localName) {
                        Log.d(TAG, "getProgressCallback: " + progress);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                loadingView.setVisibility(View.VISIBLE);
                                loadingView.setProgressNum(progress);
                                if (100 == progress) {
                                    //上传完成
                                    String order = "200007:" + localName;
                                    sentMsg(order);
                                    loadingView.setVisibility(View.INVISIBLE);
                                }

                            }
                        });

                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 结束以后，给服务器反一个消息
     *
     * @param order
     */
    private void sentMsg(String order) {
        DatagramSocket datagramSocket = MyApplication.getInstance().datagramSocket;
        if (datagramSocket == null) {
            Log.d(TAG, "sentMsg: datagramSocket == null  onDestroy");
            return;
        }
        try {
            byte[] bytes = order.getBytes();
            DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length,
                    InetAddress.getByName(SERVER_IP), PORT);

            datagramSocket.send(datagramPacket);

            Log.d(TAG, "sentMsg: order = " + order + " 发送给服务端了！");

        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 从 Uri 得到文件路径
     *
     * @param context
     * @param uri
     * @return
     */
    public static String getRealFilePath(final Context context, final Uri uri) {
        if (null == uri) return null;
        final String scheme = uri.getScheme();
        String data = null;
        if (scheme == null)
            data = uri.getPath();
        else if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            data = uri.getPath();
        } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.Images.ImageColumns.DATA}, null, null, null);
            if (null != cursor) {
                if (cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
                    if (index > -1) {
                        data = cursor.getString(index);
                    }
                }
                cursor.close();
            }
        }
        return data;
    }
}
