package com.tbc.vasa.bleadmin;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Main2Activity extends AppCompatActivity implements View.OnClickListener {

    private TextView mText;
    private Button mAdvertiseButton;
    private Button mDiscoverButton, ble;
    private EditText ble_msg;
    private BluetoothLeScanner mBluetoothLeScanner;
    private Handler mHandler = new Handler();
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mGattServer;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private AdvertiseCallback mAdvertiseCallback;
    private BluetoothGatt mGatt;
    private boolean mConnected;
    private boolean mInitialized = false;
    private String MSG_KEY = "THREAD_MSG: ";
    List<ScanResult> mScanResults = new ArrayList<>();
    List<BluetoothDevice> mDevices = new ArrayList<>();
    private UUID SERVICE_UUID = UUID.fromString("CDB7950D-73F1-4D4D-8E47-C090502DBD63");
    private UUID CHARACTERISTIC_UUID = UUID.fromString("CDB7950D-73F1-4D4D-8E47-C090502DBD66");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        mText = (TextView) findViewById( R.id.text );
        mDiscoverButton = (Button) findViewById( R.id.discover_btn );
        mAdvertiseButton = (Button) findViewById( R.id.advertise_btn );
        ble = (Button) findViewById( R.id.ble );
        ble_msg = (EditText) findViewById(R.id.ble_msg);

        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        assert mBluetoothManager != null;
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if( !mBluetoothAdapter.isMultipleAdvertisementSupported() ) {
            Toast.makeText( this, "Multiple advertisement not supported", Toast.LENGTH_SHORT ).show();
            mAdvertiseButton.setEnabled( false );
            mDiscoverButton.setEnabled( false );
        }

        ble.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(Main2Activity.this, MainActivity.class);
                startActivity(i);
            }
        });
        mDiscoverButton.setOnClickListener( this );
        mAdvertiseButton.setOnClickListener( this );

        GattServerCallback gattServerCallback = new GattServerCallback();
        mGattServer = mBluetoothManager.openGattServer(this, gattServerCallback);
        setupServer();
    }

    private void setupServer() {
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic writeCharacteristic = new BluetoothGattCharacteristic(
                CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);
        service.addCharacteristic(writeCharacteristic);

        mGattServer.addService(service);
    }


    @Override
    public void onClick(View v) {
        if( v.getId() == R.id.discover_btn ) {
            discover();
        } else if( v.getId() == R.id.advertise_btn ) {
            advertise();
        }
    }

    private void discover() {
        System.out.println("DISCOVER...");
        List<ScanFilter> filters = new ArrayList<>();
        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();
        filters.add(filter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                System.out.println("HANDLER...");
                mBluetoothLeScanner.stopScan(mScanCallback);
            }
        }, 100000);
    }

    private void advertise() {
        System.out.println("ADVERTISE...");

        mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .setConnectable(true)
                .build();

        ParcelUuid pUuid = new ParcelUuid(SERVICE_UUID);

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid( pUuid )
                .addServiceData( pUuid, "vasa".getBytes( Charset.forName( "UTF-8" ) ) )
                .build();

        mAdvertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                System.out.println("ADVERTISEMENT STARTED...");
                super.onStartSuccess(settingsInEffect);
            }

            @Override
            public void onStartFailure(int errorCode) {
                System.out.println("ADVERTISEMENT FAILED...");
                Log.e( "BLE", "Advertising onStartFailure: " + errorCode );
                super.onStartFailure(errorCode);
            }
        };

        mBluetoothLeAdvertiser.startAdvertising( settings, data, mAdvertiseCallback );
    }

    @Override
    protected void onResume() {
        super.onResume();

    }


    //Scanner Callback
    private ScanCallback mScanCallback = new ScanCallback() {
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onScanResult(int callbackType, ScanResult result) {



            super.onScanResult(callbackType, result);

            ParcelUuid pUuid = new ParcelUuid( UUID.fromString("0000950d-0000-1000-8000-00805f9b34fb"));
                //System.out.println("SCAN_CALLBACK... "+ new String(result.getScanRecord().getServiceData().get(pUuid), Charset.forName("UTF-8")));
            System.out.println("RSSI: "+ result.getRssi());
            //System.out.println("Result: "+ result);
            //System.out.println("TXPOWER: "+ result.getTxPower());
            //mText.setText("ASHISH: "+result);
            if(result.getRssi()>=(-65)){
                mText.setText("ASHISH: "+result);
                System.out.println("Result: "+ result);
                stopScan();
            }

            BluetoothDevice bluetoothDevice = result.getDevice();
            connectDevice(bluetoothDevice);

            if( result == null
                    || result.getDevice() == null
                    || TextUtils.isEmpty(result.getDevice().getName()))
                return;

            StringBuilder builder = new StringBuilder(result.getDevice().getName());

            builder.append("\n").append(new String(result.getScanRecord().getServiceData(result.getScanRecord().getServiceUuids().get(0)), Charset.forName("UTF-8")));


            //mText.setText(builder.toString());
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            System.out.println("BATCH RESULT...");
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e( "BLE", "Discovery onScanFailed: " + errorCode );
            System.out.println("SCAN FAILED...");
            super.onScanFailed(errorCode);
        }
    };

    private void connectDevice(BluetoothDevice device) {
        GattClientCallback gattClientCallback = new GattClientCallback();
        mGatt = device.connectGatt(this, false, gattClientCallback);
        mGatt.requestMtu(32);
    }

    //Client Callbacks
    private class GattClientCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            System.out.println("GATT_CLIENT_CALLBACK...");

            if (status == BluetoothGatt.GATT_FAILURE) {
                disconnectGattServer();
                return;
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                disconnectGattServer();
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                System.out.println("CONNECTION ESTABLISHED!!!");
                mConnected = true;
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectGattServer();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                return;
            }
            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            mInitialized = gatt.setCharacteristicNotification(characteristic, true);
            sendMessage(gatt);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            System.out.println("DATA WRITTEN TO CHARACTERISTIC...");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            byte[] messageBytes = characteristic.getValue();
            String messageString = null;
            try {
                messageString = new String(messageBytes, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Log.e("CLIENT_CHARACTERISTIC: ", "Unable to convert message bytes to string");
            }
            Log.d("Received message: " , messageString);

            /*Message msg = handler.obtainMessage();
            Bundle bundle = new Bundle();
            bundle.putString(MSG_KEY, messageString);
            msg.setData(bundle);
            handler.sendMessage(msg);*/



        }
    }

    private class GattServerCallback extends BluetoothGattServerCallback {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                System.out.println("NEW DEVICE ADDED...");
                mDevices.add(device);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mDevices.remove(device);
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);

            if (characteristic.getUuid().equals(CHARACTERISTIC_UUID)) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
                int length = value.length;
                byte[] reversed = new byte[length];
                for (int i = 0; i < length; i++) {
                    reversed[i] = value[length - (i + 1)];
                }
                characteristic.setValue(reversed);
                for (BluetoothDevice _device : mDevices) {
                    mGattServer.notifyCharacteristicChanged(_device, characteristic, false);
                }
            }
        }
    }


    public void disconnectGattServer() {
        mConnected = false;
        if (mGatt != null) {
            mGatt.disconnect();
            mGatt.close();
        }
    }

    private void stopServer() {
        if (mGattServer != null) {
            mGattServer.close();
        }
    }
    private void stopAdvertising() {
        if (mBluetoothLeAdvertiser != null) {
            mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
        }
    }

    private void stopScan() {
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled() && mBluetoothLeScanner != null) {
            mBluetoothLeScanner.stopScan(mScanCallback);
            //System.exit(0);
        }

        mScanCallback = null;
        mHandler = null;
    }

    private void sendMessage(BluetoothGatt gatt) {
        if (!mConnected) {
            return;
        }

        BluetoothGattService service = gatt.getService(SERVICE_UUID);
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
        String message = "ashish";
        System.out.println("MESSAGE: "+message);
        byte[] messageBytes = new byte[0];
        try {
            messageBytes = message.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            Log.e("MSG_SEND_ERR: ", "Failed to convert message string to byte array");
        }
        characteristic.setValue(messageBytes);
        boolean success = mGatt.writeCharacteristic(characteristic);
        if(success){
            System.out.println("MSG_STATUS: "+"msg written to server...");
        }
    }

    @SuppressLint("HandlerLeak")
    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            String string = bundle.getString(MSG_KEY);
            mText.setText(string);
        }
    };

    /*private void scanComplete() {
        if (mScanResults.isEmpty()) {
            return;
        }
        for (String deviceAddress : mScanResults.keySet()) {
            Log.d("FOUND DEVICES", "Found device: " + deviceAddress);
        }
    }*/

}
