package com.cxs.mycamerademo.screen_capture;

import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.cxs.mycamerademo.R;

import java.nio.ByteBuffer;

/**
 * @author cuishuxiang
 * @date 2017/11/23.
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
public class ScreenActivity extends AppCompatActivity {
    private static final String TAG = "ScreenActivity";
    private static final int SCREEN_SHOT = 0;

    MediaProjection mediaProjection;
    MediaProjectionManager projectionManager;
    VirtualDisplay virtualDisplay;
    int mResultCode;
    Intent mData;
    ImageReader imageReader;

    int width;
    int height;
    int dpi;

    String imageName;
    Bitmap bitmap;
    ImageView imageView;
    Button start_btn;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen);

        initScreen();
    }

    private void initScreen() {
        DisplayMetrics metric = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metric);
        width = metric.widthPixels;
        height = metric.heightPixels;
        dpi = metric.densityDpi;
        Log.d(TAG, "initScreen: metric.widthPixels : " + metric.widthPixels + "\nmetric.heightPixels : " + metric.heightPixels
                + "\nmetric.densityDpi:" + metric.densityDpi);

        imageView = (ImageView) findViewById(R.id.imageView);
        start_btn = findViewById(R.id.start_btn);
        start_btn.setOnClickListener(new View.OnClickListener() {
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onClick(View v) {
                if (mediaProjection == null) {
                    setUpMediaProjection();
                } else if (virtualDisplay == null) {
                    setUpMediaProjection();
                    setUpVirtualDisplay();
                }
                startScreenShot();
            }
        });

        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == SCREEN_SHOT){
            if(resultCode == RESULT_OK){
                mResultCode = resultCode;
                mData = data;
                setUpMediaProjection();
                setUpVirtualDisplay();
                startCapture();

            }
        }
    }

    private void startCapture() {
        imageName = System.currentTimeMillis() + ".png";
        Image image = imageReader.acquireNextImage();
        if (image == null) {
            Log.e(TAG, "image is null.");
            return;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        imageReader.close();//关闭imageReader只截屏1次
//        image.close();  关闭这个，就一直重复截屏


        if (bitmap != null) {
            Log.d(TAG, "startCapture: " + bitmap.getByteCount() + "\nbitmap.getHeight() : " + bitmap.getHeight() + "\nbitmap.getWidth() : " + bitmap.getWidth());
            imageView.setImageBitmap(bitmap);
        }

    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setUpVirtualDisplay(){
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1);
        mediaProjection.createVirtualDisplay("ScreenShout",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                null);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setUpMediaProjection(){
        mediaProjection = projectionManager.getMediaProjection(mResultCode,mData);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void startScreenShot() {
        startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_SHOT);
    }

}
