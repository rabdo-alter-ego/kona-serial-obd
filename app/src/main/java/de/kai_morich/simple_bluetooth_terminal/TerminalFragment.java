package de.kai_morich.simple_bluetooth_terminal;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private SerialService service;

    private TextView receiveText;
    private TextView sendText;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private final StringBuilder responseBuffer = new StringBuilder();
    private final Object responseLock = new Object();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        //View sendBtn = view.findViewById(R.id.send_btn);
        //sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));
        View sendSessionBtn = view.findViewById(R.id.btn_send_session);
        sendSessionBtn.setOnClickListener(v -> obdSession());
        View startSessionBtn = view.findViewById(R.id.btn_start_session);
        startSessionBtn.setOnClickListener(v -> startLoopObsSession());
        View stopSessionBtn = view.findViewById(R.id.btn_stop_session);
        stopSessionBtn.setOnClickListener(v -> stopLoopObsSession());


        View atdBtn = view.findViewById(R.id.btn_atd);
        atdBtn.setOnClickListener(v -> send("ATD"));
        View atzBtn = view.findViewById(R.id.btn_atz);
        atzBtn.setOnClickListener(v -> send("ATZ"));
        View ate0Btn = view.findViewById(R.id.btn_ate0);
        ate0Btn.setOnClickListener(v -> send("ATE0"));
        View atl0Btn = view.findViewById(R.id.btn_atl0);
        atl0Btn.setOnClickListener(v -> send("ATL0"));
        View ats0Btn = view.findViewById(R.id.btn_ats0);
        ats0Btn.setOnClickListener(v -> send("ATS0"));
        View ath1Btn = view.findViewById(R.id.btn_ath1);
        ath1Btn.setOnClickListener(v -> send("ATH1"));
        View atstffBtn = view.findViewById(R.id.btn_atstff);
        atstffBtn.setOnClickListener(v -> send("ATSTFF"));
        View atfeBtn = view.findViewById(R.id.btn_atfe);
        atfeBtn.setOnClickListener(v -> send("ATFE"));
        View atsp6Btn = view.findViewById(R.id.btn_atsp6);
        atsp6Btn.setOnClickListener(v -> send("ATSP6"));
        View atcra7ecBtn = view.findViewById(R.id.btn_atcra7ec);
        atcra7ecBtn.setOnClickListener(v -> send("ATCRA7EC"));

        View vibrate = view.findViewById(R.id.btn_vibrate);
        vibrate.setOnClickListener(v -> vibrateDevice(requireContext(), 1000));

        View btn220105 = view.findViewById(R.id.btn_soc);
        btn220105.setOnClickListener(v ->
                sendAndParseCommand("220105", 2000, new OBDParser() {
                    @Override
                    public Map<String, Object> parse(String data) {
                        return parse220105(data);
                    }
                })
        );

        View btn220101 = view.findViewById(R.id.btn_bms);
        btn220101.setOnClickListener(v ->
                sendAndParseCommand("220101", 2000, new OBDParser() {
                    @Override
                    public Map<String, Object> parse(String data) {
                        return parse220101(data);
                    }
                })
        );
        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            menu.findItem(R.id.backgroundNotification).setChecked(service != null && service.areNotificationsEnabled());
        } else {
            menu.findItem(R.id.backgroundNotification).setChecked(true);
            menu.findItem(R.id.backgroundNotification).setEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id == R.id.backgroundNotification) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!service.areNotificationsEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
                } else {
                    showNotificationSettings();
                }
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            byte[] bytes = (str + "\r").getBytes(Charset.forName("US-ASCII"));
            SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            getActivity().runOnUiThread(() -> receiveText.append(spn));
            service.write(bytes);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private String sendAndReceive(String command, int timeoutMs) throws Exception {
        if (connected != Connected.True) {
            throw new IllegalStateException("Not connected");
        }

        synchronized (responseLock) {
            responseBuffer.setLength(0); // pulizia buffer vecchio
        }

        send(command); // manda comando

        long start = System.currentTimeMillis();
        while (true) {
            synchronized (responseLock) {
                // DEBUG nella UI
                String bufferStr = responseBuffer.toString().replace("\r", "\\r").replace("\n", "\\n");
                getActivity().runOnUiThread(() -> receiveText.append("[DEBUG] Buffer attuale: " + bufferStr + "\n"));

                int promptIndex = responseBuffer.indexOf(">");
                getActivity().runOnUiThread(() -> receiveText.append("[DEBUG] promptIndex=" + promptIndex + "\n"));

                if (promptIndex >= 0) {
                    String full = responseBuffer.substring(0, promptIndex).trim();
                    getActivity().runOnUiThread(() ->
                            receiveText.append("[DEBUG] RETURN full='" + full.replace("\r","\\r").replace("\n","\\n") + "'\n")
                    );
                    responseBuffer.delete(0, promptIndex + 1);
                    return full;
                }

                long elapsed = System.currentTimeMillis() - start;
                if (elapsed >= timeoutMs) {
                    getActivity().runOnUiThread(() ->
                            receiveText.append("[DEBUG] TIMEOUT raggiunto dopo " + elapsed + "ms\n")
                    );
                    throw new Exception("Timeout waiting for response");
                }

                // aspetta che arrivi un nuovo chunk o un po’ di tempo per ricontrollare
                responseLock.wait(50);
            }
        }
    }


    private void obdSession() {
        // Initialization commands for ELM327
        List<String> initCommands = Arrays.asList(
                "ATD", "ATZ", "ATE0", "ATL0", "ATS0", "ATH1", "ATSTFF", "ATFE", "ATSP6", "ATCRA7EC"
        );

        // Step 1: Run initialization commands sequentially
        sendCommandListAsyncWithCallback(initCommands, 2000, () -> {
            // Step 2: Send 220101, then when done send 220105
            sendAndParseCommand("220101", 2000, new OBDParser() {
                @Override
                public Map<String, Object> parse(String data) {
                    Map<String, Object> parsed220101 = parse220101(data);

                    // Update UI or handle parsed data here if needed
                    getActivity().runOnUiThread(() -> {
                        receiveText.append("220101 parsed successfully\n");
                    });

                    // Step 3: Now send 220105
                    sendAndParseCommand("220105", 2000, new OBDParser() {
                        @Override
                        public Map<String, Object> parse(String data2) {
                            Map<String, Object> parsed220105 = parse220105(data2);

                            // Update UI or handle parsed data here if needed
                            getActivity().runOnUiThread(() -> {
                                receiveText.append("220105 parsed successfully\n");
                            });

                            return parsed220105;
                        }
                    });

                    return parsed220101;
                }
            });
        });
    }


    /**
     * Send a list of commands sequentially with a callback after all complete.
     */
    private void sendCommandListAsyncWithCallback(List<String> commands, int timeoutMs, Runnable callback) {
        executor.execute(() -> {
            StringBuilder allResults = new StringBuilder();
            for (String cmd : commands) {
                try {
                    String resp = sendAndReceive(cmd, timeoutMs);

                    if (resp == null || resp.trim().isEmpty() || resp.contains("ERROR") || resp.contains("?")) {
                        allResults.append("[").append(cmd).append("] => ERROR\n");
                    } else {
                        allResults.append("[").append(cmd).append("] => ").append(resp).append("\n");
                    }

                } catch (Exception e) {
                    allResults.append("[").append(cmd).append("] => EXCEPTION: ")
                            .append(e.getMessage()).append("\n");
                }
            }

            // Update UI with init responses
            String finalResults = allResults.toString();
            getActivity().runOnUiThread(() -> receiveText.append(finalResults));

            // Call the callback after all commands have completed
            if (callback != null) {
                callback.run();
            }
        });
    }


    public interface OBDParser {
        Map<String, Object> parse(String data);
    }

    private void sendAndParseCommand(String command, int timeoutMs, OBDParser parser) {
        executor.execute(() -> {
            try {
                // Send command and get raw response
                String response = sendAndReceive(command, timeoutMs);

                // Parse response using provided parser
                Map<String, Object> parsedData = parser.parse(response);

                // Convert parsed data to readable string
                StringBuilder display = new StringBuilder();
                display.append("Command: ").append(command).append("\n");
                if (parsedData.isEmpty()) {
                    display.append("Error parsing data or timeout\n");
                } else {
                    for (Map.Entry<String, Object> entry : parsedData.entrySet()) {
                        display.append(entry.getKey())
                                .append(": ")
                                .append(entry.getValue())
                                .append("\n");
                    }
                }
                display.append("\n");

                // Append to terminal safely on UI thread
                String finalDisplay = display.toString();
                getActivity().runOnUiThread(() -> receiveText.append(finalDisplay));

            } catch (Exception e) {
                e.printStackTrace();
                String errorMsg = "Command: " + command + " => EXCEPTION: " + e.getMessage() + "\n\n";
                getActivity().runOnUiThread(() -> receiveText.append(errorMsg));
            }
        });
    }


    private Map<String, Object> parse220101(String data) {
        Map<String, Object> parsedData = new HashMap<>();
        try {

            String firstBlock = "7EC21";
            String secondBlock = "7EC22";
            String thirdBlock = "7EC23";
            String fourthBlock = "7EC24";
            String fifthBlock = "7EC25";
            String sixthBlock = "7EC26";
            String seventhBlock = "7EC27";
            String eighthBlock = "7EC28";

            if (data.contains(firstBlock) && data.contains(secondBlock) && data.contains(thirdBlock)
                    && data.contains(fourthBlock) && data.contains(fifthBlock) && data.contains(sixthBlock)
                    && data.contains(seventhBlock) && data.contains(eighthBlock)) {

                // Extract blocks
                String extractedFirstData = data.substring(data.indexOf(firstBlock), data.indexOf(secondBlock)).replace(firstBlock, "");
                String extractedSecondData = data.substring(data.indexOf(secondBlock), data.indexOf(thirdBlock)).replace(secondBlock, "");
                String extractedThirdData = data.substring(data.indexOf(thirdBlock), data.indexOf(fourthBlock)).replace(thirdBlock, "");
                String extractedFourthData = data.substring(data.indexOf(fourthBlock), data.indexOf(fifthBlock)).replace(fourthBlock, "");
                String extractedFifthData = data.substring(data.indexOf(fifthBlock), data.indexOf(sixthBlock)).replace(fifthBlock, "");
                String extractedSixthData = data.substring(data.indexOf(sixthBlock), data.indexOf(seventhBlock)).replace(sixthBlock, "");
                String extractedSeventhData = data.substring(data.indexOf(seventhBlock), data.indexOf(eighthBlock)).replace(seventhBlock, "");
                String extractedEighthData = data.substring(data.indexOf(eighthBlock), data.indexOf(eighthBlock) + 18).replace(eighthBlock, "");

                // Charging bits (7th block)
                int chargingInt = Integer.parseInt(extractedSeventhData.substring(10, 12), 16);
                String chargingBits = String.format("%8s", Integer.toBinaryString(chargingInt)).replace(' ', '0');

                if (!extractedFirstData.isEmpty() && !extractedSecondData.isEmpty() && !extractedFourthData.isEmpty()) {

                    // Signed 8-bit temperature conversions
                    int batteryMinTemperature = Integer.parseInt(extractedSecondData.substring(10, 12), 16);
                    if (batteryMinTemperature >= 128) batteryMinTemperature -= 256;

                    int batteryMaxTemperature = Integer.parseInt(extractedSecondData.substring(8, 10), 16);
                    if (batteryMaxTemperature >= 128) batteryMaxTemperature -= 256;

                    int batteryInletTemperature = Integer.parseInt(extractedThirdData.substring(10, 12), 16);
                    if (batteryInletTemperature >= 128) batteryInletTemperature -= 256;

                    // Signed 16-bit battery current
                    int batteryCurrent = Integer.parseInt(extractedSecondData.substring(0, 2) + extractedSecondData.substring(2, 4), 16);
                    if (batteryCurrent >= 32768) batteryCurrent -= 65536;

                    // Cumulative energy charged
                    double cumulativeEnergyCharged = (
                            (Integer.parseInt(extractedSixthData.substring(0, 2), 16) << 24) +
                                    (Integer.parseInt(extractedSixthData.substring(2, 4), 16) << 16) +
                                    (Integer.parseInt(extractedSixthData.substring(4, 6), 16) << 8) +
                                    Integer.parseInt(extractedSixthData.substring(6, 8), 16)
                    ) / 10.0;

                    // Cumulative energy discharged
                    double cumulativeEnergyDischarged = (
                            (Integer.parseInt(extractedSixthData.substring(8, 10), 16) << 24) +
                                    (Integer.parseInt(extractedSixthData.substring(10, 12), 16) << 16) +
                                    (Integer.parseInt(extractedSixthData.substring(12, 14), 16) << 8) +
                                    Integer.parseInt(extractedSeventhData.substring(0, 2), 16)
                    ) / 10.0;

                    // Charging flags
                    int charging = (chargingBits.charAt(4) == '1' && chargingBits.charAt(5) == '0') ? 1 : 0;
                    int normalChargePort = (chargingBits.charAt(1) == '1' && extractedFirstData.substring(12, 14).equals("03")) ? 1 : 0;
                    int rapidChargePort = (chargingBits.charAt(1) == '1' && !extractedFirstData.substring(12, 14).equals("03")) ? 1 : 0;

                    // Fill parsed data
                    parsedData.put("SOC_BMS", Integer.parseInt(extractedFirstData.substring(2, 4), 16) / 2.0);
                    parsedData.put("DC_BATTERY_VOLTAGE", ((Integer.parseInt(extractedSecondData.substring(4, 6), 16) << 8)
                            + Integer.parseInt(extractedSecondData.substring(6, 8), 16)) / 10.0);
                    parsedData.put("CHARGING", charging);
                    parsedData.put("NORMAL_CHARGE_PORT", normalChargePort);
                    parsedData.put("RAPID_CHARGE_PORT", rapidChargePort);
                    parsedData.put("BATTERY_MIN_TEMPERATURE", batteryMinTemperature);
                    parsedData.put("BATTERY_MAX_TEMPERATURE", batteryMaxTemperature);
                    parsedData.put("BATTERY_INLET_TEMPERATURE", batteryInletTemperature);
                    parsedData.put("DC_BATTERY_CURRENT", batteryCurrent * 0.1);
                    parsedData.put("CUMULATIVE_ENERGY_CHARGED", cumulativeEnergyCharged);
                    parsedData.put("CUMULATIVE_ENERGY_DISCHARGED", cumulativeEnergyDischarged);
                    parsedData.put("AUX_BATTERY_VOLTAGE", Integer.parseInt(extractedFourthData.substring(10, 12), 16) / 10.0);

                    // Battery power in kW
                    parsedData.put("DC_BATTERY_POWER", (batteryCurrent * 0.1) * ((Integer.parseInt(extractedSecondData.substring(4, 6), 16) << 8)
                            + Integer.parseInt(extractedSecondData.substring(6, 8), 16)) / 1000.0);
                }
            }
        } catch (Exception e) {
            e.printStackTrace(); // optional: log parsing errors
        }
        return parsedData;
    }

    // --- 220105 parser (SOC + SOH) ---
    public static Map<String, Object> parse220105(String data) {
        Map<String, Object> parsed = new HashMap<>();
        try {
            String fourthBlock = "7EC24";
            String fifthBlock = "7EC25";

            if (data.contains(fourthBlock) && data.contains(fifthBlock) && data.contains("7EC26")) {
                String extractedFourthData = extract(data, fourthBlock, fifthBlock);
                String extractedFifthData = extract(data, fifthBlock, "7EC26");

                if (!extractedFourthData.isEmpty() && !extractedFifthData.isEmpty()) {
                    parsed.put("SOC_DISPLAY", Integer.parseInt(extractedFifthData.substring(0, 2), 16) / 2.0);
                    parsed.put("SOH", ((Integer.parseInt(extractedFourthData.substring(2, 4), 16) << 8) +
                            Integer.parseInt(extractedFourthData.substring(4, 6), 16)) / 10.0);
                }
            }
        } catch (Exception e) {
            parsed.put("error", "Parse 220105 error: " + e.getMessage());
        }
        return parsed;
    }

    private static String extract(String data, String start, String end) {
        return data.substring(data.indexOf(start) + start.length(), data.indexOf(end));
    }

    private void startLoopObsSession() {

    }

    private void stopLoopObsSession() {

    }

    private void receive(ArrayDeque<byte[]> datas) {
        SpannableStringBuilder spn = new SpannableStringBuilder();
        for (byte[] data : datas) {
            String msg = new String(data);
            spn.append(TextUtil.toCaretString(msg, true));
        }
        getActivity().runOnUiThread(() -> receiveText.append(spn));    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    /*
     * starting with Android 14, notifications are not shown in notification bar by default when App is in background
     */

    private void showNotificationSettings() {
        Intent intent = new Intent();
        intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
        intent.putExtra("android.provider.extra.APP_PACKAGE", getActivity().getPackageName());
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(Arrays.equals(permissions, new String[]{Manifest.permission.POST_NOTIFICATIONS}) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !service.areNotificationsEnabled())
            showNotificationSettings();
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {

        String chunk = new String(data, Charset.forName("US-ASCII")).replace("\r", "").replace("\n", "");
        // --- DEBUG: visualizza tutti i byte ricevuti in esadecimale ---
        StringBuilder hex = new StringBuilder();
        for (byte b : data) {
            hex.append(String.format("%02X ", b));
        }
        receiveText.append("[DEBUG] Chunk ricevuto (hex): " + hex.toString() + "\n");
        receiveText.append("[DEBUG] Chunk ricevuto (str): " + chunk.replace("\r", "\\r").replace("\n", "\\n") + "\n");

        synchronized (responseLock) {
            responseBuffer.append(chunk);
            responseLock.notifyAll(); // wake up anyone waiting
        }

        ArrayDeque<byte[]> datas = new ArrayDeque<>();
        datas.add(data);
        receive(datas);
    }

    @Override
    public void onSerialRead(ArrayDeque<byte[]> datas) {
        StringBuilder combinedHex = new StringBuilder();
        StringBuilder combinedStr = new StringBuilder();

        synchronized (responseLock) {
            for (byte[] data : datas) {
                // converti chunk in stringa ASCII e pulisci \r \n
                String chunk = new String(data, Charset.forName("US-ASCII")).replace("\r", "").replace("\n", "");

                // append chunk al buffer condiviso
                responseBuffer.append(chunk);

                // costruisci log esadecimale
                for (byte b : data) {
                    combinedHex.append(String.format("%02X ", b));
                }
                combinedStr.append(chunk.replace("\r", "\\r").replace("\n", "\\n")).append(" ");
            }
            responseLock.notifyAll(); // sveglia thread che aspetta risposta
        }

        // aggiorna la UI con debug
        String finalHex = combinedHex.toString();
        String finalStr = combinedStr.toString();
        getActivity().runOnUiThread(() -> {
            receiveText.append("[DEBUG] Chunks ricevuti (hex): " + finalHex + "\n");
            receiveText.append("[DEBUG] Chunks ricevuti (str): " + finalStr + "\n");
        });

        // opzionale: mantieni anche l’append normale dei chunk nella UI come prima
        receive(datas);
    }


    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }


    // Function to vibrate
    public void vibrateDevice(Context context, long milliseconds) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(milliseconds);
            }
        }
    }

}
