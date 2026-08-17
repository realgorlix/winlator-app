package com.winlator;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.winlator.cli.ContainerRuntime;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;

import java.util.ArrayList;

public class ContainerConsoleFragment extends Fragment {
    private static final int MAX_LINES = 2000;

    private final int containerId;
    private TextView statusView;
    private TextView logView;
    private ScrollView scrollView;
    private EditText commandView;
    private ImageButton sendButton;
    private final ArrayList<String> lines = new ArrayList<>();
    private final ContainerRuntime.Listener runtimeListener = new ContainerRuntime.Listener() {
        @Override
        public void onLog(String line) {
            if (getActivity() != null) getActivity().runOnUiThread(() -> appendLogLine(line));
        }

        @Override
        public void onStatusChanged(ContainerRuntime.Status status, int exitCode) {
            if (getActivity() != null) getActivity().runOnUiThread(() -> updateStatus(status, exitCode));
        }
    };

    public ContainerConsoleFragment(int containerId) {
        this.containerId = containerId;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.container_console_fragment, container, false);

        statusView = view.findViewById(R.id.TVStatus);
        logView = view.findViewById(R.id.TVLogs);
        scrollView = view.findViewById(R.id.ScrollView);
        commandView = view.findViewById(R.id.ETCommand);
        sendButton = view.findViewById(R.id.BTSend);

        sendButton.setOnClickListener((v) -> sendCommand());
        commandView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                sendCommand();
                return true;
            }
            return false;
        });

        Container activeContainer = new ContainerManager(requireContext()).getContainerById(containerId);
        if (activeContainer != null) ((AppCompatActivity)requireActivity()).getSupportActionBar().setTitle(activeContainer.getName());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        lines.clear();
        logView.setText("");
        ContainerRuntime.getInstance().addListener(runtimeListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        ContainerRuntime.getInstance().removeListener(runtimeListener);
    }

    private void sendCommand() {
        String command = commandView.getText().toString().trim();
        if (!command.isEmpty() && ContainerRuntime.getInstance().sendCommand(command)) {
            commandView.setText("");
        }
    }

    private void appendLogLine(String line) {
        lines.add(line);
        if (lines.size() > MAX_LINES) {
            lines.subList(0, lines.size() - MAX_LINES).clear();
            rebuildLog();
        }
        else logView.append(line+"\n");
        scrollToBottom();
    }

    private void rebuildLog() {
        logView.setText(String.join("\n", lines)+"\n");
    }

    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void updateStatus(ContainerRuntime.Status status, int exitCode) {
        int statusResId;
        switch (status) {
            case RUNNING:
                statusResId = R.string.status_running;
                break;
            case CRASHED:
                statusResId = R.string.status_crashed;
                break;
            default:
                statusResId = R.string.status_stopped;
                break;
        }

        String text = getString(statusResId);
        if (status == ContainerRuntime.Status.CRASHED) text = getString(R.string.status_crashed_with_code, exitCode);
        statusView.setText(text);

        boolean running = status == ContainerRuntime.Status.RUNNING;
        commandView.setEnabled(running);
        sendButton.setEnabled(running);
    }
}
