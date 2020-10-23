package example.com.bluetoothtest.bean;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by clara.tong on 2020/10/16
 */
public class SelectedBluetoothDevice {
    private String deviceName;
    private String deviceAddress;
    private boolean isConnected;
    private String receivedData;

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceAddress() {
        return deviceAddress;
    }

    public void setDeviceAddress(String deviceAddress) {
        this.deviceAddress = deviceAddress;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }

    public String getReceivedData() {
        return receivedData;
    }

    public void setReceivedData(String receivedData) {
        this.receivedData = receivedData;
    }

}
