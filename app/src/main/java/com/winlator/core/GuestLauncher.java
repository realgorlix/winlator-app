package com.winlator.core;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.winlator.box64.Box64Preset;
import com.winlator.box64.Box64PresetManager;
import com.winlator.widget.LogView;
import com.winlator.xconnector.UnixSocketConfig;
import com.winlator.xenvironment.RootFS;

import java.io.File;

public abstract class GuestLauncher {
    public static void extractBox64File(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String box64Version = preferences.getString("box64_version", DefaultVersion.BOX64);
        String currentBox64Version = preferences.getString("current_box64_version", "");

        if (!box64Version.equals(currentBox64Version)) {
            GeneralComponents.extractFile(GeneralComponents.Type.BOX64, context, box64Version, DefaultVersion.BOX64);
            preferences.edit().putString("current_box64_version", box64Version).apply();
        }
    }

    public static void copyDefaultBox64RCFile(Context context, RootFS rootFS) {
        FileUtils.copy(context, "box64/default.box64rc", new File(rootFS.getRootDir(), "/etc/config.box64rc"));
    }

    public static EnvVars buildBaseEnvVars(Context context, RootFS rootFS, String box64Preset, boolean headless) {
        File rootDir = rootFS.getRootDir();
        EnvVars envVars = new EnvVars();
        addBox64EnvVars(context, rootFS, box64Preset, envVars);
        LocaleHelper.setEnvVars(envVars);

        envVars.put("HOME", rootDir+RootFS.HOME_PATH);
        envVars.put("USER", RootFS.USER);
        envVars.put("TMPDIR", rootDir+"/tmp");
        if (!headless) envVars.put("DISPLAY", ":0");
        envVars.put("PATH", rootDir+rootFS.getWinePath()+"/bin:"+rootDir+"/usr/local/bin:"+rootDir+"/usr/bin");
        envVars.put("LD_LIBRARY_PATH", rootFS.getLibDir().getPath());
        envVars.put("BOX64_LD_LIBRARY_PATH", rootDir+"/lib/x86_64-linux-gnu");
        envVars.put("ANDROID_SYSVSHM_SERVER", rootDir+UnixSocketConfig.SYSVSHM_SERVER_PATH);

        return envVars;
    }

    public static void addBox64EnvVars(Context context, RootFS rootFS, String box64Preset, EnvVars envVars) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        int box64Logs = preferences.getInt("box64_logs", 0);
        boolean saveToFile = preferences.getBoolean("save_logs_to_file", false);

        envVars.put("BOX64_NOBANNER", box64Logs >= 1 ? "0" : "1");
        envVars.put("BOX64_DYNAREC", "1");
        envVars.put("BOX64_UNITYPLAYER", "0");

        if (box64Logs >= 1) {
            envVars.put("BOX64_LOG", "1");
            envVars.put("BOX64_DYNAREC_MISSING", "1");

            if (box64Logs == 2) {
                envVars.put("BOX64_SHOWSEGV", "1");
                envVars.put("BOX64_DLSYM_ERROR", "1");
                envVars.put("BOX64_TRACE_FILE", "stderr");

                if (saveToFile) {
                    File parent = (new File(preferences.getString("log_file", LogView.getLogFile().getPath()))).getParentFile();
                    if (parent != null && parent.isDirectory()) {
                        File traceDir = new File(parent, "trace");
                        if (!traceDir.isDirectory()) traceDir.mkdirs();
                        FileUtils.clear(traceDir);

                        envVars.put("BOX64_TRACE_FILE", traceDir+"/box64-%pid.txt");
                    }
                }
            }
        }

        envVars.putAll(Box64PresetManager.getEnvVars(context, box64Preset));

        File box64RCFile = new File(rootFS.getRootDir(), "/etc/config.box64rc");
        envVars.put("BOX64_RCFILE", box64RCFile.getPath());
    }

    public static String box64Command(RootFS rootFS) {
        return rootFS.getRootDir()+"/usr/local/bin/box64";
    }

    public static String defaultBox64Preset() {
        return Box64Preset.CONSERVATIVE;
    }
}
