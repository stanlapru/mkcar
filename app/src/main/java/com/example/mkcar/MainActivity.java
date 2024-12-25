package com.example.mkcar;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements JoystickView.JoystickListener{

    private final String TAG = MainActivity.class.getSimpleName();

    private static final UUID BT_MODULE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier

    // #defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
    public final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status

    // GUI Components
    private TextView mBluetoothStatus;
    private TextView coordinatesText;

    private BluetoothAdapter mBTAdapter;
    private ArrayAdapter<String> mBTArrayAdapter;

    private Handler mHandler; // Our main handler that will receive callback notifications
    private ConnectedThread mConnectedThread; // bluetooth background worker thread to send and receive data
    private BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path

    private boolean isLeftSignalOn = false;
    private boolean isRightSignalOn = false;
    private boolean isHeadlightsOn = false;
    private boolean isEmergencyOn = false;
    private boolean isHornOn = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBluetoothStatus =  findViewById(R.id.bluetooth_status);
        Button mScanBtn = findViewById(R.id.scan);
        Button mOffBtn = findViewById(R.id.off);
        Button mDiscoverBtn = findViewById(R.id.discover);
        Button mListPairedDevicesBtn = findViewById(R.id.paired_btn);
        CheckBox ledLeft = findViewById(R.id.checkbox_left);
        CheckBox ledRight = findViewById(R.id.checkbox_right);
        CheckBox ledHeadlights = findViewById(R.id.checkbox_headlights);
        CheckBox ledEmergency = findViewById(R.id.checkbox_emergency);
        CheckBox horn = findViewById(R.id.checkbox_horn);
        coordinatesText = findViewById(R.id.coordinatesText);

        JoystickView joystickView = findViewById(R.id.joystickView);
        joystickView.setJoystickListener(this);

        mBTArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        mBTAdapter = BluetoothAdapter.getDefaultAdapter(); // get a handle on the bluetooth radio

        ListView mDevicesListView = findViewById(R.id.devices_list_view);
        mDevicesListView.setAdapter(mBTArrayAdapter); // assign model to view
        mDevicesListView.setOnItemClickListener(mDeviceClickListener);

        // Ask for location permission if not already allowed
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);


        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {

                if (msg.what == CONNECTING_STATUS) {
                    if (msg.arg1 == 1)
                        mBluetoothStatus.setText("Bluetooth включен" + msg.obj);
                    else
                        mBluetoothStatus.setText("Bluetooth не удалось подключить");
                }
            }
        };

        if (mBTArrayAdapter == null) {
            // Device does not support Bluetooth
            mBluetoothStatus.setText("Bluetooth не поддерживается");
            Toast.makeText(getApplicationContext(), "Bluetooth устройство не найдено", Toast.LENGTH_SHORT).show();
        } else {

            ledLeft.setOnClickListener(v -> {
                isLeftSignalOn = !isLeftSignalOn;
                isRightSignalOn = false;
                if (mConnectedThread != null) //First check to make sure thread created
                    if (isLeftSignalOn) mConnectedThread.write("LEFT:1");
                    else mConnectedThread.write("LEFT:0");
            });

            ledRight.setOnClickListener(v -> {
                isRightSignalOn = !isRightSignalOn;
                isLeftSignalOn = false;
                if (mConnectedThread != null) //First check to make sure thread created
                    if (isRightSignalOn) mConnectedThread.write("RGHT:1");
                    else mConnectedThread.write("RGHT:0");
            });

            ledHeadlights.setOnClickListener(v -> {
                isHeadlightsOn = !isHeadlightsOn;
                if (mConnectedThread != null) //First check to make sure thread created
                    if (isHeadlightsOn) mConnectedThread.write("HEAD:1");
                    else mConnectedThread.write("HEAD:0");
            });

            ledEmergency.setOnClickListener(v -> {
                isEmergencyOn = !isEmergencyOn;
                if (mConnectedThread != null) //First check to make sure thread created
                    if (isEmergencyOn) mConnectedThread.write("EMER:1");
                    else mConnectedThread.write("EMER:0");
            });

            horn.setOnClickListener(v -> {
                isHornOn = !isHornOn;
                if (mConnectedThread != null) //First check to make sure thread created
                    if (isHornOn) mConnectedThread.write("HORN:1");
                    else mConnectedThread.write("HORN:0");
            });


            mScanBtn.setOnClickListener(v -> bluetoothOn());
            mOffBtn.setOnClickListener(v -> bluetoothOff());
            mListPairedDevicesBtn.setOnClickListener(v -> listPairedDevices());
            mDiscoverBtn.setOnClickListener(v -> discover());
        }
    }

    @Override
    public void onJoystickMoved(float xPercent, float yPercent, int id) {
        // Translate normalized coordinates to device-friendly values (e.g., -100 to 100).
        int x = (int) (xPercent * 100);
        int y = (int) (yPercent * 100);

        coordinatesText.setText("X: " + x + " Y: " + y);
        if (mConnectedThread != null) mConnectedThread.write(coordinatesText.toString());
    }


    private void bluetoothOn() {
        if (!mBTAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            mBluetoothStatus.setText("Bluetooth включен");
            Toast.makeText(getApplicationContext(), "Bluetooth включен", Toast.LENGTH_SHORT).show();

        } else {
            Toast.makeText(getApplicationContext(), "Bluetooth уже включен", Toast.LENGTH_SHORT).show();
        }
    }

    // Enter here after user selects "yes" or "no" to enabling radio
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent Data) {
        // Check which request we're responding to
        super.onActivityResult(requestCode, resultCode, Data);
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected.
                mBluetoothStatus.setText("Bluetooth включен");
            } else
                mBluetoothStatus.setText("Bluetooth выключен");
        }
    }

    private void bluetoothOff() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mBTAdapter.disable(); // turn off
        mBluetoothStatus.setText("Bluetooth выключен");
        Toast.makeText(getApplicationContext(), "Bluetooth turned Off", Toast.LENGTH_SHORT).show();
    }

    private void discover() {
        // Check if the device is already discovering
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (mBTAdapter.isDiscovering()) {
            mBTAdapter.cancelDiscovery();
            Toast.makeText(getApplicationContext(), "Поиск остановлен", Toast.LENGTH_SHORT).show();
        } else {
            if (mBTAdapter.isEnabled()) {
                mBTArrayAdapter.clear(); // clear items
                mBTAdapter.startDiscovery();
                Toast.makeText(getApplicationContext(), "Поиск начат", Toast.LENGTH_SHORT).show();
                registerReceiver(blReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
            } else {
                Toast.makeText(getApplicationContext(), "Bluetooth не включен", Toast.LENGTH_SHORT).show();
            }
        }
    }

    final BroadcastReceiver blReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // add the name to the list
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                assert device != null;
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                mBTArrayAdapter.notifyDataSetChanged();
            }
        }
    };

    private void listPairedDevices() {
        mBTArrayAdapter.clear();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Set<BluetoothDevice> mPairedDevices = mBTAdapter.getBondedDevices();
        if (mBTAdapter.isEnabled()) {
            // put it's one to the adapter
            for (BluetoothDevice device : mPairedDevices)
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());

            Toast.makeText(getApplicationContext(), "Показать связанные", Toast.LENGTH_SHORT).show();
        } else
            Toast.makeText(getApplicationContext(), "Bluetooth не включен", Toast.LENGTH_SHORT).show();
    }

    private final AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            if (!mBTAdapter.isEnabled()) {
                Toast.makeText(getBaseContext(), "Bluetooth не включен", Toast.LENGTH_SHORT).show();
                return;
            }

            mBluetoothStatus.setText("Подключение...");
            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) view).getText().toString();
            final String address = info.substring(info.length() - 17);
            final String name = info.substring(0, info.length() - 17);

            // Spawn a new thread to avoid blocking the GUI one
            new Thread() {
                @Override
                public void run() {
                    boolean fail = false;

                    BluetoothDevice device = mBTAdapter.getRemoteDevice(address);

                    try {
                        mBTSocket = createBluetoothSocket(device);
                    } catch (IOException e) {
                        fail = true;
                        Toast.makeText(getBaseContext(), "Не удалось создать сокет", Toast.LENGTH_SHORT).show();
                    }
                    // Establish the Bluetooth socket connection.
                    try {

                        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                        mBTSocket.connect();
                    } catch (IOException e) {
                        try {
                            fail = true;
                            mBTSocket.close();
                            mHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                    .sendToTarget();
                        } catch (IOException e2) {
                            //insert code to deal with this
                            Toast.makeText(getBaseContext(), "Не удалось создать сокет", Toast.LENGTH_SHORT).show();
                        }
                    }
                    if (!fail) {
                        mConnectedThread = new ConnectedThread(mBTSocket, mHandler);
                        mConnectedThread.start();

                        mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                                .sendToTarget();
                    }
                }
            }.start();
        }
    };

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
            return (BluetoothSocket) m.invoke(device, BT_MODULE_UUID);
        } catch (Exception e) {
            Log.e(TAG, "Could not create Insecure RFComm Connection", e);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return  null;
        }
        return device.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
    }
}

