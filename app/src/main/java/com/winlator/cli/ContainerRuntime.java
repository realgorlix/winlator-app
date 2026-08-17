package com.winlator.cli;

import com.winlator.container.Container;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class ContainerRuntime {
    public enum Status {STOPPED, RUNNING, CRASHED}

    private static final int MAX_BUFFERED_LINES = 5000;
    private static final ContainerRuntime instance = new ContainerRuntime();

    public interface Listener {
        void onLog(String line);
        void onStatusChanged(Status status, int exitCode);
    }

    private final ArrayList<String> logBuffer = new ArrayList<>();
    private final ArrayList<Listener> listeners = new ArrayList<>();
    private final Object lock = new Object();
    private Container container;
    private CliProcess process;
    private Status status = Status.STOPPED;
    private int exitCode = 0;
    private volatile boolean stopping = false;

    private ContainerRuntime() {}

    public static ContainerRuntime getInstance() {
        return instance;
    }

    public Container getContainer() {
        return container;
    }

    public Status getStatus() {
        synchronized (lock) {
            return status;
        }
    }

    public int getExitCode() {
        synchronized (lock) {
            return exitCode;
        }
    }

    public boolean isRunning() {
        return getStatus() == Status.RUNNING;
    }

    public boolean isRunningContainer(int containerId) {
        return container != null && container.id == containerId && isRunning();
    }

    public void start(Container container, CliProcess process) {
        synchronized (lock) {
            this.container = container;
            this.process = process;
            this.exitCode = 0;
            this.stopping = false;
            logBuffer.clear();
            status = Status.RUNNING;
        }

        notifyStatusChanged(Status.RUNNING, 0);
        startReaderThread();
        startWaitThread();
    }

    private void startReaderThread() {
        final CliProcess currentProcess = process;
        new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (lock) {
                        logBuffer.add(line);
                        if (logBuffer.size() > MAX_BUFFERED_LINES) logBuffer.remove(0);
                    }
                    notifyLog(line);
                }
            }
            catch (IOException e) {}
        }, "cli-reader").start();
    }

    private void startWaitThread() {
        final CliProcess currentProcess = process;
        new Thread(() -> {
            int code;
            try {
                code = currentProcess.waitFor();
            }
            catch (InterruptedException e) {
                code = 0;
            }

            Status newStatus;
            synchronized (lock) {
                exitCode = code;
                newStatus = stopping ? Status.STOPPED : (code == 0 ? Status.STOPPED : Status.CRASHED);
                status = newStatus;
            }
            currentProcess.close();
            notifyStatusChanged(newStatus, code);
        }, "cli-wait").start();
    }

    public boolean sendCommand(String command) {
        CliProcess currentProcess;
        synchronized (lock) {
            if (status != Status.RUNNING) return false;
            currentProcess = process;
        }
        if (currentProcess == null) return false;
        try {
            currentProcess.getOutputStream().write((command+"\n").getBytes(StandardCharsets.UTF_8));
            currentProcess.getOutputStream().flush();
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }

    public void stop() {
        CliProcess currentProcess;
        synchronized (lock) {
            stopping = true;
            currentProcess = process;
        }
        if (currentProcess != null) currentProcess.destroy();
    }

    public void addListener(Listener listener) {
        ArrayList<String> buffered;
        Status currentStatus;
        int code;
        synchronized (lock) {
            listeners.add(listener);
            buffered = new ArrayList<>(logBuffer);
            currentStatus = status;
            code = exitCode;
        }
        for (String line : buffered) listener.onLog(line);
        listener.onStatusChanged(currentStatus, code);
    }

    public void removeListener(Listener listener) {
        synchronized (lock) {
            listeners.remove(listener);
        }
    }

    private void notifyLog(String line) {
        ArrayList<Listener> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(listeners);
        }
        for (Listener listener : snapshot) listener.onLog(line);
    }

    private void notifyStatusChanged(Status status, int exitCode) {
        ArrayList<Listener> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(listeners);
        }
        for (Listener listener : snapshot) listener.onStatusChanged(status, exitCode);
    }
}
