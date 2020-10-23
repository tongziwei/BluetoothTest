package example.com.bluetoothtest.bluetooth;

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
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MultiBluetoothLeManager {
    private static final String TAG = MultiBluetoothLeManager.class.getSimpleName();
    private LocalBroadcastManager mLocalBroadcastManager;
    private Context mContext;
    //创建基本线程池
    final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(5,Integer.MAX_VALUE,1,TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(20));

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
 //   private BluetoothGatt mBluetoothGatt;
    private ArrayList<BluetoothGatt> connectionQueue = new ArrayList<BluetoothGatt>();
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;

    private int mConnectState = STATE_DISCONNECTED;
    private static boolean gatt_status_133 = false;

    final Handler handler = new Handler();

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED           = "com.goertek.futing.ble.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED        = "com.goertek.futing.ble.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_RECONNECT       = "com.goertek.futing.ble.ACTION_GATT_RECONNECT";

    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.goertek.futing.ble.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE           = "com.goertek.futing.ble.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA                      = "com.goertek.futing.ble.EXTRA_DATA";
    public final static String EXTRA_DEVICE                      = "com.goertek.futing.ble.EXTRA_DEVICE";

//    private boolean isSendSuccessful = true;
//    private boolean synFlag = true;
//    private LinkedList<BluetoothCharacterItem> bluetoothCharacterList = new LinkedList<>();
    private List<BluetoothMessage> mBluetoothMessage = new ArrayList<>(); //对应多个设备，大小为设备大小

    private boolean isFinish =false;

    public MultiBluetoothLeManager(Context mContext) {
        this.mContext = mContext;
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(mContext);
        initMessageThread();
    }

    public void close(){
        destroy();
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
                  /*  for(BluetoothGatt bluetoothGatt:connectionQueue){
                        bluetoothGatt.discoverServices();
                    }*/
                  gatt.discoverServices();

                }else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                    Log.i(TAG, gatt.getDevice().getAddress()+"Disconnected from GATT server.");
                    mConnectState = STATE_DISCONNECTED;
                    //  close();/
                    intentAction = ACTION_GATT_DISCONNECTED;
                    broadcastUpdate(intentAction,gatt);
                    connectionQueue.remove(gatt);
                    deleteMessageList(address);
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
                gatt_status_133 =true;

            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if(status == BluetoothGatt.GATT_SUCCESS){
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED,gatt);
                enableNotification(gatt);    //enable接收通知
                if(gatt.requestMtu(256)){              //设置BLE最大传输单元为256byte,需在服务发现后设置
                    Log.d(TAG,"requestMtu success");
                }else{
                    Log.d(TAG,"requestMtu fail");
                }

            }else{
                Log.w(TAG, "onServicesDiscovered received: "+ status );
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);     //调用readCharacteristic后回调
            if(status == BluetoothGatt.GATT_SUCCESS){
                Log.d(TAG, "onCharacteristicRead: ");
                broadcastUpdate(ACTION_DATA_AVAILABLE,characteristic,gatt);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.d(TAG, "onCharacteristicChanged: ");      //调用setCharacteristicNotification() 后回调
            broadcastUpdate(ACTION_DATA_AVAILABLE,characteristic,gatt);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d(TAG, "onCharacteristicWrite: status "+status);
           String address = gatt.getDevice().getAddress();
            for (BluetoothMessage item: mBluetoothMessage) {
                if (item.address.equals(address)){
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        item.isSendSuccessful = true;
                        item.synFlag = true;
                        String deviceAddress = gatt.getDevice().getAddress();
                       // Log.d(TAG, deviceAddress+"发送成功,value: "+CrMathUtils.getStringFromBytes(characteristic.getValue()));
                    }else{
                        item.isSendSuccessful = false;
                    }
                }
            }

        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            Log.d(TAG," onMtuChanged:"+mtu);
            if (BluetoothGatt.GATT_SUCCESS == status) {
                Log.d(TAG, "onMtuChanged success MTU = " + mtu);
            }else {
                Log.d(TAG, "onMtuChanged fail ");
            }

        }
    };


    private void broadcastUpdate(final String action,BluetoothGatt gatt){
        String deviceAddress = gatt.getDevice().getAddress();
       /* if(deviceName==null){
            deviceName ="Unknow Device";
        }*/
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DEVICE,deviceAddress);
        mLocalBroadcastManager.sendBroadcast(intent);
    }


    private void broadcastUpdate(final String action,final BluetoothGattCharacteristic characteristic,BluetoothGatt gatt){
        final Intent intent = new Intent(action);
        String deviceAddress = gatt.getDevice().getAddress();
        final byte[] data = characteristic.getValue();
        intent.putExtra(EXTRA_DEVICE,deviceAddress);

        if(data != null && data.length > 0){
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for (byte byteChar : data)
                stringBuilder.append(String.format("%02X",byteChar));
            Log.d(TAG, deviceAddress+" receive data: "+stringBuilder.toString());
            intent.putExtra(EXTRA_DATA,stringBuilder.toString());
        }

        mLocalBroadcastManager.sendBroadcast(intent);

    }


    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize(){
        if(mBluetoothManager == null){
            mBluetoothManager = (BluetoothManager)mContext.getSystemService(Context.BLUETOOTH_SERVICE);
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
    public boolean connect(final String address){
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
        //Android版本大于6的构造方法connectGatt中最后一个参数，设置为BluetoothDevice.TRANSPORT_LE，解决连接错误133的问题。
        mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback,BluetoothDevice.TRANSPORT_LE);
//        mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback);
      //  connectionQueue.add(bluetoothGatt);
        mBluetoothDeviceAddress = address;
        mConnectState = STATE_CONNECTING;

  /*      handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(gatt_status_133)
                {
                    Log.d(TAG, "Catch issue");
                    connect(mBluetoothDeviceAddress);
                    gatt_status_133=false;
                }
            }
        }, 4000);*/

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
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic,BluetoothGatt gatt){
        if(mBluetoothAdapter == null ||gatt ==null){
            Log.w(TAG,"BluetoothAdapter not initialized");
            return;
        }
        gatt.readCharacteristic(characteristic);
    }

    private void enableNotification(BluetoothGatt gatt){
        BluetoothGattService gattService = gatt.getService(UUID.fromString(SampleGattAttributes.BES_BLE_SERVICE));
        BluetoothGattCharacteristic gattCharacteristic = gattService.getCharacteristic(UUID.fromString(SampleGattAttributes.BES_BLE_READ_NOTIFY_CHARACTERISTIC));
        setCharacteristicNotification(gatt,gattCharacteristic,true);
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

        if(SampleGattAttributes.BES_BLE_READ_NOTIFY_CHARACTERISTIC.equals(characteristic.getUuid().toString())){
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);

        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *发现服务成功后，将所有GATT 服务添加到列表
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices(BluetoothGatt gatt) {
        List<BluetoothGattService> bluetoothGattServiceList = new ArrayList<>();
        bluetoothGattServiceList.addAll(gatt.getServices());
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

    public void writeData(UUID service, UUID characteristic, byte[] values){
        if(mBluetoothAdapter == null || connectionQueue.isEmpty()){
            Log.w(TAG,"BluetoothAdapter not initialized");
            return;
        }
        Log.d(TAG, "connectionQueue size："+connectionQueue.size());
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
    private void writeCharacteristic(final BluetoothGatt gatt, final UUID service,
                                    final UUID characteristic,
                                    final byte[] values) {
        pushMessage(gatt, service,characteristic,values);
    }

    private void pushMessage(final BluetoothGatt gatt, final UUID service,
                             final UUID characteristic,
                             final byte[] values){

        String address = gatt.getDevice().getAddress();
        synchronized (mBluetoothMessage) {
            if (mBluetoothMessage.size() > 0) {
                for (BluetoothMessage item : mBluetoothMessage) {
                    if (item.address.equals(address)) {
                        item.bluetoothCharacterList.addFirst(new BluetoothCharacterItem(gatt, service, characteristic, values));

                        Log.d(TAG, "pushMessage address:" + address + ";bluetoothCharacterList size:" + item.bluetoothCharacterList.size());
                        return;
                    }
                }
            }
            BluetoothMessage bluetoothMessage = new BluetoothMessage();
            bluetoothMessage.address = address;
            bluetoothMessage.bluetoothCharacterList.addFirst(new BluetoothCharacterItem(gatt, service, characteristic, values));
            mBluetoothMessage.add(bluetoothMessage);
        }
        Log.d(TAG,"pushMessage address:"+address + ";bluetoothCharacterList size:"+ 1);
    }

    private void initMessageThread(){
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                while (!isFinish){
                    //是否需要发送数据
                    boolean isSendData = false;
                    if(mBluetoothMessage.size() > 0){
                        for (BluetoothMessage item :mBluetoothMessage){
                            if (item.bluetoothCharacterList.size() > 0){
                                isSendData = true;
                                break;
                            }
                        }
                    }
                    if (isSendData){

                        //发送数据状态监测
                        boolean isNeedSleep = false;
                        synchronized (mBluetoothMessage) {
                            for (int i = mBluetoothMessage.size() - 1; i > -1; i--) {
                                //判断是否需要睡眠
                                if (mBluetoothMessage.get(i).synFlag) {
                                    if (mBluetoothMessage.get(i).isSendSuccessful) {
                                        if (mBluetoothMessage.get(i).currentBlutoothCharacter == mBluetoothMessage.get(i).bluetoothCharacterList.getLast()) {
                                            mBluetoothMessage.get(i).bluetoothCharacterList.removeLast();
                                            if (mBluetoothMessage.get(i).bluetoothCharacterList.size() > 0) {
                                                sendMessage(mBluetoothMessage.get(i));
                                            } else {
                                                mBluetoothMessage.remove(i);
                                            }
                                        } else if (mBluetoothMessage.get(i).currentBlutoothCharacter == null) {
                                            if (mBluetoothMessage.get(i).bluetoothCharacterList.size() > 0) {
                                                sendMessage(mBluetoothMessage.get(i));
                                            }
                                        }

                                    } else {
                                        sendMessage(mBluetoothMessage.get(i));
                                    }
                                } else {
                                    isNeedSleep = true;
                                }
                            }
                        }
                        if (isNeedSleep){
                            try {
                                Thread.sleep(40);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }else{
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }



//                    if (bluetoothCharacterList.size() > 0){
//                        if (synFlag){
//                            if (isSendSuccessful){
//                                if(currentBlutoothCharacter == bluetoothCharacterList.getLast()){
//                                    bluetoothCharacterList.removeLast();
//                                    if (bluetoothCharacterList.size() > 0){
//                                        sendMessage();
//                                    }else{
//                                        currentBlutoothCharacter = null;
//                                    }
//                                }else if(currentBlutoothCharacter == null){
//                                    if (bluetoothCharacterList.size() > 0){
//                                        sendMessage();
//                                    }
//                                }
//
//                            }else{
//                                sendMessage();
//                            }
//                        }else{
//                            try {
//                                Thread.sleep(40);
//                            } catch (InterruptedException e) {
//                                e.printStackTrace();
//                            }
//                        }
//                    }else{
//                        try {
//                            Thread.sleep(200);
////                            Log.d(TAG,"hearing ......");
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//                    }


                }
            }
        };

        threadPoolExecutor.execute(runnable);
    }

    private void sendMessage(BluetoothMessage bluetoothMessage){
        Log.d(TAG,"sendMessage address:"+bluetoothMessage.getAddress());
        BluetoothCharacterItem item = bluetoothMessage.bluetoothCharacterList.getLast();
        if (item.getGatt() != null &&item.getService() != null){
            BluetoothGattService gattService = item.getGatt().getService(item.getService());
            if (gattService != null && item.getCharacteristic() != null){
                BluetoothGattCharacteristic gattCharacteristic = gattService.getCharacteristic(item.getCharacteristic());
                if (gattCharacteristic != null){
                    gattCharacteristic.setValue(item.getValues());
                    bluetoothMessage.synFlag = false;
                    bluetoothMessage.currentBlutoothCharacter = item;
                    boolean sendStatus = item.getGatt().writeCharacteristic(gattCharacteristic);
                    Log.d(TAG,"发送"+sendStatus);
                }

            }

        }

    }

//    private void sendMessage(){
//        BluetoothCharacterItem item = bluetoothCharacterList.getLast();
//        if (item.getGatt() != null &&item.getService() != null){
//            BluetoothGattService gattService = item.getGatt().getService(item.getService());
//            if (gattService != null && item.getCharacteristic() != null){
//                BluetoothGattCharacteristic gattCharacteristic = gattService.getCharacteristic(item.getCharacteristic());
//                if (gattCharacteristic != null){
//                    gattCharacteristic.setValue(item.getValues());
//                    synFlag = false;
//                    currentBlutoothCharacter = item;
//                    item.getGatt().writeCharacteristic(gattCharacteristic);
//                    Log.d(TAG,"发送成功");
//                }
//
//            }
//
//        }
//
//    }



    public void send(byte[] data, boolean CRLF,UUID service, UUID characteristic) {
        if(CRLF) {
            byte[] data2 = new byte[data.length + 2];
            for(int i = 0 ; i < data.length ; i++)
                data2[i] = data[i];
            data2[data2.length - 2] = 0x0A;
            data2[data2.length - 1] = 0x0D;
            writeData(service,characteristic,data2);
        } else {
            writeData(service,characteristic,data);
        }
    }

    /**
     * 发送的数据对象
     */
    class BluetoothCharacterItem {
        private BluetoothGatt gatt;
        private UUID service;      //用于发送数据的serviceUUID
        private UUID characteristic; //用于发送数据的characteristicUUID
        private byte[] values; //发送的数据

        public BluetoothCharacterItem(){}
        public BluetoothCharacterItem(BluetoothGatt gatt,UUID service,UUID characteristic, byte[] values){
            this.gatt = gatt;
            this.service = service;
            this.characteristic = characteristic;
            this.values = values;
        }

        public BluetoothGatt getGatt() {
            return gatt;
        }

        public void setGatt(BluetoothGatt gatt) {
            this.gatt = gatt;
        }

        public UUID getService() {
            return service;
        }

        public void setService(UUID service) {
            this.service = service;
        }

        public UUID getCharacteristic() {
            return characteristic;
        }

        public void setCharacteristic(UUID characteristic) {
            this.characteristic = characteristic;
        }

        public byte[] getValues() {
            return values;
        }

        public void setValues(byte[] values) {
            this.values = values;
        }
    }

    class BluetoothMessage{
        private boolean isSendSuccessful ;
        private boolean synFlag ;
        private LinkedList<BluetoothCharacterItem> bluetoothCharacterList;  //设备对应的发送数据队列
        private BluetoothCharacterItem currentBlutoothCharacter;
        private String address;

        public BluetoothMessage(){
            this.isSendSuccessful = true;
            this.synFlag = true;
            this.currentBlutoothCharacter = null;
            this.bluetoothCharacterList = new LinkedList<>();

        }

        public boolean isSendSuccessful() {
            return isSendSuccessful;
        }

        public void setSendSuccessful(boolean sendSuccessful) {
            isSendSuccessful = sendSuccessful;
        }

        public boolean isSynFlag() {
            return synFlag;
        }

        public void setSynFlag(boolean synFlag) {
            this.synFlag = synFlag;
        }

        public LinkedList<BluetoothCharacterItem> getBluetoothCharacterList() {
            return bluetoothCharacterList;
        }

        public void setBluetoothCharacterList(LinkedList<BluetoothCharacterItem> bluetoothCharacterList) {
            this.bluetoothCharacterList = bluetoothCharacterList;
        }

        public BluetoothCharacterItem getCurrentBlutoothCharacter() {
            return currentBlutoothCharacter;
        }

        public void setCurrentBlutoothCharacter(BluetoothCharacterItem currentBlutoothCharacter) {
            this.currentBlutoothCharacter = currentBlutoothCharacter;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }
    }

    //调用情况：BLE连接断开
    public void deleteMessageList(String address){
        synchronized (mBluetoothMessage){
            if(mBluetoothMessage.size() > 0){
                for (int i = 0;i<mBluetoothMessage.size();i++){
                    if (mBluetoothMessage.get(i).getAddress().equals(address)){
                        mBluetoothMessage.remove(i);
                    }
                }
            }
        }
    }

    public void destroy(){
        isFinish = true;
    }
}
