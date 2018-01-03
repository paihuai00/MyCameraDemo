package com.cxs.mycamerademo.camera;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import com.cxs.mycamerademo.ConvertUtils;
import com.cxs.mycamerademo.MyApplication;
import com.cxs.mycamerademo.R;
import com.google.android.cameraview.AspectRatio;
import com.google.android.cameraview.CameraView;
import com.zhihu.matisse.Matisse;
import com.zhihu.matisse.MimeType;
import com.zhihu.matisse.engine.impl.GlideEngine;
import com.zhihu.matisse.internal.entity.CaptureStrategy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;


/**
 * 发送一张图片，直接发送 到服务端
 */
public class ImageSendActivity extends AppCompatActivity {
    private static final String TAG = "ImageActivity";

    private static final int REQUEST_CAMERA_PERMISSION = 1;

    //图片选择器，请求吗
    private static final int CHOOSE_IMG_REQUEST = 0;
    //接收，选择图片返回的uri
    List<Uri> chooseImgs = new ArrayList<>();

    @BindView(R.id.img_recyclerview)
    RecyclerView imgRecyclerview;

    @BindView(R.id.album_btm)
    Button albumBtm;

    private ImageRVAdadapter adadapter;
    private ArrayList<Bitmap> bitmapArrayList;

    private Handler mBackgroundHandler;

    private String SERVER_IP;//服务器ip
    private final int PORT = 8080;

    private DatagramSocket datagramSocket;
    private byte[] bitmapBytes;

    private final String IMG_END_ORDER = "200006";

    private boolean isFinish = false;

    private DisplayMetrics metrics;
    private int mScreenWidth;
    private int mScreenHeight;
    private int dpi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_send);
        ButterKnife.bind(this);
        SERVER_IP = getIntent().getStringExtra("SERVER_IP");
        Log.d(TAG, "onCreate: 接收到SERVER_IP = " + SERVER_IP);
        initView();

        initSocket();

        metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenWidth = metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;
        dpi = metrics.densityDpi;
        Log.d(TAG, "onCreate: dpi" + dpi);
    }

    private void initSocket() {
        if (datagramSocket == null) {
            datagramSocket = MyApplication.getInstance().datagramSocket;
        }

        SendImgThread sendImgThread = new SendImgThread();
        sendImgThread.start();
        Log.d(TAG, " SendImgThread 开启!");

    }

    private void initView() {

        /**
         * 初始化下面的展示列表
         */
        bitmapArrayList = new ArrayList<>();
        adadapter = new ImageRVAdadapter(this, bitmapArrayList);
        adadapter.setMyRecycleItemClick(new ImageRVAdadapter.MyRecycleItemClick() {
            @Override
            public void mItemClick(View view, int position) {
                showAlertDialog(position);
            }
        });

        //设置横向
        LinearLayoutManager hoLinearLayoutManager = new LinearLayoutManager(this);
        hoLinearLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);

        imgRecyclerview.setLayoutManager(hoLinearLayoutManager);

        imgRecyclerview.setAdapter(adadapter);
    }


    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: ");
        isFinish = true;
        //销毁的时候，发送结束命令
        sentMsg(IMG_END_ORDER);
        super.onDestroy();

    }

    private void showAlertDialog(final int position) {
        new AlertDialog.Builder(this)
                .setCancelable(false)

                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(ImageSendActivity.this, "点击了取消" + position, Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        bitmapBytes = ConvertUtils.bitmap2Bytes(bitmapArrayList.get(position), Bitmap.CompressFormat.JPEG);
                        Log.d(TAG, "onClick: 点击了确定,发送：bitmapBytes.length" + bitmapBytes.length);
                        dialog.dismiss();
                    }
                })
                .create().show();
    }

    @OnClick({R.id.album_btm})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.album_btm:
                Matisse.from(ImageSendActivity.this)
                        .choose(MimeType.of(MimeType.PNG, MimeType.JPEG))
                        .countable(true)
                        .maxSelectable(1)//最多选择1张
                        .gridExpectedSize(getResources().getDimensionPixelSize(R.dimen.grid_expected_size))//图片显示表格的大小getResources()
                        .capture(true)
                        .showSingleMediaType(true)
                        .captureStrategy(new CaptureStrategy(true, "com.cxs.mycamerademo.fileprovider"))
                        .thumbnailScale(0.8f)//缩放比例
                        .restrictOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
                        .imageEngine(new GlideEngine())
                        .forResult(CHOOSE_IMG_REQUEST);
//                Matisse.from(this)
//                        .choose(MimeType.ofImage())
//                        .showSingleMediaType(true)
//                        .maxSelectable(1)
//                        .imageEngine(new GlideEngine())
//                        .forResult(CHOOSE_IMG_REQUEST);

                break;
        }
    }

    /**
     * 结束以后，给服务器反一个消息
     *
     * @param order
     */
    private void sentMsg(String order) {
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
     * 发送图片的线程
     */
    class SendImgThread extends Thread {
        @Override
        public void run() {

            while (!isFinish) {
//                Log.d(TAG, "run: 发送图片线程");
                if (bitmapBytes == null) {
                    //只有每次 不为null的时候，才执行发送操作
                    //发送完毕之后，置为null
                    continue;
                }

                try {
                    //定义每次发送的大小
                    byte[] bytes = new byte[1024 * 8];

                    //计算需要循环几次，能把当前的图片发送完(以每次发送 1024 * 8大小发送)
                    int times = (bitmapBytes.length / bytes.length) + 1;

                    for (int i = 0; i < times; i++) {
                        int start = i * bytes.length;
                        int end = (i + 1) * bytes.length > bitmapBytes.length ? bitmapBytes.length : (i + 1) * bytes.length;

                        //将图片拆分，为 1024*8 的大小分别发送
                        byte[] sendBytes = Arrays.copyOfRange(bitmapBytes, start, end);

                        Log.d(TAG, "bitmapBytes.length=" + bitmapBytes.length +
                                "\n循环次数" + times +
                                "\n每次发送: start " + start +
                                "\nend : " + end +
                                "\n每次发送的大小：sendBytes" + sendBytes.length);
                        DatagramPacket datagramPacket = new DatagramPacket(sendBytes, sendBytes.length,
                                InetAddress.getByName(SERVER_IP), PORT);

                        //发送
                        datagramSocket.send(datagramPacket);
                    }

                    if (bitmapBytes != null) {
                        bitmapBytes = null;
                    }

                    Log.d(TAG, "run: 图片发送完成");
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //选择图片的返回码
        if (requestCode == CHOOSE_IMG_REQUEST && resultCode == RESULT_OK) {
            chooseImgs = Matisse.obtainResult(data);
            Log.d(TAG, "onActivityResult: 选择图片chooseImgs.size()=" + chooseImgs.size());

            long startTime = System.currentTimeMillis();
            Bitmap bitmap = getBitmapFromUri(chooseImgs.get(0));
            Log.d(TAG, "onActivityResult: 原图的 宽：" + bitmap.getWidth() + " 高：" + bitmap.getHeight());

            int[] scaleSize = ConvertUtils.getScaleWidthHeight(bitmap.getWidth(), bitmap.getHeight(),
                    mScreenWidth, mScreenHeight);
            //等比缩放
            bitmap = ConvertUtils.ratio(bitmap, scaleSize[0], scaleSize[1]);
            Log.d(TAG, "onActivityResult:处理后的 width:" + bitmap.getWidth() +
                    "\nheight : " + bitmap.getHeight()+
                    "\nscaleSize[0]"+scaleSize[0]+
                    "\nscaleSize[1]"+scaleSize[1]);

            bitmapBytes = ConvertUtils.bitmap2Bytes(bitmap, Bitmap.CompressFormat.JPEG);

            Log.d(TAG, "onActivityResult 发送：bitmapBytes.length" + bitmapBytes.length+
                    "\nbitmap.getWidth()"+bitmap.getWidth()+
                    "\nbitmap.getHeight()"+bitmap.getHeight());

            bitmapArrayList.add(0, bitmap);
            adadapter.notifyDataSetChanged();

            long endTime = System.currentTimeMillis();
            Log.d(TAG, "onActivityResult: 耗时:" + (endTime - startTime));

        }
    }

    /**
     * uri  ---->  bitmap
     * @param uri
     * @return
     */
    private Bitmap getBitmapFromUri(Uri uri) {
        try {
            // 读取uri所在的图片
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
            return bitmap;
        } catch (Exception e) {
            Log.e("[Android]", e.getMessage());
            Log.e("[Android]", "目录为：" + uri);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * uri ---> filePath
     * @param uri
     * @return
     */
    private String uri2FilePath(Uri uri) {
        String img_path;
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor actualimagecursor = managedQuery(uri, proj, null,
                null, null);
        if (actualimagecursor == null) {
            img_path = uri.getPath();
        } else {
            int actual_image_column_index = actualimagecursor
                    .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            actualimagecursor.moveToFirst();
            img_path = actualimagecursor
                    .getString(actual_image_column_index);
        }
        File file = new File(img_path);
        Log.d(TAG, "lubanCompressImg: 原图片大小" + file.length());
        return img_path;
    }

}

