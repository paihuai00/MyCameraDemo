package com.cxs.mycamerademo.camera;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;


import com.cxs.mycamerademo.R;
import com.cxs.mycamerademo.simple_mode_image.utils.ImageSelectorUtils;

import java.util.ArrayList;

/**
 * @author cuishuxiang
 * @date 2017/12/7.
 */

public class ImmediateSendActivity extends AppCompatActivity {
    private static final String TAG = "ImmediateSendActivity";
    private static final int REQUEST_CODE = 0x00000011;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_immediate_send);

        ImageSelectorUtils.openPhoto(this, REQUEST_CODE,
                true, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE && data != null) {
            ArrayList<String> images = data.getStringArrayListExtra(ImageSelectorUtils.SELECT_RESULT);

        }
    }
}
