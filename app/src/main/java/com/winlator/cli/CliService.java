package com.winlator.cli;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

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

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class CliService extends Service {
    public static final String DEFAULT_CHANNEL_ID = "winlator_cli";
    public static final int NOTIFICATION_ID = 1001;
    public static final int DEFAULT_ACCEPT_TIMEOUT = 60000;
    private static final String TAG = "WinlatorCLI";

    private final SysVSHMServer sysVSHMServer = new SysVSHMServer();
    private XServer xServer;
    private XServerComponent xServerComponent;

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
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.cli_status_starting)));

        final String execPathArg = intent != null ? intent.getStringExtra("exec_path") : null;
        final String[] args = intent != null ? intent.getStringArrayExtra("args") : null;
        final int containerId = intent != null ? intent.getIntExtra("container_id", 0) : 0;
        final int portArg = intent != null ? intent.getIntExtra("port", 0) : 0;
        final int acceptTimeout = intent != null ? intent.getIntExtra("accept_timeout", DEFAULT_ACCEPT_TIMEOUT) : DEFAULT_ACCEPT_TIMEOUT;
        final boolean debug = intent != null && intent.getBooleanExtra("debug", false);

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                run(execPathArg, args, containerId, portArg, acceptTimeout, debug);
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
        if (xServerComponent != null) {
            xServerComponent.stop();
            xServerComponent = null;
            xServer = null;
        }
        sysVSHMServer.stop();
        stopSelf();
    }

    private void run(String execPathArg, String[] args, int containerId, int portArg, int acceptTimeout, boolean debug) {
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
        if (containerId > 0) container = containerManager.getContainerById(containerId);
        else if (portArg > 0) container = containerManager.getContainerByCliPort(portArg);
        if (container == null) container = containerManager.ensureContainer(Container.CLI_PORT_DEFAULT);
        if (container == null) {
            finish();
            return;
        }

        int port = portArg > 0 ? portArg : container.getCliPort();

        String execPath = execPathArg != null && !execPathArg.isEmpty() ? execPathArg : container.getExecPath();
        if (execPath == null || execPath.isEmpty()) {
            updateNotification(getString(R.string.cli_error_no_exec_path));
            finish();
            return;
        }

        CliSession session = new CliSession(port);
        if (!session.bind()) {
            updateNotification(getString(R.string.cli_error_port_in_use, port));
            finish();
            return;
        }

        updateNotification(getString(R.string.cli_status_waiting, port));
        Socket client = session.accept(acceptTimeout);
        if (client == null) {
            session.close();
            finish();
            return;
        }

        try {
            sendToClient(client, "[winlator-cli] connected, preparing container...\n");
            containerManager.activateContainer(container);
            updateNotification(getString(R.string.cli_status_preparing));
            CliRunner.prepareContainer(this, container);
            NetworkInfoUpdateComponent.updateFiles(this, rootFS);

            sendToClient(client, "[winlator-cli] starting X server...\n");
            String rootPath = rootFS.getRootDir().getPath();
            xServer = new XServer(new ScreenInfo(1280, 720));
            xServerComponent = new XServerComponent(xServer, UnixSocketConfig.create(rootPath, UnixSocketConfig.XSERVER_PATH));
            xServerComponent.start();

            sysVSHMServer.setXServer(xServer);
            sysVSHMServer.start(rootPath);

            sendToClient(client, "[winlator-cli] starting "+execPath+"\n");
            updateNotification(getString(R.string.cli_status_running, execPath));
            CliProcess process = CliRunner.run(this, container, execPath, args, debug, null);
            if (process == null || process.process == null) {
                sendToClient(client, "[winlator-cli] error: failed to start process\n");
                session.close();
                finish();
                return;
            }

            session.start(process, client, (status) -> finish());
        }
        catch (Throwable e) {
            Log.e(TAG, "cli session failed", e);
            sendToClient(client, "[winlator-cli] error: "+e+"\n");
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            session.close();
            finish();
        }
    }

    private void sendToClient(Socket client, String text) {
        try {
            client.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
            client.getOutputStream().flush();
        }
        catch (Exception e) {
            Log.e(TAG, "failed to send to client", e);
        }
    }

    private void createNotificationChannel() {
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(DEFAULT_CHANNEL_ID, getString(R.string.cli_notification_channel), NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, DEFAULT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.cli_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(text));
    }
}
