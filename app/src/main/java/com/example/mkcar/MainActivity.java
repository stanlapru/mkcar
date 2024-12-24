package com.example.mkcar;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements JoystickView.JoystickListener {

    // Global variables
    private boolean isLeftSignalOn = false;
    private boolean isRightSignalOn = false;
    private boolean isHeadlightsOn = false;
    private boolean isEmergencyOn = false;
    private boolean isHornOn = false;

    private TextView coordinatesText;
    private TextView pressedButtonsText;
    private static final int REQUEST_ENABLE_BT = 1;

    //We will use a Handler to get the BT Connection status
    public static Handler handler;
    BluetoothDevice arduinoBTModule = null;
    UUID arduinoUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // default uuid

    private OutputStream outputStream; // For Bluetooth communication

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        JoystickView joystickView = findViewById(R.id.joystickView);
        coordinatesText = findViewById(R.id.coordinatesText);
        pressedButtonsText = findViewById(R.id.pressedButtonsText);
        joystickView.setJoystickListener(this);

        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        TextView btDevices = findViewById(R.id.btDevices);
        TextView btError = findViewById(R.id.btError);
        Button connectToDevice = (Button) findViewById(R.id.connectToDevice);
        Button searchDevices = (Button) findViewById(R.id.searchDevices);
        Button clearValues = (Button) findViewById(R.id.refresh);
        Button btnLeftSignal = findViewById(R.id.leftSignalButton);
        Button btnHeadlights = findViewById(R.id.headlightsButton);
        Button btnRightSignal = findViewById(R.id.rightSignalButton);
        Button btnEmergency = findViewById(R.id.emergencySignalButton);
        Button btnHorn = findViewById(R.id.hornButton);

        // Set up button listeners
        btnLeftSignal.setOnClickListener(v -> {
            isLeftSignalOn = !isLeftSignalOn;
            isRightSignalOn = false;
            updatePressedButtons();
        });
        btnHeadlights.setOnClickListener(v -> {
            isHeadlightsOn = !isHeadlightsOn;
            updatePressedButtons();
        });
        btnRightSignal.setOnClickListener(v -> {
            isRightSignalOn = !isRightSignalOn;
            isLeftSignalOn = false;
            updatePressedButtons();
        });
        btnEmergency.setOnClickListener(v -> {
            isEmergencyOn = !isEmergencyOn;
            updatePressedButtons();
        });
        btnHorn.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                isHornOn = true;
                updatePressedButtons();
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                isHornOn = false;
                updatePressedButtons();
            }
            return true;
        });

        // Using a handler to update the interface in case of an error connecting to the BT device
        // My idea is to show handler vs RxAndroid
        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 0: // Error
                        String arduinoMsg = msg.obj.toString(); // Read message from Arduino
                        break;
                }
            }
        };

        // Set a listener event on a button to clear the texts
        clearValues.setOnClickListener(view -> btDevices.setText(""));


        // Create an Observable from RxAndroid
        // The code will be executed when an Observer subscribes to the the Observable
        final Observable<String> connectToBTObservable = Observable.create(emitter -> {
            //Call the constructor of the ConnectThread class
            //Passing the Arguments: an Object that represents the BT device,
            // the UUID and then the handler to update the UI
            ConnectThread connectThread = new ConnectThread(arduinoBTModule, arduinoUUID, handler);
            connectThread.run();
            //Check if Socket connected
            if (connectThread.getMmSocket().isConnected()) {
                //The pass the Open socket as arguments to call the constructor of ConnectedThread
                ConnectedThread connectedThread = new ConnectedThread(connectThread.getMmSocket());
                connectedThread.run();
                if(connectedThread.getValueRead()!=null)
                {
                    // If we have read a value from the Arduino
                    // we call the onNext() function
                    //This value will be observed by the observer
                    emitter.onNext(connectedThread.getValueRead());
                }
                //We just want to stream 1 value, so we close the BT stream
                connectedThread.cancel();
            }
            // SystemClock.sleep(5000); // simulate delay
            //Then we close the socket connection
            connectThread.cancel();
            //We could Override the onComplete function
            emitter.onComplete();

        });

        connectToDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (arduinoBTModule != null) {
                    //We subscribe to the observable until the onComplete() is called
                    //We also define control the thread management with
                    // subscribeOn:  the thread in which you want to execute the action
                    // observeOn: the thread in which you want to get the response
                    connectToBTObservable.
                            observeOn(AndroidSchedulers.mainThread()).
                            subscribeOn(Schedulers.io()).
                            subscribe(valueRead -> {
                                //valueRead returned by the onNext() from the Observable
                                //We just scratched the surface with RxAndroid
                            });

                }
            }
        });

        searchDevices.setOnClickListener(new View.OnClickListener() {
            //Display all the linked BT Devices
            @Override
            public void onClick(View view) {
                //Check if the phone supports BT
                if (bluetoothAdapter == null) {
                    // Device doesn't support Bluetooth
                    btError.setText("Устройство не поддерживает Bluetooth.");
                } else {
                    //Check BT enabled. If disabled, we ask the user to enable BT
                    if (!bluetoothAdapter.isEnabled()) {
                        btError.setText("Bluetooth выключен.");
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            btError.setText("Нет разрешения на использование Bluetooth.");
                            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                        }
                    } else {
                        btError.setText("");
                    }
                    String btDevicesString="";
                    Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

                    if (pairedDevices.size() > 0) {
                        // There are paired devices. Get the name and address of each paired device.
                        for (BluetoothDevice device: pairedDevices) {
                            String deviceName = device.getName();
                            String deviceHardwareAddress = device.getAddress(); // MAC address
                            //We append all devices to a String that we will display in the UI
                            btDevicesString=btDevicesString+deviceName+" ["+deviceHardwareAddress+"]\n";
                            //If we find the HC 05 device (the Arduino BT module)
                            //We assign the device value to the Global variable BluetoothDevice
                            //We enable the button "Connect to HC 05 device"
                            if (deviceName.equals("HC-06")) {
                                if (device.getUuids() != null && device.getUuids().length > 0) {
                                    arduinoUUID = device.getUuids()[0].getUuid();
                                    arduinoBTModule = device;
                                    //HC-06 Found, enabling the button to read results
                                    connectToDevice.setEnabled(true);
                                    break;
                                }
                            }
                            btDevices.setText(btDevicesString);
                        }
                    }
                }
            }
        });
        setupRxDataStream();
    }

    private void updatePressedButtons() {
        StringBuilder pressedButtons = new StringBuilder("Нажато: ");

        if (isLeftSignalOn) pressedButtons.append("Влево ");
        if (isRightSignalOn) pressedButtons.append("Вправо ");
        if (isHeadlightsOn) pressedButtons.append("Фары ");
        if (isEmergencyOn) pressedButtons.append("Аварийка ");
        if (isHornOn) pressedButtons.append("Гудок ");

        pressedButtonsText.setText(pressedButtons.toString());
    }

    private void setupRxDataStream() {
        Observable<String> joystickObservable = Observable.create(emitter -> {
            JoystickView.JoystickListener joystickListener = (xPercent, yPercent, id) -> {
                int x = (int) (xPercent * 100);
                int y = (int) (yPercent * 100);
                emitter.onNext("X:" + x + " Y:" + y);
            };
        });

        Observable<String> buttonStateObservable = Observable.create(emitter -> {
            while (true) {
                String buttonState = "L:" + isLeftSignalOn + " R:" + isRightSignalOn +
                        " H:" + isHeadlightsOn + " E:" + isEmergencyOn + " HO:" + isHornOn;
                emitter.onNext(buttonState);
                Thread.sleep(100); // Throttle the button state updates
            }
        });

        // Combine joystick and button states
        Observable.combineLatest(
                        joystickObservable,
                        buttonStateObservable,
                        (joystickData, buttonData) -> joystickData + " " + buttonData
                ).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::sendDataToArduino);
    }

    private void sendDataToArduino(String data) {
        if (outputStream != null) {
            try {
                outputStream.write((data + "\n").getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onJoystickMoved(float xPercent, float yPercent, int id) {
        // Translate normalized coordinates to device-friendly values (e.g., -100 to 100).
        int x = (int) (xPercent * 100);
        int y = (int) (yPercent * 100);

        coordinatesText.setText("X: " + x + " Y: " + y);

        // Send these values to Arduino via Bluetooth
        sendJoystickCoordinates(x, y);
    }

    private void sendJoystickCoordinates(int x, int y) {
        String message = "X:" + x + " Y:" + y;
        // Write this message to the Bluetooth output stream
        //outputStream.write(message.getBytes());
    }
}
