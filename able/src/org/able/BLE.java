package org.able;

import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import android.util.SparseArray;
import org.kivy.android.PythonActivity;
import org.kivy.android.PythonService;

import java.util.List;
import java.util.ArrayList;


public class BLE {
    private String TAG = "BLE-python";
    private PythonBluetooth mPython;
    private Context mContext;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private List<BluetoothGattService> mBluetoothGattServices;
    private static BluetoothLeScanner mScanner = null;
    private MyScanCallback mScanCallback = new MyScanCallback();
    private boolean mScanning;


    public void showError(int reason, final String msg) {
        if (PythonActivity.mActivity != null)
            PythonActivity.mActivity.toastError(TAG + " error. " + msg);
        Log.d(TAG, msg);
        mPython.on_error(reason, msg);
    }

    public BLE(PythonBluetooth python) {
        mPython = python;
        mContext = PythonActivity.mActivity != null? (Context) PythonActivity.mActivity:
                PythonService.mService.getApplication().getApplicationContext();
        mBluetoothGatt = null;


        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            showError(PythonBluetooth.REASON_NO_BLE,"Device do not support Bluetooth Low Energy.");
            return;
        }

        final BluetoothManager bluetoothManager =
                (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    public BluetoothDevice getDevice(String address) {
        if (mBluetoothAdapter != null)
            return mBluetoothAdapter.getRemoteDevice(address);
        else
            return null;
    }

    private class BluetoothDisabledReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // It means the user has changed his bluetooth state.
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);

                if (state == BluetoothAdapter.STATE_TURNING_OFF) {
                    Log.i(TAG, "Bluetooth is turning off");
                    // The user bluetooth is turning off yet, but it is not disabled yet.
                }
                else if (state == BluetoothAdapter.STATE_OFF) {
                    Log.i(TAG, "Bluetooth is now off");
                    mPython.on_bluetooth_disabled(true);
                    mContext.unregisterReceiver(this);
                }
            }
        }
    }

    public void disable() {
        if (mBluetoothAdapter != null && mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON) {
            mContext.registerReceiver(new BluetoothDisabledReceiver(),
                new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            mBluetoothAdapter.disable();
        }
        else
            mPython.on_bluetooth_disabled(false);
    }

    public BluetoothGatt getGatt() {
        return mBluetoothGatt;
    }

    private class MyScanCallback extends ScanCallback {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            SparseArray<byte[]> sp = null;
            byte[] bt = null;
            Log.i(TAG, "onScanResult "+result.getDevice().getName()+"/"+result.getDevice().getAddress());
            ScanRecord sr = result.getScanRecord();
            if ((sp = sr.getManufacturerSpecificData()) != null && sp.size() > 0)
                bt = sp.get(sp.keyAt(0));
            mPython.on_device(result.getDevice(), result.getRssi(), bt==null?new byte[0]:bt);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                onScanResult(1, sr);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.i(TAG, "onScanFailed "+errorCode);
            mScanning = false;
            mPython.on_scan_started(false);
        }
    }

    public void startScan(int EnableBtCode, ScanSettings ss, List<ScanFilter> sfs) {
        Log.d(TAG, "startScan");
        if (mBluetoothAdapter == null) {
            showError(PythonBluetooth.REASON_NO_ADAPTER, "Device do not support Bluetooth Low Energy.");
            return;
        }
        if (!mBluetoothAdapter.isEnabled()) {
            if (PythonActivity.mActivity != null) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                PythonActivity.mActivity.startActivityForResult(enableBtIntent, EnableBtCode);
            }
            else
                showError(PythonBluetooth.REASON_NOT_ENABLED, "Bluetooth is disabled.");
            return;
        }
        if (mScanner == null) {
            mScanner = mBluetoothAdapter.getBluetoothLeScanner();
            if (mScanner == null) {
                showError(PythonBluetooth.REASON_NO_SCANNER, "Bluetooth scanner not available.");
                return;
            }
        }
        if  (ss==null)
            ss = (new ScanSettings.Builder()).build();
        if (sfs==null)
            sfs = new ArrayList<ScanFilter>();

        mScanner.startScan(sfs, ss, mScanCallback);
        mScanning = true;
        mPython.on_scan_started(true);
    }

    public void stopScan() {
        if (mScanning == true) {
            Log.d(TAG, "stopScan");
            mScanning = false;
            mScanner.stopScan(mScanCallback);
            mPython.on_scan_completed();
        }
    }

    public void connectGatt(BluetoothDevice device) {
        Log.d(TAG, "connectGatt");
        if (mBluetoothGatt == null) {
            mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback);
        }
    }

    public void closeGatt() {
        Log.d(TAG, "closeGatt");
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
    }

    private final BluetoothGattCallback mGattCallback =
            new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.d(TAG, "Connected to GATT server, status:" + status);
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d(TAG, "Disconnected from GATT server, status:" + status);
                    }
                    mPython.on_connection_state_change(status, newState);
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "onServicesDiscovered - success");
                        mBluetoothGattServices = mBluetoothGatt.getServices();
                    } else {
                        showError(PythonBluetooth.REASON_DISCOVER_ERROR,"onServicesDiscovered status:" + status);
                        mBluetoothGattServices = null;
                    }
                    mPython.on_services(status, mBluetoothGattServices);
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt,
                                                    BluetoothGattCharacteristic characteristic) {
                    mPython.on_characteristic_changed(characteristic);
                }

                public void onCharacteristicRead(BluetoothGatt gatt,
                                                 BluetoothGattCharacteristic characteristic,
                                                 int status) {
                    mPython.on_characteristic_read(characteristic, status);
                }

                public void onCharacteristicWrite(BluetoothGatt gatt,
                                                  BluetoothGattCharacteristic characteristic,
                                                  int status) {
                    mPython.on_characteristic_write(characteristic, status);
                }

                public void onDescriptorRead(BluetoothGatt gatt,
                                             BluetoothGattDescriptor descriptor,
                                             int status) {
                    mPython.on_descriptor_read(descriptor, status);
                }

                public void onDescriptorWrite(BluetoothGatt gatt,
                                              BluetoothGattDescriptor descriptor,
                                              int status) {
                    mPython.on_descriptor_write(descriptor, status);
                }

            };

    public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] data) {
        if (characteristic.setValue(data)) {
            return mBluetoothGatt.writeCharacteristic(characteristic);
        }
        return false;
    }

    public boolean writeCharacteristicNoResponse(BluetoothGattCharacteristic characteristic, byte[] data) {
        if (characteristic.setValue(data)) {
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            return mBluetoothGatt.writeCharacteristic(characteristic);
        }
        return false;
    }

    public boolean readCharacteristic(BluetoothGattCharacteristic characteristic) {
        return mBluetoothGatt.readCharacteristic(characteristic);
    }
}
