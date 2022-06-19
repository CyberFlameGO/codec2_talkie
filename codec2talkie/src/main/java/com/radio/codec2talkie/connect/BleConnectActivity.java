package com.radio.codec2talkie.connect;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.text.style.ParagraphStyle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.radio.codec2talkie.R;
import com.radio.codec2talkie.settings.PreferenceKeys;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class BleConnectActivity extends AppCompatActivity {

    private final static int SCAN_PERIOD = 5000;

    private final static int BT_ENABLE = 1;
    private final static int BT_CONNECT_SUCCESS = 2;
    private final static int BT_CONNECT_FAILURE = 3;
    private final static int BT_SOCKET_FAILURE = 4;
    private final static int BT_ADAPTER_FAILURE = 5;
    private final static int BT_SCAN_COMPLETED = 6;

    private static final UUID BT_CLIENT_UUID = UUID.fromString("00000001-ba2a-46c9-ae49-01b0961f68bb");
    private static final UUID BT_MODULE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final int BtAddressLength = 17;

    private ProgressBar _progressBarBt;
    private ListView _btDevicesList;
    private TextView _textViewConnectingBt;

    private BluetoothLeScanner _btBleScanner;
    private BluetoothAdapter _btAdapter;
    private BluetoothSocket _btSocket;
    private ArrayAdapter<String> _btArrayAdapter;
    private String _btSelectedName;
    private String _btDefaultName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_connect);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        _btDefaultName = sharedPreferences.getString(PreferenceKeys.PORTS_BT_CLIENT_NAME, null);

        _btAdapter = BluetoothAdapter.getDefaultAdapter();
        _btArrayAdapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item);

        _btDevicesList = findViewById(R.id.btDevicesList);
        _btDevicesList.setAdapter(_btArrayAdapter);
        _btDevicesList.setOnItemClickListener(onBtDeviceClickListener);
        _btDevicesList.setVisibility(View.INVISIBLE);

        _textViewConnectingBt = findViewById(R.id.textViewConnectingBt);
        _textViewConnectingBt.setVisibility(View.VISIBLE);

        _progressBarBt = findViewById(R.id.progressBarBt);
        _progressBarBt.setVisibility(View.VISIBLE);
        ObjectAnimator.ofInt(_progressBarBt, "progress", 10)
                .setDuration(300)
                .start();

        _btBleScanner = _btAdapter.getBluetoothLeScanner();
        enableBluetooth();
    }

    private void enableBluetooth() {
        if (_btAdapter == null) {
            Message resultMsg = new Message();
            resultMsg.what = BT_ADAPTER_FAILURE;
            onBtStateChanged.sendMessage(resultMsg);
        } else if (_btAdapter.isEnabled()) {
            connectOrPopulateDevices();
        }
        else {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, BT_ENABLE);
            Toast.makeText(getApplicationContext(), getString(R.string.bt_turned_on), Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeviceList() {
        if (_btDevicesList.getVisibility() == View.INVISIBLE) {
            _progressBarBt.setVisibility(View.INVISIBLE);
            _textViewConnectingBt.setVisibility(View.INVISIBLE);
            _btDevicesList.setVisibility(View.VISIBLE);
        }
    }

    private void showProgressBar() {
        if (_progressBarBt.getVisibility() == View.INVISIBLE) {
            _progressBarBt.setVisibility(View.VISIBLE);
            _textViewConnectingBt.setVisibility(View.VISIBLE);
            _btDevicesList.setVisibility(View.INVISIBLE);
        }
    }

    private void connectOrPopulateDevices() {
        if (_btDefaultName != null && !_btDefaultName.trim().isEmpty()) {
            _btSelectedName = _btDefaultName;
            connectToBluetoothClient(addressFromDisplayName(_btDefaultName));
        } else {
            populateBondedDevices();
        }
    }

    private final ScanCallback leScanCallback =
        new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                _btArrayAdapter.add(result.getDevice().getName() + " | " + result.getDevice().getAddress());
            }
        };

    private void populateBondedDevices() {
        _btArrayAdapter.clear();

        ScanFilter.Builder scanFilterBuilder = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(BT_CLIENT_UUID));
        ScanFilter[] scanFilters = { scanFilterBuilder.build() };
        _btBleScanner.startScan(Arrays.asList(scanFilters), null, leScanCallback);

        Message resultMsg = new Message();
        resultMsg.what = BT_SCAN_COMPLETED;
        onBtStateChanged.sendMessageDelayed(resultMsg, SCAN_PERIOD);
    }

    private void connectToBluetoothClient(String address) {
        showProgressBar();
        BluetoothDevice device = _btAdapter.getRemoteDevice(address);

        new Thread() {
            @Override
            public void run() {
                BluetoothDevice btDevice = _btAdapter.getRemoteDevice(address);
                Message resultMsg = Message.obtain();
                resultMsg.what = BT_CONNECT_SUCCESS;
                try {
                    _btSocket = btDevice.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
                } catch (IOException e) {
                    resultMsg.what = BT_SOCKET_FAILURE;
                    onBtStateChanged.sendMessage(resultMsg);
                    return;
                }
                try {
                    _btSocket.connect();
                } catch (IOException e) {
                    resultMsg.what = BT_CONNECT_FAILURE;
                }
                onBtStateChanged.sendMessage(resultMsg);
            }
        }.start();
    }

    private final Handler onBtStateChanged = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            String toastMsg;
            if (msg.what == BT_CONNECT_FAILURE) {
                toastMsg = getString(R.string.bt_connect_failed);
                // connection to default device failed, fall back
                if (_btDefaultName != null && !_btDefaultName.trim().isEmpty()) {
                    _btDefaultName = null;
                    populateBondedDevices();
                    return;
                }
            } else if (msg.what == BT_SOCKET_FAILURE) {
                toastMsg = getString(R.string.bt_socket_failed);
            } else if (msg.what == BT_ADAPTER_FAILURE) {
                toastMsg = getString(R.string.bt_adapter_not_found);
            } else if (msg.what == BT_SCAN_COMPLETED) {
                toastMsg = getString(R.string.bt_ble_scan_completed);
                _btBleScanner.stopScan(leScanCallback);
            } else {
                toastMsg = getString(R.string.bt_connected);
                BluetoothSocketHandler.setSocket(_btSocket);
                Intent resultIntent = new Intent();
                resultIntent.putExtra("name", _btSelectedName);
                setResult(Activity.RESULT_OK, resultIntent);
            }
            Toast.makeText(getBaseContext(), toastMsg, Toast.LENGTH_SHORT).show();
            if (msg.what == BT_CONNECT_SUCCESS) {
                finish();
            }
            showDeviceList();
        }
    };

    private String addressFromDisplayName(String displayName) {
        return displayName.substring(displayName.length() - BtAddressLength);
    }

    private final AdapterView.OnItemClickListener onBtDeviceClickListener  = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            _btSelectedName = (String)parent.getAdapter().getItem(position);
            Toast.makeText(getApplicationContext(), getString(R.string.bt_connecting_to, _btSelectedName), Toast.LENGTH_LONG).show();
            connectToBluetoothClient(addressFromDisplayName(_btSelectedName));
        }
    };

    protected void onActivityResult(int requestCode, int resultCode, Intent Data) {
        super.onActivityResult(requestCode, resultCode, Data);
        if (requestCode == BT_ENABLE) {
            if (resultCode == RESULT_OK) {
                connectOrPopulateDevices();
            } else if (resultCode == RESULT_CANCELED){
                setResult(RESULT_CANCELED);
                finish();
            }
        }
    }
}