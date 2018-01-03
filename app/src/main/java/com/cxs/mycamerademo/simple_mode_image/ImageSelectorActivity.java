package com.cxs.mycamerademo.simple_mode_image;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import com.cxs.mycamerademo.ConvertUtils;
import com.cxs.mycamerademo.MyApplication;
import com.cxs.mycamerademo.R;
import com.cxs.mycamerademo.simple_mode_image.adapter.FolderAdapter;
import com.cxs.mycamerademo.simple_mode_image.adapter.ImageAdapter;
import com.cxs.mycamerademo.simple_mode_image.constant.Constants;
import com.cxs.mycamerademo.simple_mode_image.entry.Folder;
import com.cxs.mycamerademo.simple_mode_image.entry.Image;
import com.cxs.mycamerademo.simple_mode_image.model.ImageModel;
import com.cxs.mycamerademo.simple_mode_image.utils.DateUtils;
import com.cxs.mycamerademo.simple_mode_image.utils.ImageSelectorUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;

public class ImageSelectorActivity extends AppCompatActivity {
    private static final String TAG = "ImageSelectorActivity";
    private TextView tvTime;
    private TextView tvFolderName;
    private TextView tvConfirm;
    private TextView tvPreview;
    private FrameLayout btnConfirm;
    private FrameLayout btnPreview;
    private RecyclerView rvImage;
    private RecyclerView rvFolder;
    private View masking;
    private ImageView btn_back;

    private ImageAdapter mAdapter;
    private GridLayoutManager mLayoutManager;

    private ArrayList<Folder> mFolders;
    private Folder mFolder;
    private boolean isToSettings = false;
    private static final int PERMISSION_REQUEST_CODE = 0X00000011;

    private boolean isOpenFolder;
    private boolean isShowTime;
    private boolean isInitFolder;
    private boolean isSingle;
    private int mMaxCount;

    private Handler mHideHandler = new Handler();
    private Runnable mHide = new Runnable() {
        @Override
        public void run() {
            hideTime();
        }
    };

    //简易模式，点击图片就选择（单选）
    private boolean isSimpleMode = false;

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
    private ImageView showImg;
    /**
     * 启动图片选择器
     *
     * @param activity
     * @param requestCode
     * @param isSingle       是否单选
     * @param maxSelectCount 图片的最大选择数量，小于等于0时，不限数量，isSingle为false时才有用。
     */
    public static void openActivity(Activity activity, int requestCode,
                                    boolean isSingle, int maxSelectCount) {
        Intent intent = new Intent(activity, ImageSelectorActivity.class);
        intent.putExtra(Constants.MAX_SELECT_COUNT, maxSelectCount);
        intent.putExtra(Constants.IS_SINGLE, isSingle);
        activity.startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_select);

        showImg = findViewById(R.id.showImg);
        Intent intent = getIntent();
        mMaxCount = intent.getIntExtra(Constants.MAX_SELECT_COUNT, 0);
        isSingle = intent.getBooleanExtra(Constants.IS_SINGLE, false);
        SERVER_IP = intent.getStringExtra("SERVER_IP");
        Toast.makeText(this, "接收到IP：" + SERVER_IP, Toast.LENGTH_SHORT).show();
        Log.d(TAG, "onCreate: 接收到SERVER_IP = " + SERVER_IP);


        //判断是不是简易模式
        isSimpleMode = intent.getBooleanExtra(Constants.IsSimpleMode, false);

        setStatusBarColor();
        initView();
        initListener();
        initImageList();
        checkPermissionAndLoadImages();
        hideFolderList();
        setSelectImageCount(0);

        initSocket();

        initMetrics();
    }

    private void initMetrics() {
        metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenHeight = metrics.heightPixels;
        mScreenWidth = metrics.widthPixels;
    }

    private void initSocket() {
        if (datagramSocket == null) {
            datagramSocket = MyApplication.getInstance().datagramSocket;
        }

        SendImgThread sendImgThread = new SendImgThread();
        sendImgThread.start();
        Log.d(TAG, " SendImgThread 开启!");

    }

    /**
     * 修改状态栏颜色
     */
    private void setStatusBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.parseColor("#373c3d"));
        }
    }

    private void initView() {
        rvImage = (RecyclerView) findViewById(R.id.rv_image);
        rvFolder = (RecyclerView) findViewById(R.id.rv_folder);
        tvConfirm = (TextView) findViewById(R.id.tv_confirm);
        tvPreview = (TextView) findViewById(R.id.tv_preview);
        btnConfirm = (FrameLayout) findViewById(R.id.btn_confirm);
        btnPreview = (FrameLayout) findViewById(R.id.btn_preview);
        tvFolderName = (TextView) findViewById(R.id.tv_folder_name);
        tvTime = (TextView) findViewById(R.id.tv_time);
        masking = findViewById(R.id.masking);
        btn_back=findViewById(R.id.btn_back);

        initSimpleMode();//处理简易模式
    }

    private void initSimpleMode() {
        if (isSimpleMode) {
            btn_back.setVisibility(View.GONE);
            tvConfirm.setVisibility(View.GONE);
            tvPreview.setVisibility(View.GONE);
        }else {
            btn_back.setVisibility(View.VISIBLE);
            tvConfirm.setVisibility(View.VISIBLE);
            tvPreview.setVisibility(View.VISIBLE);
        }
    }

    private void initListener() {

        btn_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });


        btnPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<Image> images = new ArrayList<>();
                images.addAll(mAdapter.getSelectImages());
                toPreviewActivity(images, 0);
            }
        });

        btnConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirm();
            }
        });

        findViewById(R.id.btn_folder).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isInitFolder) {
                    if (isOpenFolder) {
                        closeFolder();
                    } else {
                        openFolder();
                    }
                }
            }
        });

        masking.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeFolder();
            }
        });

        rvImage.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                changeTime();
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                changeTime();
            }
        });
    }

    /**
     * 初始化图片列表
     */
    private void initImageList() {
        // 判断屏幕方向
        Configuration configuration = getResources().getConfiguration();
        if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            mLayoutManager = new GridLayoutManager(this, 3);
        } else {
            mLayoutManager = new GridLayoutManager(this, 5);
        }

        rvImage.setLayoutManager(mLayoutManager);
        mAdapter = new ImageAdapter(this, mMaxCount, isSingle);
        if (isSimpleMode) {
            mAdapter.isSimpleMode(isSimpleMode);
        } else {
            mAdapter.isSimpleMode(false);
        }
        rvImage.setAdapter(mAdapter);
        ((SimpleItemAnimator) rvImage.getItemAnimator()).setSupportsChangeAnimations(false);
        if (mFolders != null && !mFolders.isEmpty()) {
            setFolder(mFolders.get(0));
        }
        mAdapter.setOnImageSelectListener(new ImageAdapter.OnImageSelectListener() {
            @Override
            public void OnImageSelect(Image image, boolean isSelect, int selectCount) {
                setSelectImageCount(selectCount);
            }
        });
        mAdapter.setOnItemClickListener(new ImageAdapter.OnItemClickListener() {
            @Override
            public void OnItemClick(Image image, int position) {
                if (isSimpleMode) {
                    Toast.makeText(getBaseContext(), image.getPath(), Toast.LENGTH_SHORT).show();

                    Bitmap bitmap = ConvertUtils.getBitmap(image.getPath());
                    Log.d(TAG, "OnItemClick: " + bitmap.getByteCount());

                    int[] scaleSize = ConvertUtils.getScaleWidthHeight(bitmap.getWidth(), bitmap.getHeight(),
                            mScreenWidth, mScreenHeight);
                    //等比缩放
                    bitmap = ConvertUtils.ratio(bitmap, scaleSize[0], scaleSize[1]);
                    showImg.setImageBitmap(bitmap);

                    bitmapBytes = ConvertUtils.bitmap2Bytes(bitmap, Bitmap.CompressFormat.JPEG);

                    Log.d(TAG, "最终发送：bitmapBytes.length" + bitmapBytes.length+
                            "\nbitmap.getWidth()"+bitmap.getWidth()+
                            "\nbitmap.getHeight()"+bitmap.getHeight());

                }else {
                    toPreviewActivity(mAdapter.getData(), position);
                }

            }
        });
    }

    /**
     * 初始化图片文件夹列表
     */
    private void initFolderList() {
        if (mFolders != null && !mFolders.isEmpty()) {
            isInitFolder = true;
            rvFolder.setLayoutManager(new LinearLayoutManager(ImageSelectorActivity.this));
            FolderAdapter adapter = new FolderAdapter(ImageSelectorActivity.this, mFolders);
            adapter.setOnFolderSelectListener(new FolderAdapter.OnFolderSelectListener() {
                @Override
                public void OnFolderSelect(Folder folder) {
                    setFolder(folder);
                    closeFolder();
                }
            });
            rvFolder.setAdapter(adapter);
        }
    }

    /**
     * 刚开始的时候文件夹列表默认是隐藏的
     */
    private void hideFolderList() {
        rvFolder.post(new Runnable() {
            @Override
            public void run() {
                rvFolder.setTranslationY(rvFolder.getHeight());
                rvFolder.setVisibility(View.GONE);
            }
        });
    }

    /**
     * 设置选中的文件夹，同时刷新图片列表
     *
     * @param folder
     */
    private void setFolder(Folder folder) {
        if (folder != null && mAdapter != null && !folder.equals(mFolder)) {
            mFolder = folder;
            tvFolderName.setText(folder.getName());
            rvImage.scrollToPosition(0);
            mAdapter.refresh(folder.getImages());
        }
    }

    private void setSelectImageCount(int count) {
        if (count == 0) {
            btnConfirm.setEnabled(false);
            btnPreview.setEnabled(false);
            tvConfirm.setText("确定");
            tvPreview.setText("预览");
        } else {
            btnConfirm.setEnabled(true);
            btnPreview.setEnabled(true);
            tvPreview.setText("预览(" + count + ")");
            if (isSingle) {
                tvConfirm.setText("确定");
            } else if (mMaxCount > 0) {
                tvConfirm.setText("确定(" + count + "/" + mMaxCount + ")");
            } else {
                tvConfirm.setText("确定(" + count + ")");
            }
        }
    }

    /**
     * 弹出文件夹列表
     */
    private void openFolder() {
        if (!isOpenFolder) {
            masking.setVisibility(View.VISIBLE);
            ObjectAnimator animator = ObjectAnimator.ofFloat(rvFolder, "translationY",
                    rvFolder.getHeight(), 0).setDuration(300);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    rvFolder.setVisibility(View.VISIBLE);
                }
            });
            animator.start();
            isOpenFolder = true;
        }
    }

    /**
     * 收起文件夹列表
     */
    private void closeFolder() {
        if (isOpenFolder) {
            masking.setVisibility(View.GONE);
            ObjectAnimator animator = ObjectAnimator.ofFloat(rvFolder, "translationY",
                    0, rvFolder.getHeight()).setDuration(300);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    rvFolder.setVisibility(View.GONE);
                }
            });
            animator.start();
            isOpenFolder = false;
        }
    }

    /**
     * 隐藏时间条
     */
    private void hideTime() {
        if (isShowTime) {
            ObjectAnimator.ofFloat(tvTime, "alpha", 1, 0).setDuration(300).start();
            isShowTime = false;
        }
    }

    /**
     * 显示时间条
     */
    private void showTime() {
        if (!isShowTime) {
            ObjectAnimator.ofFloat(tvTime, "alpha", 0, 1).setDuration(300).start();
            isShowTime = true;
        }
    }

    /**
     * 改变时间条显示的时间（显示图片列表中的第一个可见图片的时间）
     */
    private void changeTime() {
        int firstVisibleItem = getFirstVisibleItem();
        if (firstVisibleItem >= 0 && firstVisibleItem < mAdapter.getData().size()) {
            Image image = mAdapter.getData().get(firstVisibleItem);
            String time = DateUtils.getImageTime(image.getTime() * 1000);
            tvTime.setText(time);
            showTime();
            mHideHandler.removeCallbacks(mHide);
            mHideHandler.postDelayed(mHide, 1500);
        }
    }

    private int getFirstVisibleItem() {
        return mLayoutManager.findFirstVisibleItemPosition();
    }

    private void confirm() {
        if (mAdapter == null) {
            return;
        }
        //因为图片的实体类是Image，而我们返回的是String数组，所以要进行转换。
        ArrayList<Image> selectImages = mAdapter.getSelectImages();
        ArrayList<String> images = new ArrayList<>();
        for (Image image : selectImages) {
            images.add(image.getPath());
        }

        //点击确定，把选中的图片通过Intent传给上一个Activity。
        Intent intent = new Intent();
        intent.putStringArrayListExtra(ImageSelectorUtils.SELECT_RESULT, images);
        setResult(RESULT_OK, intent);

        if (!isSimpleMode) {
            //如果不是简易模式，就finish
            finish();
        }

    }

    private void toPreviewActivity(ArrayList<Image> images, int position) {
        if (images != null && !images.isEmpty()) {
            PreviewActivity.openActivity(this, images,
                    mAdapter.getSelectImages(), isSingle, mMaxCount, position);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isToSettings) {
            isToSettings = false;
            checkPermissionAndLoadImages();
        }
    }

    /**
     * 处理图片预览页返回的结果
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.RESULT_CODE) {

            if (data != null && data.getBooleanExtra(Constants.IS_CONFIRM, false)) {
                //如果用户在预览页点击了确定，就直接把用户选中的图片返回给用户。
                confirm();
            } else {
                //否则，就刷新当前页面。
                mAdapter.notifyDataSetChanged();
                setSelectImageCount(mAdapter.getSelectImages().size());
            }
        }
    }

    /**
     * 横竖屏切换处理
     *
     * @param newConfig
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mLayoutManager != null && mAdapter != null) {
            //切换为竖屏
            if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
                mLayoutManager.setSpanCount(3);
            }
            //切换为横屏
            else if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mLayoutManager.setSpanCount(5);
            }
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * 检查权限并加载SD卡里的图片。
     */
    private void checkPermissionAndLoadImages() {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
//            Toast.makeText(this, "没有图片", Toast.LENGTH_LONG).show();
            return;
        }
        int hasWriteContactsPermission = ContextCompat.checkSelfPermission(getApplication(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (hasWriteContactsPermission == PackageManager.PERMISSION_GRANTED) {
            //有权限，加载图片。
            loadImageForSDCard();
        } else {
            //没有权限，申请权限。
            ActivityCompat.requestPermissions(ImageSelectorActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * 处理权限申请的回调。
     *
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //允许权限，加载图片。
                loadImageForSDCard();
            } else {
                //拒绝权限，弹出提示框。
                showExceptionDialog();
            }
        }
    }

    /**
     * 发生没有权限等异常时，显示一个提示dialog.
     */
    private void showExceptionDialog() {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle("提示")
                .setMessage("该相册需要赋予访问存储的权限，请到“设置”>“应用”>“权限”中配置权限。")
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        finish();
                    }
                }).setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                startAppSettings();
                isToSettings = true;
            }
        }).show();
    }

    /**
     * 从SDCard加载图片。---------> 重要方法
     */
    private void loadImageForSDCard() {
        ImageModel.loadImageForSDCard(this, new ImageModel.DataCallback() {
            @Override
            public void onSuccess(ArrayList<Folder> folders) {
                mFolders = folders;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mFolders != null && !mFolders.isEmpty()) {
                            initFolderList();
                            setFolder(mFolders.get(0));
                        }
                    }
                });
            }
        });
    }

    /**
     * 启动应用的设置
     */
    private void startAppSettings() {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN && isOpenFolder) {
            closeFolder();
            return true;
        }
        return super.onKeyDown(keyCode, event);
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

    @Override
    protected void onDestroy() {
        isFinish = true;
        sentMsg(IMG_END_ORDER);
        Log.d(TAG, "onDestroy: ");
        super.onDestroy();
    }
}
