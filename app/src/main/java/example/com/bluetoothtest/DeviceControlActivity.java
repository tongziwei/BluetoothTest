package example.com.bluetoothtest;

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
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import example.com.bluetoothtest.bluetooth.BluetoothLeService;
import example.com.bluetoothtest.bluetooth.SampleGattAttributes;
import example.com.bluetoothtest.view.SendDataDialog;

public class DeviceControlActivity extends AppCompatActivity {
    private static final String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRA_DEVICE_NAME =  "Device name";
    public static final String EXTRA_DEVICE_ADDRESS = "Device address";

    private String mDeviceName;
    private String mDeviceAddress;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private Toolbar connectToolbar;
    private TextView deviceNameText;
    private TextView deviceAddressText;
    private TextView connectStateText;
    private TextView dataText;
    private ExpandableListView gattServiceList;

    private  BluetoothLeService.LocalBinder mLocalBinder;
    private  BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private boolean mConnected = false;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder)iBinder).getService();
            if(!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            mBluetoothLeService.connect(mDeviceAddress);
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
            if(BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)){
                mConnected = true;
                updateConnectState(R.string.Gatt_connect);
                invalidateOptionsMenu();
            }else if(BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)){
                mConnected = false;
                updateConnectState(R.string.Gatt_disconnect);
                invalidateOptionsMenu();
                clearUI();
            }else if(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)){
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            }else if(BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)){
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }else if(BluetoothLeService.ACTION_SEND_DATA_SUCCESS.equals(action)){
                Toast.makeText(DeviceControlActivity.this,"数据发送成功",Toast.LENGTH_SHORT).show();
            }
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_control);
        connectToolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(connectToolbar);
        deviceNameText = (TextView)findViewById(R.id.device_name);
        deviceAddressText = (TextView)findViewById(R.id.device_address);
        connectStateText = (TextView)findViewById(R.id.connect_state);
        dataText = (TextView)findViewById(R.id.data_textView);
        gattServiceList = (ExpandableListView)findViewById(R.id.gatt_services_list);

        Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRA_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS);

        deviceNameText.setText(mDeviceName);
        deviceAddressText.setText(mDeviceAddress);

     //   getActionBar().setTitle(mDeviceName);
     //   getActionBar().setDisplayHomeAsUpEnabled(true);

        Intent bindIntent = new Intent(this,BluetoothLeService.class);
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
        if(mBluetoothLeService != null){
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG,"Connect request result=" + result);
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_connect,menu);
        if(mConnected){
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        }else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectState(final int resourceId){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                    connectStateText.setText(resourceId);
            }
        });
    }

    private void clearUI(){
        gattServiceList.setAdapter((SimpleExpandableListAdapter)null);
        dataText.setText(R.string.no_data);
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        final ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        final ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }
        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        gattServiceList.setAdapter(gattServiceAdapter);

    /*    gattServiceList.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
                String serviceUUID = gattServiceData.get(groupPosition).get(LIST_UUID);
                Log.d("tzw", "serviceUUID: "+serviceUUID);
                String characteristicUUID = gattCharacteristicData.get(groupPosition).get(childPosition).get(LIST_UUID);
                Log.d("tzw", "characteristicUUID: "+characteristicUUID);
                showSendDataDialog(serviceUUID,characteristicUUID);
                return true;
            }
        });*/
        gattServiceList.setOnChildClickListener(servicesListClickListner);



    }

    private void displayData(String data){
        if(data != null){
            dataText.setText(data);
        }
    }

    private static IntentFilter makeUpdateIntentFilter(){
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        return intentFilter;
    }

    private void showSendDataDialog(final String serviceUUID, final String characteristicUUID){
        SendDataDialog sendDataDialog = new SendDataDialog(DeviceControlActivity.this,serviceUUID,characteristicUUID);
        sendDataDialog.setOnSendDataDialogBtnClickListener(new SendDataDialog.OnSendDialogBtnClickListener() {
            @Override
            public void onDialogBtnSend(View view, String data) {
                UUID sUUID = UUID.fromString(serviceUUID);
                UUID cUUID = UUID.fromString(characteristicUUID);
                mBluetoothLeService.writeCharacteristic(sUUID,cUUID,data);
            }
        });
        sendDataDialog.show();
    }

    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
            };

}
