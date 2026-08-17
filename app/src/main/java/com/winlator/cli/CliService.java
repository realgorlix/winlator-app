package com.winlator.cli;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.winlator.MainActivity;
import com.winlator.R;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.xconnector.UnixSocketConfig;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.RootFSInstaller;
import com.winlator.xenvironment.components.NetworkInfoUpdateComponent;
import com.winlator.xenvironment.components.XServerComponent;
import com.winlator.xserver.ScreenInfo;
import com.winlator.xserver.XServer;

import java.util.concurrent.Executors;

public class CliService extends Service {
    public static final String DEFAULT_CHANNEL_ID = "winlator_cli";
    public static final int NOTIFICATION_ID = 1001;
    private static final String TAG = "WinlatorCLI";

    private final SysVSHMServer sysVSHMServer = new SysVSHMServer();
    private final ContainerRuntime.Listener runtimeListener = new ContainerRuntime.Listener() {
        @Override
        public void onLog(String line) {}

        @Override
        public void onStatusChanged(ContainerRuntime.Status status, int exitCode) {
            if (status == ContainerRuntime.Status.RUNNING) return;
            if (status == ContainerRuntime.Status.CRASHED) {
                updateNotification(getString(R.string.cli_status_crashed, exitCode));
            }
            finish();
        }
    };

    private XServer xServer;
    private XServerComponent xServerComponent;
    private int containerId;
    private volatile boolean finishing = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ContainerRuntime.getInstance().isRunning()) return START_NOT_STICKY;

        finishing = false;
        containerId = 0;
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.cli_status_starting)));

        final String execPathArg = intent != null ? intent.getStringExtra("exec_path") : null;
        final int containerIdArg = intent != null ? intent.getIntExtra("container_id", 0) : 0;

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                run(execPathArg, containerIdArg);
            }
            catch (Throwable e) {
                Log.e(TAG, "cli session failed", e);
                updateNotification(e.getClass().getSimpleName()+": "+e.getMessage());
                finish();
            }
        });
        return START_NOT_STICKY;
    }

    private void finish() {
        if (finishing) return;
        finishing = true;
        ContainerRuntime.getInstance().removeListener(runtimeListener);
        if (xServerComponent != null) {
            xServerComponent.stop();
            xServerComponent = null;
            xServer = null;
        }
        sysVSHMServer.stop();
        stopSelf();
    }

    private void run(String execPathArg, int containerIdArg) {
        RootFS rootFS = RootFS.find(this);
        if (!rootFS.isValid() || rootFS.getVersion() < RootFSInstaller.LATEST_VERSION) {
            updateNotification(getString(R.string.cli_status_installing));
            if (!RootFSInstaller.installSync(this)) {
                finish();
                return;
            }
        }

        ContainerManager containerManager = new ContainerManager(this);
        Container container = null;
        if (containerIdArg > 0) container = containerManager.getContainerById(containerIdArg);
        if (container == null) container = containerManager.ensureContainer(Container.CLI_PORT_DEFAULT);
        if (container == null) {
            finish();
            return;
        }

        containerId = container.id;
        String execPath = execPathArg != null && !execPathArg.isEmpty() ? execPathArg : container.getExecPath();
        if (execPath == null || execPath.isEmpty()) {
            updateNotification(getString(R.string.cli_error_no_exec_path));
            finish();
            return;
        }

        ContainerRuntime.getInstance().addListener(runtimeListener);

        try {
            containerManager.activateContainer(container);
            updateNotification(getString(R.string.cli_status_preparing));
            CliRunner.prepareContainer(this, container);
            NetworkInfoUpdateComponent.updateFiles(this, rootFS);

            String rootPath = rootFS.getRootDir().getPath();
            xServer = new XServer(new ScreenInfo(1280, 720));
            xServerComponent = new XServerComponent(xServer, UnixSocketConfig.create(rootPath, UnixSocketConfig.XSERVER_PATH));
            xServerComponent.start();

            sysVSHMServer.setXServer(xServer);
            sysVSHMServer.start(rootPath);

            updateNotification(getString(R.string.cli_status_running, execPath));
            CliProcess process = CliRunner.run(this, container, execPath, null, container.isDebugEnabled(), null);
            if (process == null || process.process == null) {
                updateNotification(getString(R.string.cli_error_no_exec_path));
                finish();
                return;
            }

            ContainerRuntime.getInstance().start(container, process);
        }
        catch (Throwable e) {
            Log.e(TAG, "cli session failed", e);
            updateNotification(e.getClass().getSimpleName()+": "+e.getMessage());
            finish();
        }
    }

    private void createNotificationChannel() {
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(DEFAULT_CHANNEL_ID, getString(R.string.cli_notification_channel), NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }
    }

    private PendingIntent buildOpenConsoleIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("open_console_container_id", containerId);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, containerId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildNotification(String text) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, DEFAULT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.cli_notification_title))
            .setContentText(text)
            .setOngoing(true);

        if (containerId > 0) {
            builder.addAction(R.drawable.icon_keyboard, getString(R.string.open_console), buildOpenConsoleIntent());
        }

        return builder.build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(text));
    }
}
