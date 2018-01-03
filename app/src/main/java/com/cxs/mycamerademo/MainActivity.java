package com.cxs.mycamerademo;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;


import com.cxs.mycamerademo.camera.ImageActivity;
import com.cxs.mycamerademo.camera.ImageSendActivity;
import com.cxs.mycamerademo.camera.ImmediateSendActivity;
import com.cxs.mycamerademo.post_file.PostFileActivity;
import com.cxs.mycamerademo.record.RecordActivity;
import com.cxs.mycamerademo.record.RecordActivity640_480;
import com.cxs.mycamerademo.simple_mode_image.ImageSelectorActivity;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * @author cuishuxiang
 * @date 2017/11/28.
 */

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    @BindView(R.id.image_btn)
    Button imageBtn;
    @BindView(R.id.record_btn)
    Button recordBtn;
    @BindView(R.id.record640_480_btn)
    Button record640480Btn;
    @BindView(R.id.post_file_btn)
    Button postFileBtn;
    @BindView(R.id.spinner)
    Spinner spinnerPic;
    @BindView(R.id.immediate_image_btn)
    Button immediateImageBtn;

    /**
     * socket 相关
     */
    private String SERVER_IP;//服务器ip
    private final int PORT = 8080;
    private DatagramSocket datagramSocket;
    private DatagramPacket datagramPacket;
    private ReceiveServerThread receiveServerThread;

    private String VIDEO_START_ORDER = "200003";//视频开启
    private String IMG_START_ORDER = "200005";//图片开启

    private int MODE = 0;//0 ：为正常预览模式；  1：简易直接发送

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        datagramSocket = MyApplication.getInstance().datagramSocket;
        //开启接收广播的线程,接收ip
        receiveServerThread = new ReceiveServerThread();
        receiveServerThread.start();


        initSpinner();
    }

    private void initSpinner() {
        List<String> stringList = new ArrayList<>();
        stringList.add("模式一：拍照预览 !");
        stringList.add("模式二：直接发送 !");
        ArrayAdapter<String> stringArrayAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, stringList);

        spinnerPic.setAdapter(stringArrayAdapter);

        spinnerPic.setSelection(0);

        spinnerPic.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Toast.makeText(MainActivity.this, "点击了！" + position, Toast.LENGTH_SHORT).show();
                if (1 == position) {
                    MODE = 1;
                    Toast.makeText(MainActivity.this, "Mode:直接发送", Toast.LENGTH_SHORT).show();
                } else if (0 == position) {
                    MODE = 0;
                    Toast.makeText(MainActivity.this, "Mode:预览模式", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

    }

    @OnClick({R.id.image_btn,
            R.id.record_btn,
            R.id.record640_480_btn,
            R.id.post_file_btn,
            R.id.immediate_image_btn})
    public void onViewClicked(View view) {
        if (view.getId() != R.id.post_file_btn && TextUtils.isEmpty(SERVER_IP)
                && R.id.immediate_image_btn != view.getId()) {
            Toast.makeText(this, "没有接收到服务器的IP", Toast.LENGTH_SHORT).show();
            return;
        }
        switch (view.getId()) {
            case R.id.image_btn:
                sentMsg(IMG_START_ORDER);
                Intent intentImage = null;
                if (0 == MODE) {
                    intentImage = new Intent(MainActivity.this, ImageSendActivity.class);
                } else if (1 == MODE) {
                    intentImage = new Intent(MainActivity.this, ImageSelectorActivity.class);
                    intentImage.putExtra("isSimpleMode", true);
                }
                intentImage.putExtra("SERVER_IP", SERVER_IP);
                startActivity(intentImage);

                break;
            case R.id.record_btn:

                sentMsg(VIDEO_START_ORDER);

                Intent intentRecord = new Intent(MainActivity.this, RecordActivity.class);
                intentRecord.putExtra("SERVER_IP", SERVER_IP);
                startActivity(intentRecord);
                break;
            case R.id.record640_480_btn:

                sentMsg(VIDEO_START_ORDER);

                Intent intentRecord640 = new Intent(MainActivity.this, RecordActivity640_480.class);
                intentRecord640.putExtra("SERVER_IP", SERVER_IP);
                startActivity(intentRecord640);
                break;

            case R.id.post_file_btn:
                Intent fileIntent = new Intent(MainActivity.this, PostFileActivity.class);
                fileIntent.putExtra("SERVER_IP", SERVER_IP);
                startActivity(fileIntent);
                break;

            case R.id.immediate_image_btn:
//                sentMsg(IMG_START_ORDER);
//                Intent immediateIntent = new Intent(MainActivity.this,
//                        ImageSelectorActivity.class);
//                immediateIntent.putExtra("isSimpleMode", true);
//                immediateIntent.putExtra("SERVER_IP", SERVER_IP);
//                startActivity(immediateIntent);
                break;
            default:
                break;
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
                        SERVER_IP = receiveMsg.substring(22);

                        Log.d(TAG, "run: 截取的Ip：" + receiveMsg.substring(22));
                        sentMsg("hengfengxinxi#beifen2");// 发给服务器一个消息
                    }
                    Log.d(TAG, "run: 执行完毕 -- >广播执行线程");
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 接收到广播后，根据视频/图片
     * 给服务器反一个消息
     *
     * @param order
     */
    private void sentMsg(String order) {
        try {
            byte[] bytes = order.getBytes();

            DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length,
                    InetAddress.getByName(SERVER_IP), PORT);

            datagramSocket.send(datagramPacket);

            Log.d(TAG, "sentMsg: order : " + order + " 发送给服务端了！");

        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
