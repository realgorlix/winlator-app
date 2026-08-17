package com.winlator.cli;

public class CliPty {
    public static final int DEFAULT_COLS = 80;
    public static final int DEFAULT_ROWS = 24;

    static {
        System.loadLibrary("winlator");
    }

    public static native int openPty(int cols, int rows);

    public static native String getSlavePath(int masterFd);
}
