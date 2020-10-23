package example.com.bluetoothtest.bean;

import android.bluetooth.BluetoothDevice;

/**
 * Created by clara.tong on 2020/10/21
 */
public class BLEDevice {
    private BluetoothDevice bluetoothDevice;
    private boolean isChecked;

    public BluetoothDevice getBluetoothDevice() {
        return bluetoothDevice;
    }

    public void setBluetoothDevice(BluetoothDevice bluetoothDevice) {
        this.bluetoothDevice = bluetoothDevice;
    }

    public boolean isChecked() {
        return isChecked;
    }

    public void setChecked(boolean checked) {
        isChecked = checked;
    }
}
