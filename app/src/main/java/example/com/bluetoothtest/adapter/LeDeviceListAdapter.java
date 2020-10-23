package example.com.bluetoothtest.adapter;

import android.bluetooth.BluetoothDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;


import java.util.ArrayList;

import example.com.bluetoothtest.R;
import example.com.bluetoothtest.bean.BLEDevice;


/**
 * Created by clara.tong on 2020/10/15
 */
public class LeDeviceListAdapter extends BaseAdapter {
    private ArrayList<BLEDevice> mLeDevices;
    private ArrayList<BluetoothDevice> mSelectedDevices;

/*    public LeDeviceListAdapter(ArrayList<BLEDevice> leDeviceList) {
        mLeDevices = leDeviceList;
        mSelectedDevices.clear();
    }*/
    public LeDeviceListAdapter() {
        mLeDevices = new ArrayList<>();
        mSelectedDevices = new ArrayList<>();
    }

    public void addDevice(BluetoothDevice device){
        for(BLEDevice bleDevice:mLeDevices){
            if(bleDevice.getBluetoothDevice().getAddress().equals(device.getAddress())){
               return;
            }
        }
        BLEDevice blDevice = new BLEDevice();
        blDevice.setChecked(false);
        blDevice.setBluetoothDevice(device);
        mLeDevices.add(blDevice);
        notifyDataSetChanged();
    /*    if(!mLeDevices.contains(device)){
            mLeDevices.add(device);
        }*/
    }




    public ArrayList<BluetoothDevice> getSelectedDevices(){
        return mSelectedDevices;
    }

    public BLEDevice getDevice(int position){
        return mLeDevices.get(position);
    }

    public void clear(){
        mLeDevices.clear();
        mSelectedDevices.clear();
    }

    @Override
    public int getCount() {
        return mLeDevices.size();
    }

    @Override
    public Object getItem(int i) {
        return mLeDevices.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        LeDeviceListAdapter.ViewHolder viewHolder;
        if(view == null){
           view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.bluetooth_device_list_item, viewGroup, false);
            //view = LayoutInflater.from(getApplicationContext()).inflate(resourceId,viewGroup,false);
            viewHolder = new LeDeviceListAdapter.ViewHolder();
            viewHolder.deviceName = (TextView)view.findViewById(R.id.device_name);
            viewHolder.deviceAddress = (TextView)view.findViewById(R.id.device_address);
            viewHolder.deviceSelectCb = (CheckBox)view.findViewById(R.id.cb_device_select);
            view.setTag(viewHolder);
        }else {
            viewHolder = (LeDeviceListAdapter.ViewHolder)view.getTag();
        }


        final BLEDevice bledevice =  (BLEDevice)getItem(i);
        final BluetoothDevice device = bledevice.getBluetoothDevice();
        String deviceName = device.getName();
        if(deviceName != null && deviceName.length() > 0){
            viewHolder.deviceName.setText(deviceName);
        }else{
            viewHolder.deviceName.setText(R.string.unknow_device);
        }
        viewHolder.deviceAddress.setText(device.getAddress());
        viewHolder.deviceSelectCb.setOnCheckedChangeListener(null);
        viewHolder.deviceSelectCb.setChecked(bledevice.isChecked());
        viewHolder.deviceSelectCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    bledevice.setChecked(true);
                    if(!mSelectedDevices.contains(device)){
                        mSelectedDevices.add(device);
                    }
                }else{
                    bledevice.setChecked(false);
                    if(mSelectedDevices.contains(device)){
                        mSelectedDevices.remove(device);
                    }
                }
            }
        });
        return view;
    }

    class ViewHolder{
        TextView deviceName;
        TextView deviceAddress;
        CheckBox deviceSelectCb;
    }
}
