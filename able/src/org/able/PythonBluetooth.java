package org.able;

import java.util.List;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

interface PythonBluetooth
{
        public void on_error(int reason, String msg);
        public void on_device(BluetoothDevice device, int rssi, byte[] record);
        public void on_scan_started(boolean success);
        public void on_scan_completed();
        public void on_services(int status, List<BluetoothGattService> services);
        public void on_characteristic_changed(BluetoothGattCharacteristic characteristic);
        public void on_characteristic_read(BluetoothGattCharacteristic characteristic, int status);
        public void on_characteristic_write(BluetoothGattCharacteristic characteristic, int status);
        public void on_descriptor_read(BluetoothGattDescriptor descriptor, int status);
        public void on_descriptor_write(BluetoothGattDescriptor descriptor, int status);
        public void on_connection_state_change(int status, int state);
}
