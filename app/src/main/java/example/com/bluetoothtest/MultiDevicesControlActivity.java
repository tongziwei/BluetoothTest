package example.com.bluetoothtest;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import example.com.bluetoothtest.adapter.SelectedLeDevicesAdapter;
import example.com.bluetoothtest.bean.SelectedBluetoothDevice;
import example.com.bluetoothtest.bluetooth.MultiBluetoothLeService;
import example.com.bluetoothtest.bluetooth.SampleGattAttributes;

public class MultiDevicesControlActivity extends AppCompatActivity {
    private static final String TAG = "MultiDevConAct";
    public static final String EXTRA_DEVICE =  "Device_list";
    private MultiBluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

    private ArrayList<BluetoothDevice> mSelectedDevices = new ArrayList<>();
    private ArrayList<SelectedBluetoothDevice> mSelectedBleDevices = new ArrayList<>();

    private RecyclerView mRvSelectedDev;
    private SelectedLeDevicesAdapter mSelectedLeDevicesAdapter;
    private String connectState ="";

    private EditText mEtSendData;
    private Button mBtnSend;



    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mBluetoothLeService = ((MultiBluetoothLeService.LocalBinder)iBinder).getService();
            if(!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            if(mSelectedBleDevices.size()>0){
                for(SelectedBluetoothDevice device :mSelectedBleDevices){
                    mBluetoothLeService.connect(device.getDeviceAddress());
                }

            }
           // mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver =  new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if(MultiBluetoothLeService.ACTION_GATT_CONNECTED.equals(action)){
                String deviceAddress = intent.getStringExtra(MultiBluetoothLeService.EXTRA_DEVICE);
                updateConnectState(deviceAddress,true);
            }else if(MultiBluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)){
                String deviceAddress= intent.getStringExtra(MultiBluetoothLeService.EXTRA_DEVICE);
                updateConnectState(deviceAddress,false);
               // clearUI();
            }else if(MultiBluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)){
                displayGattServices(mBluetoothLeService.getSupportedGattServices());

            }else if(MultiBluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)){
                displayData(intent.getStringExtra(MultiBluetoothLeService.EXTRA_DATA));

            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_devices_control);
        initView();

        Intent intent = getIntent();
        mSelectedDevices.clear();
        mSelectedDevices.addAll(intent.<BluetoothDevice>getParcelableArrayListExtra(EXTRA_DEVICE));
        for(BluetoothDevice device:mSelectedDevices){
            SelectedBluetoothDevice ble = new SelectedBluetoothDevice();
            ble.setConnected(false);
            ble.setDeviceAddress(device.getAddress());
            ble.setDeviceName(device.getName());
            mSelectedBleDevices.add(ble);
        }
        mSelectedLeDevicesAdapter.notifyDataSetChanged();
        Log.d(TAG, "mSelectedDevices size "+mSelectedDevices.size() + "第一个设备："+mSelectedDevices.get(0).getName());
        Intent bindIntent = new Intent(this,MultiBluetoothLeService.class);
        bindService(bindIntent,mServiceConnection,BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver,makeUpdateIntentFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    private void initView(){
        mRvSelectedDev = (RecyclerView)findViewById(R.id.rv_devices);
        mSelectedLeDevicesAdapter = new SelectedLeDevicesAdapter(mSelectedBleDevices);
        mRvSelectedDev.setLayoutManager(new LinearLayoutManager(this));
        mRvSelectedDev.setAdapter(mSelectedLeDevicesAdapter);

        mSelectedLeDevicesAdapter.setOnDeviceConnectListener(new OnDeviceConnectListener() {
            @Override
            public void onConnectClick(View view, String deviceAddress, boolean connected) {
                if(connected){
                    mBluetoothLeService.disconnect(deviceAddress);
                }else{
                    mBluetoothLeService.connect(deviceAddress);
                }
            }
        });

        mEtSendData = (EditText)findViewById(R.id.et_send_data);
        mBtnSend = (Button)findViewById(R.id.btn_send);
        mBtnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mSelectedBleDevices.size()>0 &&  !mEtSendData.getText().toString().trim().isEmpty()){
                    mBluetoothLeService.writeData(UUID.fromString(SampleGattAttributes.BES_BLE_SERVICE),UUID.fromString(SampleGattAttributes.BES_BLE_WRITE_CHARACTERISTIC),mEtSendData.getText().toString().trim());
                }

            }
        });
    }

    private void updateConnectState(final String deviceAddress, final boolean connectStatus){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connectState = connectState + deviceAddress+" "+connectStatus;
                Log.d("tzw", connectState);
                mSelectedLeDevicesAdapter.updateConnectState(deviceAddress,connectStatus);
            }
        });
    }

    private void clearUI(){
       /* gattServiceList.setAdapter((SimpleExpandableListAdapter)null);
        dataText.setText(R.string.no_data);*/
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();



    }

    private void displayData(String data){
        if(data != null){
          //  dataText.setText(data);
        }
    }

    private static IntentFilter makeUpdateIntentFilter(){
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MultiBluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(MultiBluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(MultiBluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(MultiBluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        return intentFilter;
    }
}
