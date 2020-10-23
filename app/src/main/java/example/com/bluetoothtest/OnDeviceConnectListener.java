package example.com.bluetoothtest;

import android.view.View;

/**
 * Created by clara.tong on 2020/10/16
 */
public interface OnDeviceConnectListener {

    void onConnectClick(View view,String deviceAddress,boolean connected);
}
