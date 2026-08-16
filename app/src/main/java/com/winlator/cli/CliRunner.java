package com.winlator.cli;

import android.content.Context;

import com.winlator.container.Container;
import com.winlator.core.Callback;
import com.winlator.core.EnvVars;
import com.winlator.core.GuestLauncher;
import com.winlator.core.ProcessHelper;
import com.winlator.core.WineUtils;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public abstract class CliRunner {
    public static java.lang.Process run(Context context, Container container, String execPath, String[] args, boolean debug, Callback<Integer> terminationCallback) {
        RootFS rootFS = RootFS.find(context);
        GuestLauncher.extractBox64File(context);
        GuestLauncher.copyDefaultBox64RCFile(context, rootFS);

        File rootDir = rootFS.getRootDir();
        String preset = container != null ? container.getBox64Preset() : GuestLauncher.defaultBox64Preset();
        EnvVars envVars = GuestLauncher.buildBaseEnvVars(context, rootFS, preset, true);
        envVars.put("DISPLAY", ":0");

        envVars.put("BOX64_SHOWSEGV", "1");
        if (debug) {
            envVars.put("BOX64_NOBANNER", "0");
            envVars.put("BOX64_LOG", "1");
            envVars.put("BOX64_DYNAREC_MISSING", "1");
            envVars.put("WINEDEBUG", "+seh,fixme-all,err-all");
        }
        else envVars.put("WINEDEBUG", "fixme-all");

        if (container != null) {
            envVars.put("WINEPREFIX", rootDir + RootFS.WINEPREFIX);
            envVars.putAll(container.getEnvVars());
            if (!envVars.has("WINEESYNC")) envVars.put("WINEESYNC", "1");
        }

        File shmDir = new File(rootDir, "/tmp/shm");
        if (!shmDir.isDirectory()) shmDir.mkdirs();

        File workingDir = resolveWorkingDir(context, container, execPath);

        ArrayList<String> command = new ArrayList<>();
        command.add(GuestLauncher.box64Command(rootFS));
        command.add("wine");
        command.add(execPath);
        if (args != null) command.addAll(Arrays.asList(args));

        return ProcessHelper.start(command.toArray(new String[0]), envVars, workingDir, true, terminationCallback);
    }

    public static File resolveWorkingDir(Context context, Container container, String execPath) {
        File rootDir = RootFS.find(context).getRootDir();
        if (container != null && !container.getWorkingDir().isEmpty()) {
            File workingDir = new File(container.getWorkingDir());
            if (workingDir.isDirectory()) return workingDir;
        }
        if (execPath != null && !execPath.isEmpty() && execPath.startsWith("/")) {
            File execDir = new File(execPath).getParentFile();
            if (execDir != null && execDir.isDirectory()) return execDir;
        }
        return rootDir;
    }

    public static void prepareContainer(Context context, Container container) {
        WineUtils.createDosdevicesSymlinks(container, true);
    }
}
