package de.kai_morich.simple_bluetooth_terminal;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class TerminalFragment extends Fragment {

    private SerialService serialService;

    private String deviceAddress;
    private boolean bound = false;

    private EditText delayInput;
    private Button setButton;
    private TextView delayDisplay;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            SerialService.SerialBinder binder = (SerialService.SerialBinder) service;
            serialService = binder.getService();
            bound = true;

            updateNumberUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            serialService = null;
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        deviceAddress = getArguments().getString("device");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Minimal layout with two buttons
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);

        Button startButton = view.findViewById(R.id.button_start);
        Button stopButton = view.findViewById(R.id.button_stop);
        delayInput = view.findViewById(R.id.delayInput);
        setButton = view.findViewById(R.id.setButton);
        delayDisplay = view.findViewById(R.id.delayDisplay);

        setButton.setOnClickListener(v -> setNumberFromInput());

        startButton.setOnClickListener(v -> {
            if (bound) {
                // Replace with your actual device address
                serialService.start(deviceAddress);
            }
        });

        stopButton.setOnClickListener(v -> {
            if (bound) {
                serialService.stop();
            }
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(getActivity(), SerialService.class);

        getActivity().startService(intent);

        // Bind al servizio per la comunicazione UI
        getActivity().bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();

        // Unbind dal servizio per liberare il binding UI
        // Ma NON fermare il servizio: resterà vivo grazie a startForeground() nel service
        if (bound && getActivity() != null) {
            getActivity().unbindService(connection);
            bound = false;
        }
    }

    public void updateNumberUI() {
        if(bound) {
            int value = serialService.getDelay();
            delayDisplay.setText("Current value: " + value);
        }
    }

    public void setNumberFromInput() {
        if(bound) {
            int value = Integer.parseInt(delayInput.getText().toString());
            serialService.setDelay(value);
            updateNumberUI(); // refresh display
            delayInput.setText(""); // clear input
        }
    }
}