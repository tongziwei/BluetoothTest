package example.com.bluetoothtest;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;

import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import example.com.bluetoothtest.adapter.LeDeviceListAdapter;

public class DeviceScanActivity extends AppCompatActivity {
    private static final String TAG = "DeviceScanActivity";
    private static final int REQUEST_CODE_ACCESS_COARSE_LOCATION = 1;
    private Toolbar toolbar;
    private ListView devicesList;
    private LeDeviceListAdapter mDeviceListAdapter;
    private BluetoothAdapter mbluetoothAdapter;
    private Handler mHandler;
    private Button mBtnDevicesSelected;

    private  ArrayList<BluetoothDevice> mLeDevices = new ArrayList<BluetoothDevice>();
    private  ArrayList<BluetoothDevice> mSelectedLeDevices = new ArrayList<BluetoothDevice>();


    private boolean mScanning;

    private static final int REQUEST_ENABLE_BT = 1;
    // 10秒后停止查找搜索.
    private static final long SCAN_PERIOD = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_scan);
        toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        devicesList = (ListView)findViewById(R.id.bluetooth_device_list);
        mBtnDevicesSelected = (Button)findViewById(R.id.btn_devices_selected);
        mHandler = new Handler();

        initListener();
        initBluetooth();
        //请求位置权限
        requestLocationPermission();





    }

    private void initBluetooth(){
        // 检查当前手机是否支持ble 蓝牙,如果不支持退出程序
        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){
            Toast.makeText(this,R.string.ble_not_support,Toast.LENGTH_SHORT).show();
            finish();
        }

        // 初始化 Bluetooth adapter, 通过蓝牙管理器得到一个参考蓝牙适配器(API必须在以上android4.3或以上和版本)
        BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        mbluetoothAdapter = bluetoothManager.getAdapter();

        //检查设备上是否支持蓝牙
        if(mbluetoothAdapter == null){
            Toast.makeText(this,R.string.bluetoth_not_support,Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }

    private void initListener(){
        mBtnDevicesSelected.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mDeviceListAdapter != null) {
                    mSelectedLeDevices.clear();
                    if(mDeviceListAdapter.getSelectedDevices().size()>0){
                        mSelectedLeDevices.addAll(mDeviceListAdapter.getSelectedDevices()) ;
                        Intent intent = new Intent(DeviceScanActivity.this,MultiDevicesControlActivity.class);

                        intent.putParcelableArrayListExtra(MultiDevicesControlActivity.EXTRA_DEVICE,mSelectedLeDevices);
                        if(mScanning){
//                         mbluetoothAdapter.stopLeScan(mLeScanCallback);
//                          mScanning = false;
                            scanLeDevices(false);
                        }
                        startActivity(intent);
                    }

                   // Log.d(TAG, "mSelectedLeDevices "+mSelectedLeDevices.size());

                }
            }
        });

        devicesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG, "onItemClick: ");
                BluetoothDevice device = mDeviceListAdapter.getDevice(position).getBluetoothDevice();
                if(device == null) return;
                Intent intent = new Intent(DeviceScanActivity.this,DeviceControlActivity.class);
                intent.putExtra(DeviceControlActivity.EXTRA_DEVICE_NAME,device.getName());
                intent.putExtra(DeviceControlActivity.EXTRA_DEVICE_ADDRESS,device.getAddress());
                if(mScanning){
//                    mbluetoothAdapter.stopLeScan(mLeScanCallback);
//                    mScanning = false;
                    scanLeDevices(false);
                }
                startActivity(intent);
            }
        });


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_scan,menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.menu_scan:
                mDeviceListAdapter.clear();
                scanLeDevices(true);
                break;
            case R.id.menu_stop:
                scanLeDevices(false);
                break;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        //为了确保设备上蓝牙能使用, 如果当前蓝牙设备没启用,弹出对话框向用户要求授予权限来启用
     /*   if(!mbluetoothAdapter.isEnabled()){
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent,REQUEST_ENABLE_BT);
        }*/
    /*    mDeviceListAdapter = new LeDeviceListAdapter(DeviceScanActivity.this,R.layout.bluetooth_device_list_item,mLeDevices);
        devicesList.setAdapter(mDeviceListAdapter);
        scanLeDevices(true);*/
        mDeviceListAdapter = new LeDeviceListAdapter();
        devicesList.setAdapter(mDeviceListAdapter);
        scanLeDevices(true);
    }

    private void requestLocationPermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {//如果 API level 是大于等于 23(Android 6.0) 时
            //判断是否具有权限
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                //判断是否需要向用户解释为什么需要申请该权限
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    Toast.makeText(DeviceScanActivity.this,"自Android 6.0开始需要打开位置权限才可以搜索到Ble设备",Toast.LENGTH_SHORT).show();
                }
                //请求权限
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQUEST_CODE_ACCESS_COARSE_LOCATION);
            }else{
                 if(!mbluetoothAdapter.isEnabled()){
                        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(intent,REQUEST_ENABLE_BT);
                    }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,  String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_ACCESS_COARSE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //用户允许改权限，0表示允许，-1表示拒绝 PERMISSION_GRANTED = 0， PERMISSION_DENIED = -1
                //permission was granted, yay! Do the contacts-related task you need to do.
                //这里进行授权被允许的处理
                if(!mbluetoothAdapter.isEnabled()){
                    Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(intent,REQUEST_ENABLE_BT);
                }
            } else {
                //permission denied, boo! Disable the functionality that depends on this permission.
                //这里进行权限被拒绝的处理
                Toast.makeText(DeviceScanActivity.this,"你拒绝了位置权限，无法进行扫描",Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED){
            finish();
            return;
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevices(false);
        mDeviceListAdapter.clear();
    }

    private void scanLeDevices(final boolean enable){
       final BluetoothLeScanner scaner = mbluetoothAdapter.getBluetoothLeScanner();
        if(enable){
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                  //  mbluetoothAdapter.stopLeScan(mLeScanCallback);
                     // android5.0把扫描方法单独弄成一个对象了
                    scaner.stopScan(mScanCallback);   // 停止扫描
                    invalidateOptionsMenu();
                }
            },SCAN_PERIOD);

            mScanning = true;
            scaner.startScan(mScanCallback);  // 开始扫描
            //mbluetoothAdapter.startLeScan(mLeScanCallback);
        }else {
            mScanning = false;
            scaner.stopScan(mScanCallback);
           // mbluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

  /*  private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
//                    mDeviceListAdapter.addDevice(bluetoothDevice);
                    mLeDevices.add(bluetoothDevice);
                    mDeviceListAdapter.notifyDataSetChanged();
                }
            });

        }
    };*/
      private ScanCallback mScanCallback = new ScanCallback() {
          @Override
          public void onScanResult(int callbackType, final ScanResult result) {
              super.onScanResult(callbackType, result);
              // callbackType：确定这个回调是如何触发的
              // result：包括4.3版本的蓝牙信息，信号强度rssi，和广播数据scanRecord
              runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                      //mLeDevices.add(result.getDevice());
                      mDeviceListAdapter.addDevice(result.getDevice());
                    //  mDeviceListAdapter.notifyDataSetChanged();
                  }
              });

          }
          @Override
          public void onBatchScanResults(List<ScanResult> results) {
              super.onBatchScanResults(results);
              // 批量回调，一般不推荐使用，使用上面那个会更灵活
          }
          @Override
          public void onScanFailed(int errorCode) {
              super.onScanFailed(errorCode);
              // 扫描失败，并且失败原因
              Log.e(TAG, "onScanFailed: ");
          }
      };



}
