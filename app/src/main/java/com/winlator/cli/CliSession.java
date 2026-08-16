package com.winlator.cli;

import com.winlator.core.Callback;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class CliSession {
    private final int port;
    private ServerSocket serverSocket;
    private Socket socket;
    private volatile boolean running = false;

    public CliSession(int port) {
        this.port = port;
    }

    public boolean bind() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress("127.0.0.1", port));
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }

    public Socket accept(int timeoutMillis) {
        try {
            serverSocket.setSoTimeout(timeoutMillis);
            return serverSocket.accept();
        }
        catch (IOException e) {
            return null;
        }
    }

    public void start(java.lang.Process process, Socket clientSocket, Callback<Integer> onExit) {
        this.socket = clientSocket;
        running = true;

        final InputStream clientIn;
        final OutputStream clientOut;
        try {
            clientIn = clientSocket.getInputStream();
            clientOut = clientSocket.getOutputStream();
        }
        catch (IOException e) {
            running = false;
            return;
        }

        final OutputStream processIn = process.getOutputStream();
        final InputStream processOut = process.getInputStream();

        new Thread(() -> {
            byte[] buffer = new byte[4096];
            int length;
            try {
                while (running && (length = clientIn.read(buffer)) != -1) {
                    processIn.write(buffer, 0, length);
                    processIn.flush();
                }
            }
            catch (IOException e) {}
            try { processIn.close(); } catch (IOException e) {}
        }, "cli-stdin").start();

        new Thread(() -> {
            byte[] buffer = new byte[4096];
            int length;
            try {
                while ((length = processOut.read(buffer)) != -1) {
                    clientOut.write(buffer, 0, length);
                    clientOut.flush();
                }
            }
            catch (IOException e) {}
        }, "cli-stdout").start();

        new Thread(() -> {
            try {
                int exitCode = process.waitFor();
                running = false;
                try {
                    clientOut.write(("\n[winlator-cli] process exited with code "+exitCode+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    clientOut.flush();
                }
                catch (IOException e) {}
                close();
                if (onExit != null) onExit.call(exitCode);
            }
            catch (InterruptedException e) {}
        }, "cli-wait").start();
    }

    public void close() {
        running = false;
        try { if (socket != null) socket.close(); } catch (IOException e) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException e) {}
    }
}
