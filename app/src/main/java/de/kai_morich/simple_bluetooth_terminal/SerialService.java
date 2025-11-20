package de.kai_morich.simple_bluetooth_terminal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayDeque;

import java.util.Map;

/**
 * create notification and queue serial data while activity is not in the foreground
 * use listener chain: SerialSocket -> SerialService -> UI fragment
 */
public class SerialService extends Service implements SerialListener {

    public interface SerialResponseCallback {
        void onResponse(byte[] data);
        void onError(Exception e);
    }

    class SerialBinder extends Binder {
        SerialService getService() { return SerialService.this; }
    }

    private final IBinder binder;

    private SerialSocket socket;
    private boolean connected;
    private final StringBuilder responseBuffer = new StringBuilder();

    private String currentDeviceAddress = null;

    private SerialResponseCallback pendingCallback = null;


    private volatile boolean obdRunning = false;
    private int delaySeconds = 20;

    /**
     * Lifecylce
     */
    public SerialService() {
        binder = new SerialBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initNotification();
    }

    @Override
    public void onDestroy() {
        cancelNotification();
        disconnect();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Api
     */

    public void connect(String deviceAddress) {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            logStatus("pending connection...");
            connected = true;
            socket = new SerialSocket(getApplicationContext(), device);
            socket.connect(this);

        } catch (Exception e) {
            logError("connect", e, 4);
            disconnect();
        }
    }

    public void disconnect() {
        StringWriter sw = new StringWriter();
        new Throwable().printStackTrace(new PrintWriter(sw));
        String fullMessage = "DISCONNECT CALLED" + "\n\nStackTrace:\n" + sw.toString();
        logStatus("disconnected: " + fullMessage);
        connected = false; // ignore data,errors while disconnecting
        pendingCallback = null;
        if(socket != null) {
            socket.disconnect();
            socket = null;
        }
    }

    public void write(byte[] data) throws IOException {
        if(!connected)
            throw new IOException("not connected");
        socket.write(data);
    }

    private void sendCommand(String str) throws IOException {
        byte[] bytes = (str + "\r").getBytes(Charset.forName("US-ASCII"));
        write(bytes);
    }

    public void sendAndReceive(String command, SerialResponseCallback callback) {
        if (!connected) {
            callback.onError(new IOException("Not connected"));
            return;
        }

        if (!obdRunning) {
            callback.onError(new IOException("USER stopped"));
            return;
        }

        // Salva il callback
        pendingCallback = callback;

        // Invia i dati
        try {
            sendCommand(command);
        } catch (Exception e) {
            callback.onError(e);
        }
    }


    private void initNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(Constants.NOTIFICATION_CHANNEL, "Background service", NotificationManager.IMPORTANCE_LOW);
            nc.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(nc);
        }
    }

    private void createNotification() {
        Intent disconnectIntent = new Intent()
                .setPackage(getPackageName())
                .setAction(Constants.INTENT_ACTION_DISCONNECT);
        Intent restartIntent = new Intent()
                .setClassName(this, Constants.INTENT_CLASS_MAIN_ACTIVITY)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent disconnectPendingIntent = PendingIntent.getBroadcast(this, 1, disconnectIntent, flags);
        PendingIntent restartPendingIntent = PendingIntent.getActivity(this, 1, restartIntent,  flags);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(getResources().getColor(R.color.colorPrimary))
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText(socket != null ? "Connected to "+socket.getName() : "Background Service")
                //.setContentIntent(restartPendingIntent)
                .setOngoing(true);
                //.addAction(new NotificationCompat.Action(R.drawable.ic_clear_white_24dp, "Disconnect", disconnectPendingIntent));
        // @drawable/ic_notification created with Android Studio -> New -> Image Asset using @color/colorPrimaryDark as background color
        // Android < API 21 does not support vectorDrawables in notifications, so both drawables used here, are created as .png instead of .xml
        Notification notification = builder.build();
        startForeground(Constants.NOTIFY_MANAGER_START_FOREGROUND_SERVICE, notification);
    }

    private void cancelNotification() {
        stopForeground(true);
    }

    private void createSecondaryNotification(String title, String message, int notificationId) {
        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(getResources().getColor(R.color.colorPrimary))
                .setContentTitle(title)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))  // allows multiple lines
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(false); // disappears when tapped

        // Send the notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(notificationId, builder.build());
            } else {
                // Optional: request the permission from the user
                //print(request permission)
            }
        } else {
            // For Android < 13, no permission needed
            notificationManager.notify(notificationId, builder.build());
        }
    }

    private void logError(String title, Exception e, int notificationId) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        createSecondaryNotification("error: " + title, sw.toString(), notificationId);
    }

    private void logStatus(String text) {
        createSecondaryNotification("status", text, 2);
    }

    private void logSuccess(String text) {
        logSuccess(text, 3);
    }

    private void logSuccess(String text, int notificationId) {
        createSecondaryNotification("success", text, notificationId);
    }



        /**
         * SerialListener
         */
    @Override
    public void onSerialConnect() {
        logStatus("connected");

        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> {
                    logStatus("Starting OBD init after delay...");
                    runObdInitSequence();
                }, 1000);
    }

    @Override
    public void onSerialConnectError(Exception e) {
        // handle error internally
        logError("onSerialConnectError", e, 8);
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        if (!connected) return;

        // 1️⃣ Append new chunk to buffer
        String chunk = new String(data, Charset.forName("US-ASCII"));
        responseBuffer.append(chunk);

        // 2️⃣ Check for terminator
        if (chunk.contains(">")) {
            String fullResponse = responseBuffer.toString();
            responseBuffer.setLength(0); // clear buffer for next command

            if (pendingCallback != null) {
                SerialResponseCallback cb = pendingCallback;
                pendingCallback = null;
                cb.onResponse(fullResponse.getBytes(Charset.forName("US-ASCII")));
            }
        }
    }

    @Override
    public void onSerialRead(ArrayDeque<byte[]> datas) { throw new UnsupportedOperationException(); }

    @Override
    public void onSerialIoError(Exception e) {
        logError("onSerialIoError", e, 5);
        disconnect();
    }

    public void runObdInitSequence() {
        String[] initCmds = {
                "ATD", "ATZ", "ATE0", "ATL0", "ATS0", "ATH1", "ATSTFF", "ATFE", "ATSP6", "ATCRA7EC"
        };

        try {
            sendObdCommandAtIndex(initCmds, 0);
        } catch (Exception e) {
            logError("runObdInitSequence", e, 7);
        }
    }

    private void sendObdCommandAtIndex(String[] cmds, int index) {
        if (index >= cmds.length) {
            logStatus("Initialization complete!");
            sendCommandATRV();
            return;
        }

        String cmd = cmds[index];
        //logStatus("Sending command: " + cmd);

        sendAndReceive(cmd, new SerialResponseCallback() {
            @Override
            public void onResponse(byte[] data) {
                String response = new String(data, Charset.forName("US-ASCII"));
                logStatus(cmd + " Response: " + response);
                sendObdCommandAtIndex(cmds, index + 1);
            }

            @Override
            public void onError(Exception e) {
                logError(cmd, e, 6);
                disconnect();
            }
        });
    }

    private void sendCommandATRV() {
        sendAndReceive("ATRV", new SerialResponseCallback() {
            @Override
            public void onResponse(byte[] data) {
                String response = new String(data, Charset.forName("US-ASCII"));
                logSuccess("ATRV Response: " + response, 30);
                sendParseCommand220101();
            }

            @Override
            public void onError(Exception e) {
                logError("220101", e, 21);
                disconnect();
            }
        });
    }

    private void sendParseCommand220101() {
        sendAndReceive("220101", new SerialResponseCallback() {
            @Override
            public void onResponse(byte[] data) {
                String response = new String(data, Charset.forName("US-ASCII"));
                logStatus("220101 Response: " + response);
                sendParseCommand220105(response);
            }

            @Override
            public void onError(Exception e) {
                logError("220101", e, 21);
                disconnect();
            }
        });
    }

    private void sendParseCommand220105(String resp220101) {
        sendAndReceive("220105", new SerialResponseCallback() {
            @Override
            public void onResponse(byte[] data) {
                String response = new String(data, Charset.forName("US-ASCII"));
                logStatus("220105 Response: " + response);
                disconnect();
                Map<String, Object> parsed220101 = ParserUtils.parse220101(resp220101);
                Map<String, Object> parsed220105 = ParserUtils.parse220105(response);
                logSuccess("220101:\n" + parsed220101 + "\n\n220105:\n" + parsed220105);

                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> {
                            if (obdRunning && currentDeviceAddress != null) {
                                connect(currentDeviceAddress);
                            }
                        }, delaySeconds * 1000);

            }

            @Override
            public void onError(Exception e) {
                logError("220105", e, 20);
                disconnect();
            }
        });
    }

    public void start(String devAddress) {
        if(!connected) {
            if (obdRunning) return;  // already running
            obdRunning = true;

            if (currentDeviceAddress == null) {
                createNotification();
            }

            currentDeviceAddress = devAddress;
            connect(devAddress);
        }
    }

    public void stop() {
        obdRunning = false;
        currentDeviceAddress = null;
        cancelNotification();
    }


    public int getDelay() {
        return delaySeconds;
    }

    public void setDelay(int number) {
        if(number < 5) number = 5;
        if(number > 86000) number = 86000;
        delaySeconds = number;
    }

}
