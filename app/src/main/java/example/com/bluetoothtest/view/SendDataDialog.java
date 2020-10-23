package example.com.bluetoothtest.view;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.support.annotation.NonNull;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import example.com.bluetoothtest.R;


/**
 * Created by clara.tong on 2019/9/17
 */
public class SendDataDialog extends Dialog {
    public static final int DIALOG_DELETE_ITEM = 0;

    private TextView mTvServiceUUID;
    private TextView mTvCharacteristicUUID;
    private Button mBtnDialogSend;
    private EditText mEtSendData;

    private String mServiceUUID;
    private String mCharacteristicUUID;
    private Context mContext;
    private OnSendDialogBtnClickListener mOnYesNoDialogBtnClickListener;

    public interface OnSendDialogBtnClickListener {
        void onDialogBtnSend(View view,String data);
    }

    public SendDataDialog(Context context) {
        super(context);
    }

    public SendDataDialog(@NonNull Context context,String serviceUUID,String characteristicUUID) {
        super(context);
        this.mContext = context;
        this.mServiceUUID = serviceUUID;
        this.mCharacteristicUUID = characteristicUUID;
        initView();
        initListener();
    }

    public void setOnSendDataDialogBtnClickListener(OnSendDialogBtnClickListener listener) {
        this.mOnYesNoDialogBtnClickListener = listener;
    }

    private void initView(){
       View view = LayoutInflater.from(mContext).inflate(R.layout.dialog_send_data,null);
       this.setContentView(view);
        //获取当前Activity所在的窗体
        Window dialogWindow = this.getWindow();
        //设置Dialog从窗体底部弹出
        dialogWindow.setGravity(Gravity.CENTER);
        dialogWindow.setBackgroundDrawable(new ColorDrawable(mContext.getResources().getColor(R.color.common_transparent)));
        //获得窗体的属性
        WindowManager.LayoutParams lp = dialogWindow.getAttributes();
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        //设置窗口高度为包裹内容
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;

        //将属性设置给窗体
        dialogWindow.setAttributes(lp);

        mTvServiceUUID = (TextView) view.findViewById(R.id.tv_service_uuid);
        mTvCharacteristicUUID = (TextView) view.findViewById(R.id.tv_characteristic_uuid);
        mEtSendData = (EditText)view.findViewById(R.id.et_dialog_send_data);
        mBtnDialogSend = (Button) view.findViewById(R.id.btn_dialog_send);

        mTvServiceUUID.setText(mServiceUUID);
        mTvCharacteristicUUID.setText(mCharacteristicUUID);

    }

    private void initListener() {
        mBtnDialogSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mOnYesNoDialogBtnClickListener.onDialogBtnSend(view,mEtSendData.getText().toString());
            }
        });


    }

}
