package com.example.soilmate;

import          android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.bumptech.glide.Glide;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.UUID;

public class PlantDetails extends AppCompatActivity {

    // UI Components
    private ImageView plantImage;
    private TextView plantName, soilMoistureText, humidityText, phLevelText, waterLevelText,
            nitrogenText, phosphorusText, potassiumText, minSoilMoistureLevel, connectionStatusText;
    private CircularProgressIndicator progSoil, progHum, progPh, progWater;
    private FlexboxLayout scheduleContainer;
    private ConstraintLayout scheduleSection;
    private AppCompatButton viewAllButton;
    private ProgressBar  progressBar, progressBar2;
    private ImageButton editButton, backButton, changeImgButton;
    private FlexboxLayout sensorPreviewLayout;
    private Button sensorBtn, manualRefreshBtn;

    // Bluetooth Related
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothDevice connectedDevice;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private boolean isScanning = false;

    // Data Refresh
    private final Handler dataRefreshHandler = new Handler(Looper.getMainLooper());
    private boolean isDataRefreshActive = false;
    private static final long DATA_REFRESH_INTERVAL = 2000; // 2 seconds

    // Network Related
    // Replace any mDNS-related variables with:
    private static final String THINGSBOARD_IP = "192.168.0.101:8080"; // Your actual IP with port
    private static final String TELEMETRY_URL = "/api/v1/2m4x93tvdvfpdwr0uo6d/telemetry";

    private static final String DEVICE_ID = "b668ad50-0b09-11f0-ac5d-b39d8f8876b0";
    private static final String TIMESERIES_URL = "/api/plugins/telemetry/DEVICE/" + DEVICE_ID + "/values/timeseries";
     // Firebase Related
    private FirebaseFirestore databaseReference;
    private Uri selectedImageUri;
    private ActivityResultLauncher<String> imagePickerLauncher;

    // Constants
    private static final String ACCESS_TOKEN = "Bearer " + "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJzYWxpZG9pdmVyc29uMEBnbWFpbC5jb20iLCJ1c2VySWQiOiJlYjRkZTU0MC0wYjA4LTExZjAtYjJlYy0wOTEyMmE3NGM1YTAiLCJzY29wZXMiOlsiVEVOQU5UX0FETUlOIl0sInNlc3Npb25JZCI6IjRjNDMwNzU4LTI0YWItNDcyYi05YjlkLWM1NzhhYzkxNDk4ZCIsImV4cCI6MTc0MzE5OTIzNywiaXNzIjoidGhpbmdzYm9hcmQuaW8iLCJpYXQiOjE3NDMxOTAyMzcsImZpcnN0TmFtZSI6Ikl2ZXJzb24iLCJsYXN0TmFtZSI6IlNhbGlkbyIsImVuYWJsZWQiOnRydWUsImlzUHVibGljIjpmYWxzZSwidGVuYW50SWQiOiJlYTI0Y2U5MC0wYjA4LTExZjAtYjJlYy0wOTEyMmE3NGM1YTAiLCJjdXN0b21lcklkIjoiMTM4MTQwMDAtMWRkMi0xMWIyLTgwODAtODA4MDgwODA4MDgwIn0.C8mf3L5ohA2QrZ2eZTgi9u0j3Hr2K-tEXEsTb9OB0mm9zwN22sot0sZxQv0UaI9OHIWjFeJwQRDYIisdZShxcw";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plant_details);

        // Initialize Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show();
            findViewById(R.id.sensor_btn).setEnabled(false);
        }

        // Initialize UI components
        initializeUI();

        // Set up button click listeners
        setupButtonListeners();

        // Load plant data from intent
        loadPlantData();


        // Register receivers
        registerBluetoothReceivers();

        String plantId = getIntent().getStringExtra("plantId");
        if (plantId != null) {
            fetchActualImageFromFirestore(plantId);
        }

        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        plantImage.setImageURI(selectedImageUri); // Show image in ImageView
                        Log.d("FirebaseUpload", "✅ Image selected: " + uri.toString());

                        // Automatically upload after selection
                        uploadImageToFirebase();
                    } else {
                        Log.e("FirebaseUpload", "❌ Image selection failed or canceled");
                    }
                });




        changeImgButton.setOnClickListener(v -> {
            openImagePicker();
        });
    }

    private void initializeUI() {
        plantImage = findViewById(R.id.plant_img);
        plantName = findViewById(R.id.plant_name);
        soilMoistureText = findViewById(R.id.soil_moisture);
        humidityText = findViewById(R.id.humid);
        phLevelText = findViewById(R.id.ph_level);
        waterLevelText = findViewById(R.id.water_level);
        nitrogenText = findViewById(R.id.nitrogen);
        phosphorusText = findViewById(R.id.phosphorus);
        potassiumText = findViewById(R.id.potassium);
        minSoilMoistureLevel = findViewById(R.id.minMoistureLevel);
        connectionStatusText = findViewById(R.id.connection_status);

        progSoil = findViewById(R.id.progsoil);
        progHum = findViewById(R.id.proghum);
        progPh = findViewById(R.id.progph);
        progWater = findViewById(R.id.progwat);

        scheduleContainer = findViewById(R.id.scheduleContainer);
        scheduleSection = findViewById(R.id.scheduleSection);
        progressBar = findViewById(R.id.progressBar);
        progressBar2 = findViewById(R.id.progressBar2);
        editButton = findViewById(R.id.editplant);
        backButton = findViewById(R.id.backbtn);
        viewAllButton = findViewById(R.id.viewall);
        sensorBtn = findViewById(R.id.sensor_btn);
        sensorPreviewLayout = findViewById(R.id.sensor_preview_layout);
        changeImgButton = findViewById(R.id.change_img);
        changeImgButton.setOnClickListener(v -> openImagePicker());
        manualRefreshBtn = findViewById(R.id.manual_refresh);

        // Initialize connection status
        updateConnectionStatus(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPlantData();
    }

    private void setupButtonListeners() {
        backButton.setOnClickListener(v -> finish());

        viewAllButton.setOnClickListener(v -> {
            Intent historyIntent = new Intent(PlantDetails.this, Watering_History.class);
            startActivity(historyIntent);
        });

        editButton.setOnClickListener(v -> {
            String plantId = getIntent().getStringExtra("plantId");
            if (plantId != null) {
                EditPlantPopUp editDialog = new EditPlantPopUp();
                Bundle args = new Bundle();
                args.putString("plantId", plantId);
                args.putString("plantName", plantName.getText().toString());
                args.putString("plantImage", "android.resource://" + getPackageName() + "/" + getIntent().getIntExtra("imageResId", R.drawable.img));
                args.putString("minSoilMoisture", getIntent().getStringExtra("minSoilMoisture"));
                editDialog.setArguments(args);

                editDialog.setOnDismissListener(new EditPlantPopUp.OnDismissListener() {
                    @Override
                    public void onDismiss() {
                        loadPlantData();
                    }
                });

                editDialog.show(getSupportFragmentManager(), "EditPlantPopUp");
            } else {
                Toast.makeText(this, "Error: Plant ID not found", Toast.LENGTH_SHORT).show();
            }
        });
        sensorBtn.setOnClickListener(v -> {
            if (isScanning) {
                stopBluetoothDiscovery();
            } else {
                startBluetoothDiscovery();
            }
        });

        manualRefreshBtn.setOnClickListener(v -> {
            if (bluetoothSocket != null && bluetoothSocket.isConnected()) {
                fetchThingsBoardTelemetry();
            } else {
                Toast.makeText(this, "Not connected to ESP32", Toast.LENGTH_SHORT).show();
            }
        });


        changeImgButton.setOnClickListener(v -> openImagePicker());
    }

    private void registerBluetoothReceivers() {
        // Register pairing receiver
        IntentFilter pairingFilter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(pairingReceiver, pairingFilter);

        // Register connection state receiver
        IntentFilter connectionFilter = new IntentFilter();
        connectionFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        connectionFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(bluetoothStateReceiver, connectionFilter);
    }

    private void loadPlantData() {
        Intent intent = getIntent();
        if (intent != null) {
            plantName.setText(intent.getStringExtra("plantName"));
            plantImage.setImageResource(intent.getIntExtra("imageResId", 0));
            minSoilMoistureLevel.setText(intent.getStringExtra("minSoilMoisture"));

            // Set sensor values
            int soilMoisture = intent.getIntExtra("soilMoisture", 0);
            int humidity = intent.getIntExtra("humidity", 0);
            float phLevel = intent.getFloatExtra("phLevel", 0.0f);
            int waterLevel = intent.getIntExtra("waterLevel", 0);
            int nitrogen = intent.getIntExtra("nitrogen", 0);
            int phosphorus = intent.getIntExtra("phosphorus", 0);
            int potassium = intent.getIntExtra("potassium", 0);

            // Update text values
            soilMoistureText.setText(soilMoisture + "%");
            humidityText.setText(humidity + "°C");
            phLevelText.setText(String.valueOf(phLevel));
            waterLevelText.setText(waterLevel + " µS/cm");
            nitrogenText.setText(nitrogen + " mg/kg");
            phosphorusText.setText(phosphorus + " mg/kg");
            potassiumText.setText(potassium + " mg/kg");

            // Set progress bars
            progSoil.setMax(100);
            progHum.setMax(100);
            progPh.setMax(14);
            progWater.setMax(50000);

            progSoil.setProgress(soilMoisture);
            progHum.setProgress(humidity);
            progPh.setProgress((int) phLevel);
            progWater.setProgress(waterLevel);

            // Load watering schedules
            ArrayList<String> schedules = intent.getStringArrayListExtra("wateringSchedules");
            if (schedules != null && !schedules.isEmpty()) {
                scheduleSection.setVisibility(View.VISIBLE);
                populateSchedules(schedules);
            } else {
                scheduleSection.setVisibility(View.GONE);
            }

            // Load actual image if available
            String plantId = intent.getStringExtra("plantId");
            if (plantId != null) {
                fetchActualImageFromFirestore(plantId);
            }
        }
    }

    // Bluetooth Methods
    private void startBluetoothDiscovery() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            requestEnableBluetooth();
            return;
        }

        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions();
            return;
        }

        sensorPreviewLayout.removeAllViews();
        isScanning = true;
        sensorBtn.setText("Stop Scan");
        Toast.makeText(this, "Scanning for Bluetooth devices...", Toast.LENGTH_SHORT).show();

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(bluetoothReceiver, filter);

        // Start discovery
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter.startDiscovery();
            }
        } catch (SecurityException e) {
            Log.e("Bluetooth", "Permission error starting discovery", e);
            Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show();
            isScanning = false;
        }

        // Stop discovery after 10 seconds
        new Handler().postDelayed(this::stopBluetoothDiscovery, 10000);
    }

    private void stopBluetoothDiscovery() {
        if (isScanning) {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothAdapter.cancelDiscovery();
                }
                unregisterReceiver(bluetoothReceiver);
            } catch (SecurityException e) {
                Log.e("Bluetooth", "Permission error stopping discovery", e);
            }
            isScanning = false;
            sensorBtn.setText("Connect Sensor");
            Toast.makeText(this, "Bluetooth scan complete", Toast.LENGTH_SHORT).show();
        }
    }

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                try {
                    if (ActivityCompat.checkSelfPermission(PlantDetails.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null && device.getName() != null &&
                            (device.getName().contains("Soil-Sensor") || device.getName().contains("ESP32"))) {
                        addSensorToList(device.getName(), device.getAddress());
                    }
                } catch (SecurityException e) {
                    Log.e("Bluetooth", "Permission exception in receiver", e);
                }
            }
        }
    };

    private void addSensorToList(String name, String address) {
        if (name == null || name.isEmpty()) return;

        Button sensorButton = new Button(this);
        sensorButton.setText(name);
        sensorButton.setTag(address);
        sensorButton.setBackground(null);
        sensorButton.setTextColor(ContextCompat.getColor(this, R.color.dg));

        sensorButton.setOnClickListener(v -> connectToSensor(address));

        sensorPreviewLayout.addView(sensorButton);
    }

    private void connectToSensor(String sensorAddress) {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No network connection available", Toast.LENGTH_LONG).show();
            return;
        }

        progressBar2.setVisibility(View.VISIBLE);

        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                requestBluetoothPermissions();
                return;
            }

            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(sensorAddress);
            connectedDevice = device;

            // Show pairing dialog
            showPairingDialog(device);

        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(PlantDetails.this, "Error connecting to sensor: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            Log.e("Bluetooth", "Connection error", e);
        }
    }

    private void showPairingDialog(BluetoothDevice device) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Bluetooth Pairing");
        builder.setMessage("Please confirm pairing on both devices");
        builder.setPositiveButton("OK", (dialog, which) -> {
            startPairingProcess(device);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            progressBar.setVisibility(View.GONE);
        });
        builder.setCancelable(false);
        builder.show();
    }

    private void startPairingProcess(BluetoothDevice device) {
        try {
            // Check permission first
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                requestBluetoothPermissions();
                return;
            }

            // Remove existing bond if any
            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                Method removeBondMethod = device.getClass().getMethod("removeBond");
                removeBondMethod.invoke(device);
                Thread.sleep(500); // Wait for unbond to complete
            }

            // Start pairing
            Log.d("Bluetooth", "Starting pairing process");
            boolean pairingStarted = device.createBond();

            if (!pairingStarted) {
                runOnUiThread(() -> {
                    progressBar2.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to start pairing", Toast.LENGTH_SHORT).show();
                });
            }
        } catch (Exception e) {
            Log.e("Bluetooth", "Pairing error", e);
            runOnUiThread(() -> {
                progressBar2.setVisibility(View.GONE);
                Toast.makeText(this, "Pairing error: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void connectAfterPairing(BluetoothDevice device) {
        new Thread(() -> {
            try {
                Thread.sleep(500); // Wait for pairing to complete
                BluetoothSocket socket = tryConnect(device);

                if (socket != null && socket.isConnected()) {
                    bluetoothSocket = socket;
                    outputStream = socket.getOutputStream();

                    // Send server info with hardcoded IP
                    String message = "SERVER:http://" + THINGSBOARD_IP + TELEMETRY_URL;
                    outputStream.write(message.getBytes());
                    outputStream.flush();

                    runOnUiThread(() -> {
                        progressBar2.setVisibility(View.GONE);
                        updateConnectionStatus(true);
                        Toast.makeText(this, "Connected to ESP32", Toast.LENGTH_SHORT).show();
                        startDataRefreshCycle(); // Start fetching data
                    });
                } else {
                    runOnUiThread(() -> {
                        progressBar2.setVisibility(View.GONE);
                        updateConnectionStatus(false);
                        Toast.makeText(this, "Connection failed", Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar2.setVisibility(View.GONE);
                    updateConnectionStatus(false);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private BluetoothSocket tryConnect(BluetoothDevice device) throws IOException {
        // Check for BLUETOOTH_CONNECT permission first
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("BLUETOOTH_CONNECT permission not granted");
        }

        // Try secure connection first
        try {
            BluetoothSocket socket = device.createRfcommSocketToServiceRecord(MY_UUID);
            socket.connect();
            return socket;
        } catch (IOException e) {
            Log.d("Bluetooth", "Secure connection failed, trying insecure");
        }

        // Try insecure connection
        try {
            BluetoothSocket socket = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            socket.connect();
            return socket;
        } catch (IOException e) {
            Log.d("Bluetooth", "Insecure connection failed");
        }

        // Try fallback method
        try {
            Method m = device.getClass().getMethod("createRfcommSocket", int.class);
            BluetoothSocket socket = (BluetoothSocket) m.invoke(device, 1);
            socket.connect();
            return socket;
        } catch (Exception e) {
            Log.e("Bluetooth", "Fallback connection failed", e);
        }

        return null;
    }

    private final BroadcastReceiver pairingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // Check permission first
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action) &&
                    device != null && device.equals(connectedDevice)) {

                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                int prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);

                runOnUiThread(() -> {
                    if (state == BluetoothDevice.BOND_BONDED) {
                        Log.d("Bluetooth", "Device paired successfully");
                        // Now connect and send message
                        connectAfterPairing(device);
                    } else if (state == BluetoothDevice.BOND_NONE) {
                        if (prevState == BluetoothDevice.BOND_BONDING) {
                            Log.d("Bluetooth", "Pairing failed");
                            progressBar2.setVisibility(View.GONE);
                            updateConnectionStatus(false);
                            Toast.makeText(PlantDetails.this,
                                    "Pairing failed. Please try again.", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }
    };

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                if (device != null && device.equals(connectedDevice)) {
                    runOnUiThread(() -> {
                        updateConnectionStatus(true);
                        startDataRefreshCycle();
                    });
                }
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                if (device != null && device.equals(connectedDevice)) {
                    runOnUiThread(() -> {
                        updateConnectionStatus(false);
                        stopDataRefreshCycle();
                        Toast.makeText(PlantDetails.this,
                                "ESP32 disconnected", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }
    };

    // Data Refresh Methods
    private void startDataRefreshCycle() {
        if (isDataRefreshActive) return;

        isDataRefreshActive = true;
        dataRefreshHandler.post(dataRefreshRunnable);
    }

    private void stopDataRefreshCycle() {
        if (!isDataRefreshActive) return;

        isDataRefreshActive = false;
        dataRefreshHandler.removeCallbacks(dataRefreshRunnable);
    }

    private final Runnable dataRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isDataRefreshActive) {
                fetchThingsBoardTelemetry();
                dataRefreshHandler.postDelayed(this, DATA_REFRESH_INTERVAL);
            }
        }
    };

    private void updateConnectionStatus(boolean connected) {
        if (connected) {
            connectionStatusText.setText("Connected to ESP32");
            connectionStatusText.setTextColor(ContextCompat.getColor(this, R.color.green));
        } else {
            connectionStatusText.setText("Disconnected");
            connectionStatusText.setTextColor(ContextCompat.getColor(this, R.color.red));
        }
    }

    // Permission Methods
    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQUEST_BLUETOOTH_PERMISSIONS);
        }
    }

    private void requestEnableBluetooth() {
        try {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED) {
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                } else {
                    requestBluetoothPermissions();
                }
            } else {
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        } catch (SecurityException e) {
            Log.e("Bluetooth", "SecurityException when enabling Bluetooth", e);
            requestBluetoothPermissions();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                startBluetoothDiscovery();
            } else {
                Toast.makeText(this, "Bluetooth must be enabled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Network Methods
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private void getNewToken() {
        new Thread(() -> {
            try {
                URL url = new URL("http://" + THINGSBOARD_IP + "/api/auth/login");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");

                // Replace with your credentials
                String credentials = "{\"username\":\"salidoiverson0@gmail.com\",\"password\":\"Cruel_summer89\"}";

                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                os.write(credentials.getBytes());
                os.flush();

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String response = br.readLine();
                    JSONObject json = new JSONObject(response);
                    String newToken = "Bearer " + json.getString("token");

                    // Store the new token
                    SharedPreferences.Editor editor = getSharedPreferences("AppPrefs", MODE_PRIVATE).edit();
                    editor.putString("access_token", newToken);
                    editor.apply();

                    runOnUiThread(() -> {
                        Toast.makeText(this, "Token refreshed!", Toast.LENGTH_SHORT).show();
                        fetchThingsBoardTelemetry(); // Retry with new token
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Login failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void fetchThingsBoardTelemetry() {
        new Thread(() -> {
            String authToken = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                    .getString("access_token", ACCESS_TOKEN);

            HttpURLConnection conn = null;
            try {
                URL url = new URL("http://" + THINGSBOARD_IP + TIMESERIES_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", authToken);
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);

                // Get the response code (this actually makes the connection)
                int responseCode = conn.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Handle successful response
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    // Parse JSON response
                    JSONObject telemetryData = new JSONObject(response.toString());
                    runOnUiThread(() -> updateUIWithTelemetry(telemetryData));
                } else {
                    runOnUiThread(() -> {
                        String errorDetail = "HTTP " + responseCode;
                        if (responseCode == 401) {
                            errorDetail += "\nCheck your ACCESS_TOKEN";
                            // Optionally refresh token here
                            getNewToken();
                        }
                        Toast.makeText(this, "Error: " + errorDetail, Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Exception: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e("API", "Error fetching telemetry", e);
                });
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private boolean isTokenValid(String token) {
        if (!token.startsWith("Bearer ")) return false;

        try {
            String jwt = token.substring(7);
            String[] parts = jwt.split("\\.");
            JSONObject payload = new JSONObject(new String(Base64.decode(parts[1], Base64.URL_SAFE)));
            long exp = payload.getLong("exp") * 1000; // Convert to milliseconds
            return System.currentTimeMillis() < exp;
        } catch (Exception e) {
            return false;
        }
    }

    private void fetchDataWithAuth() {
        String currentToken = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                .getString("access_token", ACCESS_TOKEN);

        if (!isTokenValid(currentToken)) {
            getNewToken();
            return;
        }

        fetchThingsBoardTelemetry(); // Proceed with valid token
    }

    // Helper method to read error streams
    private String readStream(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return response.toString();
    }

    private void showIpConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Configure Server IP");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("192.168.0.103:8080");
        input.setText(THINGSBOARD_IP);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newIp = input.getText().toString().trim();
            if (!newIp.isEmpty()) {
                SharedPreferences.Editor editor = getSharedPreferences("AppPrefs", MODE_PRIVATE).edit();
                editor.putString("thingsboard_ip", newIp);
                editor.apply();
                Toast.makeText(this, "IP saved. Changes will take effect on next connection.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // Helper method to get current IP
    private String getCurrentIp() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        return prefs.getString("thingsboard_ip", THINGSBOARD_IP);
    }

    private interface DiscoveryCallback {
        void onIpDiscovered(String ip);
    }



    private void updateUIWithTelemetry(JSONObject telemetryData) {
        runOnUiThread(() -> {
            // Update soil moisture
            if (telemetryData.has("moisture")) {
                try {
                    JSONArray soilData = telemetryData.getJSONArray("moisture");
                    float value = parseSensorValue(soilData.getJSONObject(0).get("value"));
                    soilMoistureText.setText(String.format("%.1f%%", value));
                    progSoil.setProgress((int) value);

                    // Highlight if moisture is below threshold
                    try {
                        String thresholdStr = minSoilMoistureLevel.getText().toString();
                        float threshold = Float.parseFloat(thresholdStr.replaceAll("[^\\d.]", ""));
                        soilMoistureText.setTextColor(ContextCompat.getColor(this,
                                value < threshold ? R.color.red : R.color.dg));
                    } catch (NumberFormatException e) {
                        Log.e("PlantDetails", "Error parsing threshold", e);
                    }
                } catch (Exception e) {
                    Log.e("PlantDetails", "Error processing moisture data", e);
                }
            }

            // Update temperature
            if (telemetryData.has("temperature")) {
                try {
                    JSONArray tempData = telemetryData.getJSONArray("temperature");
                    float value = parseSensorValue(tempData.getJSONObject(0).get("value"));
                    humidityText.setText(String.format("%.1f°C", value));
                    progHum.setProgress((int) value);
                } catch (Exception e) {
                    Log.e("PlantDetails", "Error processing temperature data", e);
                }
            }

            // Update pH level
            if (telemetryData.has("ph")) {
                try {
                    JSONArray phData = telemetryData.getJSONArray("ph");
                    float value = parseSensorValue(phData.getJSONObject(0).get("value"));
                    phLevelText.setText(String.format("%.1f", value));
                    progPh.setProgress((int) value);
                } catch (Exception e) {
                    Log.e("PlantDetails", "Error processing pH data", e);
                }
            }

            // Update conductivity (water level)
            if (telemetryData.has("conductivity")) {
                try {
                    JSONArray waterData = telemetryData.getJSONArray("conductivity");
                    int value = (int) parseSensorValue(waterData.getJSONObject(0).get("value"));
                    waterLevelText.setText(value + " µS/cm");
                    progWater.setProgress(value);
                } catch (Exception e) {
                    Log.e("PlantDetails", "Error processing conductivity data", e);
                }
            }

            // Update NPK values
            if (telemetryData.has("nitrogen")) {
                try {
                    JSONArray nitroData = telemetryData.getJSONArray("nitrogen");
                    int value = (int) parseSensorValue(nitroData.getJSONObject(0).get("value"));
                    nitrogenText.setText(value + " mg/kg");
                } catch (Exception e) {
                    Log.e("PlantDetails", "Error processing nitrogen data", e);
                }
            }

            if (telemetryData.has("phosphorous")) {
                try {
                    JSONArray phosData = telemetryData.getJSONArray("phosphorous");
                    int value = (int) parseSensorValue(phosData.getJSONObject(0).get("value"));
                    phosphorusText.setText(value + " mg/kg");
                } catch (Exception e) {
                    Log.e("PlantDetails", "Error processing phosphorus data", e);
                }
            }

            if (telemetryData.has("potassium")) {
                try {
                    JSONArray potaData = telemetryData.getJSONArray("potassium");
                    int value = (int) parseSensorValue(potaData.getJSONObject(0).get("value"));
                    potassiumText.setText(value + " mg/kg");
                } catch (Exception e) {
                    Log.e("PlantDetails", "Error processing potassium data", e);
                }
            }

            if (telemetryData.has("pump_status")) {
                try {
                    JSONArray pumpData = telemetryData.getJSONArray("pump_status");
                    String status = pumpData.getJSONObject(0).getString("value");
                    connectionStatusText.setText(status.equals("ON") ?
                            "Watering in progress..." :
                            (bluetoothSocket != null && bluetoothSocket.isConnected()) ? "Connected" : "Disconnected");
                    connectionStatusText.setTextColor(ContextCompat.getColor(this,
                            status.equals("ON") ? R.color.sensor_button_bg :
                                    (bluetoothSocket != null && bluetoothSocket.isConnected()) ? R.color.green : R.color.red));
                } catch (Exception e) {
                    Log.e("PlantDetails", "Error processing pump status", e);
                }
            }
        });
    }

    private float parseSensorValue(Object value) throws NumberFormatException {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        String strValue = value.toString();
        // Remove any non-numeric characters except decimal point and minus sign
        return Float.parseFloat(strValue.replaceAll("[^\\d.-]", ""));
    }


    private void sendThresholdToDevice(float threshold) {
        if (bluetoothSocket == null || !bluetoothSocket.isConnected()) {
            Toast.makeText(this, "Not connected to ESP32", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                String command = "THRESHOLD:" + threshold + "\n";
                outputStream.write(command.getBytes());
                outputStream.flush();

                // Wait for response
                InputStream inputStream = bluetoothSocket.getInputStream();
                byte[] buffer = new byte[1024];
                int bytes = inputStream.read(buffer);
                String response = new String(buffer, 0, bytes).trim();

                runOnUiThread(() -> {
                    if (response.startsWith("THRESHOLD_ACK:")) {
                        float updatedThreshold = Float.parseFloat(response.substring(14));
                        minSoilMoistureLevel.setText(String.valueOf(updatedThreshold));
                        Toast.makeText(this, "Threshold updated to " + updatedThreshold + "%",
                                Toast.LENGTH_SHORT).show();
                    } else if (response.startsWith("THRESHOLD_ERROR:")) {
                        Toast.makeText(this, response.substring(16), Toast.LENGTH_LONG).show();
                    }
                });
            } catch (IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Error sending threshold: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
            }
        }).start();
    }






    // Image Handling Methods
    private void openImagePicker() {
        imagePickerLauncher.launch("image/*");
    }

    private void uploadImageToFirebase() {
        if (selectedImageUri == null) {
            Log.e("FirebaseUpload", "❌ No image selected for upload");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String plantId = getIntent().getStringExtra("plantId");

        if (plantId == null || plantId.isEmpty()) {
            Log.e("FirebaseUpload", "❌ Plant ID is null or empty. Cannot upload image.");
            progressBar.setVisibility(View.GONE);
            return;
        }

        StorageReference storageRef = FirebaseStorage.getInstance().getReference("plant_images/" + userId + "/" + plantId);

        storageRef.putFile(selectedImageUri)
                .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    Log.d("FirebaseUpload", "✅ Image uploaded successfully: " + uri.toString());
                    updateFirestoreWithImage(uri.toString(), plantId);

                    Glide.with(this)
                            .load(uri.toString())
                            .centerCrop()
                            .into(plantImage);

                    progressBar.setVisibility(View.GONE);
                }))
                .addOnFailureListener(e -> {
                    Log.e("FirebaseUpload", "❌ Image upload failed: " + e.getMessage());
                    progressBar.setVisibility(View.GONE);
                });
    }

    private void updateFirestoreWithImage(String imageUrl, String plantId) {
        FirebaseFirestore.getInstance().collection("plants").document(plantId)
                .update("actualImageUrl", imageUrl)
                .addOnSuccessListener(aVoid -> {
                    Log.d("Firestore", "✅ Image updated successfully");
                    fetchActualImageFromFirestore(plantId);
                })
                .addOnFailureListener(e -> Log.e("Firestore", "❌ Failed to update image: " + e.getMessage()));
    }

    private void fetchActualImageFromFirestore(String plantId) {
        FirebaseFirestore.getInstance().collection("plants").document(plantId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && documentSnapshot.contains("actualImageUrl")) {
                        String imageUrl = documentSnapshot.getString("actualImageUrl");
                        Log.d("Firestore", "📸 Retrieved actualImage: " + imageUrl);
                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(imageUrl)
                                    .centerCrop()
                                    .into(plantImage);
                        }
                    } else {
                        Log.e("Firestore", "⚠️ No actual image found for this plant");
                    }
                })
                .addOnFailureListener(e -> Log.e("Firestore", "❌ Failed to fetch actual image: " + e.getMessage()));
    }

    // Schedule Methods
    private void populateSchedules(ArrayList<String> schedules) {
        scheduleContainer.removeAllViews();

        for (String schedule : schedules) {
            Log.d("PlantDetails", "Adding schedule: " + schedule);

            Chip chip = new Chip(this);
            chip.setText(schedule);
            chip.setChipBackgroundColorResource(R.color.lg);
            chip.setTextColor(getResources().getColor(R.color.dg));
            chip.setTextSize(14);
            chip.setCloseIconVisible(false);

            FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(
                    FlexboxLayout.LayoutParams.WRAP_CONTENT,
                    FlexboxLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 3, 10, 0);
            chip.setLayoutParams(params);

            scheduleContainer.addView(chip);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Close Bluetooth connection
        try {
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
                bluetoothSocket = null;
            }
            connectedDevice = null;
        } catch (IOException e) {
            Log.e("Bluetooth", "Error closing Bluetooth connection", e);
        }

        // Stop any discovery and data refresh
        stopBluetoothDiscovery();
        stopDataRefreshCycle();

        // Unregister receivers
        try {
            unregisterReceiver(pairingReceiver);
            unregisterReceiver(bluetoothStateReceiver);
        } catch (IllegalArgumentException e) {
            Log.e("Receiver", "Receiver not registered", e);
        }
    }
}