package com.example.soilmate;

import android.Manifest;
import android.bluetooth.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class BLESoilSensorHelper {
    private BluetoothGatt bluetoothGatt;
    private Context context;

    // UUIDs must match exactly with ESP32 code
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");

    public BLESoilSensorHelper(Context context) {
        this.context = context;
    }

    public void connectToDevice(BluetoothDevice device) {
        if (!hasBluetoothPermissions()) {
            Log.e("BLE", "Bluetooth permissions not granted");
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            } else {
                bluetoothGatt = device.connectGatt(context, false, gattCallback);
            }
        } catch (SecurityException e) {
            Log.e("BLE", "Bluetooth permission error", e);
        }
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (!hasBluetoothPermissions()) return;

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                try {
                    gatt.discoverServices();
                    Log.d("BLE", "Connected to device");
                } catch (SecurityException e) {
                    Log.e("BLE", "Discover services permission error", e);
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLE", "Disconnected from device");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (!hasBluetoothPermissions()) return;

            if (status == BluetoothGatt.GATT_SUCCESS) {
                try {
                    BluetoothGattService service = gatt.getService(SERVICE_UUID);
                    if (service != null) {
                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                        if (characteristic != null) {
                            // Enable notifications
                            gatt.setCharacteristicNotification(characteristic, true);
                            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(descriptor);

                            // Send server info
                            String serverInfo = "SERVER:http://192.168.0.104:8080/api/v1/2m4x93tvdvfpdwr0uo6d/telemetry";
                            characteristic.setValue(serverInfo.getBytes());
                            gatt.writeCharacteristic(characteristic);
                        }
                    }
                } catch (SecurityException e) {
                    Log.e("BLE", "Service discovery permission error", e);
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            // Handle incoming data from ESP32
            byte[] data = characteristic.getValue();
            String sensorData = new String(data, StandardCharsets.UTF_8);
            Log.d("BLE", "Received data: " + sensorData);
        }
    };

    public void disconnect() {
        if (bluetoothGatt != null) {
            try {
                if (hasBluetoothPermissions()) {
                    bluetoothGatt.disconnect();
                    bluetoothGatt.close();
                }
            } catch (SecurityException e) {
                Log.e("BLE", "Disconnect permission error", e);
            }
        }
    }
}