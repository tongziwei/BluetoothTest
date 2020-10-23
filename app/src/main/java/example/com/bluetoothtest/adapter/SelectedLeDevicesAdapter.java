package example.com.bluetoothtest.adapter;

import android.bluetooth.BluetoothDevice;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;

import example.com.bluetoothtest.OnDeviceConnectListener;
import example.com.bluetoothtest.R;
import example.com.bluetoothtest.bean.SelectedBluetoothDevice;

/**
 * Created by clara.tong on 2020/10/16
 */
public class SelectedLeDevicesAdapter extends RecyclerView.Adapter<SelectedLeDevicesAdapter.ViewHolder> {
    private ArrayList<SelectedBluetoothDevice> mSelectedBluetoothDevices;
    private OnDeviceConnectListener mOnDeviceConnectListener;

    public SelectedLeDevicesAdapter(ArrayList<SelectedBluetoothDevice> mSelectedBluetoothDevices) {
        this.mSelectedBluetoothDevices = mSelectedBluetoothDevices;
    }

    public void setOnDeviceConnectListener(OnDeviceConnectListener onDeviceConnectListener){
        this.mOnDeviceConnectListener = onDeviceConnectListener;
    }

    public void updateConnectState(String deviceAddress,boolean connectStatus){
         if(mSelectedBluetoothDevices!=null &&mSelectedBluetoothDevices.size()>0){
             for(SelectedBluetoothDevice device :mSelectedBluetoothDevices){
                 if(deviceAddress.equals(device.getDeviceAddress())){
                     device.setConnected(connectStatus);
                 }
             }
         }
         notifyDataSetChanged();
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.selected_ble_item,viewGroup,false);
        return  new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
        final SelectedBluetoothDevice device = mSelectedBluetoothDevices.get(i);
        String deviceName = device.getDeviceName();
        if(deviceName != null && deviceName.length() > 0){
            viewHolder.mTvDevcieName.setText(deviceName);
        }else{
            viewHolder.mTvDevcieName.setText(R.string.unknow_device);
        }
        viewHolder.mTvDeviceAddress.setText(device.getDeviceAddress());
        viewHolder.mTvDataReceived.setText(device.getReceivedData());
        if(device.isConnected()){
            viewHolder.mBtnConnect.setText(R.string.menu_disconnect);
        }else{
            viewHolder.mBtnConnect.setText(R.string.menu_connect);
        }
        viewHolder.mBtnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                 if(mOnDeviceConnectListener!=null){
                     mOnDeviceConnectListener.onConnectClick(v,device.getDeviceAddress(),device.isConnected());
                 }
            }
        });
    }

    @Override
    public int getItemCount() {
        return mSelectedBluetoothDevices == null ? 0 : mSelectedBluetoothDevices.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder{
       private TextView mTvDevcieName;
       private TextView mTvDeviceAddress;
       private Button mBtnConnect;
       private TextView mTvDataReceived;

       public ViewHolder(@NonNull View itemView) {
           super(itemView);
           mTvDevcieName = itemView.findViewById(R.id.tv_device_name);
           mTvDeviceAddress = itemView.findViewById(R.id.tv_device_address);
           mTvDataReceived = itemView.findViewById(R.id.tv_data_received);
           mBtnConnect = itemView.findViewById(R.id.btn_connect);
       }
   }

}
