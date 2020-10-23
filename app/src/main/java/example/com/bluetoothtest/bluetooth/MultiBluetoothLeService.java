package example.com.bluetoothtest.bluetooth;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MultiBluetoothLeService extends Service {
    private static final String TAG = MultiBluetoothLeService.class.getSimpleName();
    //创建基本线程池
    final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(3,5,1,TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(10));

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private ArrayList<BluetoothGatt> connectionQueue = new ArrayList<BluetoothGatt>();
    private String mBluetoothDeviceAddress;

    private int mConnectState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED           = "com.example.bluetooth.ble.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED        = "com.example.bluetooth.ble.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_RECONNECT       = "com.example.bluetooth.ble.ACTION_GATT_RECONNECT";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.example.bluetooth.ble.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE           = "com.example.bluetooth.ble.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA                      = "com.example.bluetooth.ble.EXTRA_DATA";
    public final static String EXTRA_DEVICE                      = "com.example.bluetooth.ble.EXTRA_DEVICE";
    public final static UUID UUID_HEART_RATE_MEASUREMENT       = UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);


    public MultiBluetoothLeService() {
    }


    public class LocalBinder extends Binder {
        public MultiBluetoothLeService getService(){
            return MultiBluetoothLeService.this;
        }
    }

    MultiBluetoothLeService.LocalBinder mBinder = new MultiBluetoothLeService.LocalBinder();
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
        // TODO: Return the communication channel to the service.
        //   throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    public void close(){
        threadPoolExecutor.shutdown();
        if(connectionQueue.isEmpty()){
            return;
        }
        for(BluetoothGatt bluetoothGatt:connectionQueue){
            bluetoothGatt.close();
        }
        connectionQueue.clear();
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            String intentAction;
            String address = gatt.getDevice().getAddress();
            Log.d(TAG, "onConnectionStateChange: status "+status+" state: "+newState);
            if(status == BluetoothGatt.GATT_SUCCESS){
                if(newState == BluetoothProfile.STATE_CONNECTED){
                    mConnectState = STATE_CONNECTED;
                    intentAction = ACTION_GATT_CONNECTED;
                    connectionQueue.add(gatt);
                    broadcastUpdate(intentAction,gatt);
                    Log.i(TAG, gatt.getDevice().getAddress()+"Connected to GATT server.");
                    // Attempts to discover services after successful connection.
             /*   Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());*/
                    Log.i(TAG, "Attempting to start service discovery:");
                    for(BluetoothGatt bluetoothGatt:connectionQueue){
                        bluetoothGatt.discoverServices();
                    }

                }else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                    Log.i(TAG, gatt.getDevice().getAddress()+"Disconnected from GATT server.");
                    mConnectState = STATE_DISCONNECTED;
                    //  close();/
                    intentAction = ACTION_GATT_DISCONNECTED;
                    broadcastUpdate(intentAction,gatt);
                    connectionQueue.remove(gatt);
                    gatt.disconnect();
                    gatt.close();
                    //gatt.connect();
                    // Log.i(TAG, gatt.getDevice().getAddress()+"Reconnect...");
                }else if(newState == BluetoothProfile.STATE_CONNECTING){
                    Log.i(TAG, gatt.getDevice().getAddress()+"CONNECTING");
                }
            }else{
                gatt.disconnect();
                gatt.close();
                connect(address);
                intentAction = ACTION_GATT_RECONNECT;
                broadcastUpdate(intentAction,gatt);

            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if(status == BluetoothGatt.GATT_SUCCESS){
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED,gatt);
            }else{
                Log.w(TAG, "onServicesDiscovered received: "+ status );
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if(status == BluetoothGatt.GATT_SUCCESS){
                Log.d(TAG, "onCharacteristicRead: ");
                broadcastUpdate(ACTION_DATA_AVAILABLE,characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.d(TAG, "onCharacteristicChanged: ");
            broadcastUpdate(ACTION_DATA_AVAILABLE,characteristic);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d(TAG, "onCharacteristicWrite: status "+status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "发送成功");
            }
        }
    };

    private void broadcastUpdate(final String action){
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,BluetoothGatt gatt){
        String deviceAddress = gatt.getDevice().getAddress();
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DEVICE,deviceAddress);
        sendBroadcast(intent);
    }


    private void broadcastUpdate(final String action,final BluetoothGattCharacteristic characteristic){
        final Intent intent = new Intent(action);
        // This is special handling for the Heart Rate Measurement profile.  Data parsing is
        // carried out as per profile specifications:
        if(UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())){
            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "Heart rate format UINT16.");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "Heart rate format UINT8.");
            }
            final int heartRate = characteristic.getIntValue(format,1);
            Log.d(TAG, String.format("Received heart rate: %d", heartRate));
            intent.putExtra(EXTRA_DATA,String.valueOf(heartRate));
        }else{
            final byte[] data = characteristic.getValue();

            if(data != null && data.length > 0){
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data)
                    stringBuilder.append(String.format("%02X",byteChar));
                Log.d(TAG, "receive data: "+stringBuilder.toString());
                intent.putExtra(EXTRA_DATA,new String(data)+"\n"+stringBuilder.toString());
            }
        }
        sendBroadcast(intent);
    }


    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize(){
        if(mBluetoothManager == null){
            mBluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
            if(mBluetoothManager == null){
                Log.e(TAG, "Unable to initialize BluetoothManager" );
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if(mBluetoothAdapter == null){
            Log.e(TAG, "Unable to obtain BluetoothAdapter");
            return false;
        }
        return true;
    }
    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(String address){
        if(mBluetoothAdapter == null|| address == null){
            Log.w(TAG, "BluetoothAdapter not initialize or unspecified address" );
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if(mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)){
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");

            if(mBluetoothGatt.connect()){
                mConnectState = STATE_CONNECTING;
                return true;
            }else{
                return false;
            }
        }

        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if(device == null){
            Log.w(TAG, "Device not found.  Unable to connect." );
            return false;
        }

       // mBluetoothGatt = device.connectGatt(this,false,mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
      //  mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        //Android版本大于6的构造方法connectGatt中最后一个参数，设置为BluetoothDevice.TRANSPORT_LE，解决连接错误133的问题。
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback,BluetoothDevice.TRANSPORT_LE);
      //  connectionQueue.add(bluetoothGatt);


        mBluetoothDeviceAddress = address;
        mConnectState = STATE_CONNECTING;
        return true;
    }

    public void disconnect(String address){
        if(mBluetoothAdapter == null || connectionQueue.isEmpty()){
            Log.w(TAG,"BluetoothAdapter not initialized");
            return;
        }

        for(BluetoothGatt bluetoothGatt:connectionQueue){
            if(address.equals(bluetoothGatt.getDevice().getAddress())){
                bluetoothGatt.disconnect();
            }
        }

    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect(){
        if(mBluetoothAdapter == null || connectionQueue.isEmpty()){
            Log.w(TAG,"BluetoothAdapter not initialized");
            return;
        }
        for(BluetoothGatt bluetoothGatt:connectionQueue){
            bluetoothGatt.disconnect();
        }
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic){
        if(mBluetoothAdapter == null ||connectionQueue.isEmpty()){
            Log.w(TAG,"BluetoothAdapter not initialized");
            return;
        }
       // mBluetoothGatt.readCharacteristic(characteristic);
        for(BluetoothGatt bluetoothGatt:connectionQueue){
            bluetoothGatt.readCharacteristic(characteristic);
        }
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGatt gatt,BluetoothGattCharacteristic characteristic,boolean enabled){
        if(mBluetoothAdapter == null || gatt ==null){
            Log.w(TAG,"BluetoothAdapter not initialized");
            return;
        }

        gatt.setCharacteristicNotification(characteristic,enabled);



        if(UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())){
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
           // mBluetoothGatt.writeDescriptor(descriptor);
            gatt.writeDescriptor(descriptor);

        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *发现服务成功后，将所有GATT 服务添加到列表
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        List<BluetoothGattService> bluetoothGattServiceList = new ArrayList<>();
        if (connectionQueue.isEmpty()) return null;

        for(BluetoothGatt bluetoothGatt:connectionQueue){
            bluetoothGattServiceList.addAll(bluetoothGatt.getServices());
        }
        return bluetoothGattServiceList;
    }

    /**
     * @param service
     * @param characteristic
     * @param values
     * 向所有设备发送数据
     */
    public void writeData(UUID service, UUID characteristic, String values){
        if(mBluetoothAdapter == null || connectionQueue.isEmpty()){
            Log.w(TAG,"BluetoothAdapter not initialized");
            return;
        }
        for(BluetoothGatt bluetoothGatt:connectionQueue){
            writeCharacteristic(bluetoothGatt,service,characteristic,values);
        }

    }



    /**
     * 往特定的通道写入数据
     */
    public void writeCharacteristic(BluetoothGatt gatt,UUID service, UUID characteristic, String values) {
        writeCharacteristic(gatt,service, characteristic, values.getBytes());
    }

    /**
     * write something data to characteristic
     */
    public void writeCharacteristic(final BluetoothGatt gatt, final UUID service,
                                    final UUID characteristic,
                                    final byte[] values) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Log.e("TAG","run :  当前线程："+Thread.currentThread().getName());
                //往蓝牙数据通道的写入数据
                BluetoothGattService gattService = gatt.getService(service);
                BluetoothGattCharacteristic gattCharacteristic = gattService.getCharacteristic(characteristic);
                gattCharacteristic.setValue(values);
                gatt.writeCharacteristic(gattCharacteristic);
            }
        };
        threadPoolExecutor.execute(runnable);


    }


}
