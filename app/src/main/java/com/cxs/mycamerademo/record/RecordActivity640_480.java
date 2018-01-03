package com.cxs.mycamerademo.record;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.cxs.mycamerademo.ConvertUtils;
import com.cxs.mycamerademo.R;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * @author cuishuxiang
 * @date 2017/11/28.
 * 视频传输  640 * 480
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class RecordActivity640_480 extends AppCompatActivity {
    private static final String TAG = "RecordActivity";
    @BindView(R.id.texture)
    AutoFitTextureView mTextureView;
    @BindView(R.id.video_btn)
    Button mButtonVideo;

    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    @BindView(R.id.quality_seek_bar)
    SeekBar qualitySeekBar;
    @BindView(R.id.quality_txt)
    TextView qualityTxt;


    private CameraDevice mCameraDevice;

    private CameraCaptureSession mPreviewSession;

    private HandlerThread mBackgroundThread;

    private Handler mBackgroundHandler;

    private Integer mSensorOrientation;

    private Size mVideoSize;

    private MediaRecorder mMediaRecorder;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);


    private CaptureRequest.Builder mPreviewBuilder;

    private String mNextVideoAbsolutePath;

    private boolean mIsRecordingVideo;

    private Size mPreviewSize;

    /**
     * socket 相关
     */
    private String SERVER_IP;//服务器ip
    private final int PORT = 8080;

    private DatagramSocket datagramSocket;
    private byte[] bitmapBytes;

    private int picQuality = 20;//默认图片压缩为20 (0 - 100)

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record640_480);
        ButterKnife.bind(this);
        SERVER_IP = getIntent().getStringExtra("SERVER_IP");
        Log.d(TAG, "onCreate: 接收到SERVER_IP = " + SERVER_IP);

        initSocket();

        qualitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                qualityTxt.setText("图片质量：" + progress);
                picQuality = progress;
                Log.d(TAG, "onProgressChanged: " + progress);

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        qualityTxt.setText("图片质量：" + picQuality);

    }

    private void initSocket() {
        try {
            datagramSocket = new DatagramSocket(PORT);

            ReceiveServerThread receiveServerThread = new ReceiveServerThread();
            receiveServerThread.start();

        } catch (SocketException e) {
            e.printStackTrace();
        }
    }


    private TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                              int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable: width = " + width + "\nheight = " + height);
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

            Bitmap bitmap = mTextureView.getBitmap();

//            Log.d(TAG, "onSurfaceTextureUpdated: bitmap.getByteCount() = " +
//                    bitmap.getByteCount());

            bitmapBytes = ConvertUtils.bitmap2BytesWithQuality(bitmap, Bitmap.CompressFormat.JPEG, picQuality);

            Log.d(TAG, "onSurfaceTextureUpdated: bytes.length = " + bitmapBytes.length +
                    "\nbitmap.getWidth() = " + bitmap.getWidth() +
                    "\nbitmap.getHeight() = " + bitmap.getHeight());

//            show_img.setImageBitmap(bitmap);

        }
    };

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            startPreview();
            mCameraOpenCloseLock.release();
            if (null != mTextureView) {
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;

            finish();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void openCamera(int width, int height) {

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            Log.d(TAG, "tryAcquire");
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            String cameraId = manager.getCameraIdList()[0];

            // Choose the sizes for camera preview and video recording
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

            StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (map == null) {
                throw new RuntimeException("Cannot get available preview/video sizes");
            }
            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    width, height, mVideoSize);

            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            configureTransform(width, height);
            mMediaRecorder = new MediaRecorder();
            manager.openCamera(cameraId, mStateCallback, null);
        } catch (CameraAccessException e) {
            Toast.makeText(this, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
            finish();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Log.d(TAG, "openCamera: " + e.getMessage());
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.");
        }
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            closePreviewSession();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    private void startPreview() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            Surface previewSurface = new Surface(texture);
            mPreviewBuilder.addTarget(previewSurface);

            mCameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mPreviewSession = session;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(RecordActivity640_480.this, "Failed", Toast.LENGTH_SHORT).show();
                        }
                    }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(mPreviewBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(),
                    new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                            super.onCaptureStarted(session, request, timestamp, frameNumber);
                        }

                        @Override
                        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                                        @NonNull CaptureRequest request,
                                                        @NonNull CaptureResult partialResult) {
                            super.onCaptureProgressed(session, request, partialResult);

                        }

                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                       @NonNull CaptureRequest request,
                                                       @NonNull TotalCaptureResult result) {
                            super.onCaptureCompleted(session, request, result);

                        }
                    }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }


    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }


    private void setUpMediaRecorder() throws IOException {

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
            mNextVideoAbsolutePath = getVideoFilePath(this);
        }
        mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        mMediaRecorder.prepare();
    }


    private String getVideoFilePath(Context context) {
        final File dir = context.getExternalFilesDir(null);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                + System.currentTimeMillis() + ".mp4";
    }

    private void startRecordingVideo() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            setUpMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    updatePreview();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // UI
                            mButtonVideo.setText("停止录制");
                            mIsRecordingVideo = true;

                            // Start recording
                            mMediaRecorder.start();
                        }
                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(RecordActivity640_480.this, "Failed", Toast.LENGTH_SHORT).show();

                }
            }, mBackgroundHandler);
        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }

    }

    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    private void stopRecordingVideo() {
        // UI
        mIsRecordingVideo = false;
        mButtonVideo.setText("开始录制");
        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();

        Toast.makeText(this, "Video saved: " + mNextVideoAbsolutePath,
                Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Video saved: " + mNextVideoAbsolutePath);
        mNextVideoAbsolutePath = null;
        startPreview();
    }

    @OnClick({R.id.video_btn})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.video_btn:
                if (mIsRecordingVideo) {
                    stopRecordingVideo();
                } else {
                    startRecordingVideo();
                }
                break;
        }

    }


    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }


    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    /**
     * 发送图片的线程
     */
    class SendImgThread extends Thread {
        @Override
        public void run() {

            while (true) {
                Log.d(TAG, "run: 发送图片线程");
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

    class ReceiveServerThread extends Thread {
        @Override
        public void run() {
            Log.d(TAG, "run: 开始执行--> 广播监听线程");
            try {
                datagramSocket.setBroadcast(true);
                while (true) {
                    byte[] bytes = new byte[1024];
                    DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length);
                    //阻塞线程
                    datagramSocket.receive(datagramPacket);
                    Log.d(TAG, "run: 接收到了广播:datagramPacket.getLength() = "
                            + datagramPacket.getLength() + "\n" + datagramPacket.getData());

                    String receiveMsg = new String(datagramPacket.getData()).trim();
//                SERVER_IP = receiveMsg;
//                Log.d(TAG, "广播接收到的消息为：IP=" + SERVER_IP);
//                sentMsg(SERVER_IP);// 发给服务器一个消息
                    if (receiveMsg.contains("beifen1")) {
                        //此时接到的是 服务端下发的 Ip(广播IP指令：hengfengxinxi#beifen1:10.0.2.106)
                        //并且将  下面的指令发送回去： hengfengxinxi#beifen2
                        String stringIp = receiveMsg.substring(22);
                        SERVER_IP = receiveMsg.substring(22);

                        Log.d(TAG, "run: 截取的Ip：" + receiveMsg.substring(22));
                        sentMsg(stringIp);// 发给服务器一个消息
                    }

                    if ("200001".equals(receiveMsg)) {

                        SendImgThread sendImgThread = new SendImgThread();
                        sendImgThread.start();
                        Log.d(TAG, "run: SendImgThread 开启，发送图片!");

                    }

                    Log.d(TAG, "run: 执行完毕 -- >广播执行线程");
                }

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    /**
     * 接收到广播后，给服务器反一个消息
     *
     * @param serverIp
     */
    private void sentMsg(String serverIp) {
        try {
            byte[] bytes = "hengfengxinxi#beifen2".getBytes();
            DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length,
                    InetAddress.getByName(serverIp), PORT);

            datagramSocket.send(datagramPacket);

            Log.d(TAG, "sentMsg: hengfengxinxi#beifen2 发送给服务端了！");

        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
