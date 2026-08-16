package com.winlator.xenvironment.components;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

import com.winlator.core.FileUtils;
import com.winlator.core.NetworkHelper;
import com.winlator.xenvironment.EnvironmentComponent;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.util.List;

public class NetworkInfoUpdateComponent extends EnvironmentComponent {
    private BroadcastReceiver broadcastReceiver;

    @Override
    public void start() {
        Context context = environment.getContext();
        final NetworkHelper networkHelper = new NetworkHelper(context);
        updateFiles(networkHelper);

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateFiles(networkHelper);
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(broadcastReceiver, filter);
    }

    @Override
    public void stop() {
        if (broadcastReceiver != null) {
            environment.getContext().unregisterReceiver(broadcastReceiver);
            broadcastReceiver = null;
        }
    }

    public static void updateFiles(Context context, RootFS rootFS) {
        NetworkHelper networkHelper = new NetworkHelper(context);
        updateIFAddrsFile(rootFS, networkHelper.getIFAddresses());
        updateEtcHostsFile(rootFS, networkHelper.getIPv4Address());
    }

    private void updateFiles(NetworkHelper networkHelper) {
        updateIFAddrsFile(environment.getRootFS(), networkHelper.getIFAddresses());
        updateEtcHostsFile(environment.getRootFS(), networkHelper.getIPv4Address());
    }

    private static void updateIFAddrsFile(RootFS rootFS, List<NetworkHelper.IFAddress> ifAddresses) {
        File file = new File(rootFS.getTmpDir(), "ifaddrs");

        String content = "";
        if (!ifAddresses.isEmpty()) {
            for (NetworkHelper.IFAddress ifAddress : ifAddresses) {
                content += (!content.isEmpty() ? "\n" : "")+ifAddress.toString();
            }
        }
        else content = (new NetworkHelper.IFAddress()).toString();

        FileUtils.writeString(file, content);
    }

    private static void updateEtcHostsFile(RootFS rootFS, String ipAddress) {
        String ip = ipAddress != null ? ipAddress : "127.0.0.1";
        String hostname = null;
        try {
            byte[] data = FileUtils.read(new File("/proc/sys/kernel/hostname"));
            if (data != null) hostname = new String(data, java.nio.charset.StandardCharsets.UTF_8).trim();
        }
        catch (Exception ignored) {}

        String content = "127.0.0.1\tlocalhost\n"+ip+"\tlocalhost\n";
        if (hostname != null && !hostname.isEmpty()) content += "127.0.0.1\t"+hostname+"\n"+ip+"\t"+hostname+"\n";
        File file = new File(rootFS.getRootDir(), "etc/hosts");
        FileUtils.writeString(file, content);
    }
}
