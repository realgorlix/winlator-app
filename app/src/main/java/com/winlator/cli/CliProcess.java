package com.winlator.cli;

import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class CliProcess {
    public final java.lang.Process process;
    public final int masterFd;
    private final ParcelFileDescriptor masterPfd;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    public CliProcess(java.lang.Process process, int masterFd, ParcelFileDescriptor masterPfd) {
        this.process = process;
        this.masterFd = masterFd;
        this.masterPfd = masterPfd;
        this.inputStream = new FileInputStream(masterPfd.getFileDescriptor());
        this.outputStream = new FileOutputStream(masterPfd.getFileDescriptor());
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public int waitFor() throws InterruptedException {
        return process.waitFor();
    }

    public void destroy() {
        process.destroy();
    }

    public void close() {
        try { inputStream.close(); } catch (IOException e) {}
        try { outputStream.close(); } catch (IOException e) {}
        try { masterPfd.close(); } catch (IOException e) {}
    }
}
