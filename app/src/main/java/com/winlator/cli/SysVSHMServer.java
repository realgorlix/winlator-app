package com.winlator.cli;

import com.winlator.sysvshm.SysVSHMConnectionHandler;
import com.winlator.sysvshm.SysVSHMRequestHandler;
import com.winlator.sysvshm.SysVSharedMemory;
import com.winlator.xconnector.UnixSocketConfig;
import com.winlator.xconnector.XConnectorEpoll;
import com.winlator.xserver.SHMSegmentManager;
import com.winlator.xserver.XServer;

public class SysVSHMServer {
    private XConnectorEpoll connector;
    private SysVSharedMemory sysVSharedMemory;
    private XServer xServer;

    public void setXServer(XServer xServer) {
        this.xServer = xServer;
    }

    public void start(String rootPath) {
        if (connector != null) return;
        UnixSocketConfig socketConfig = UnixSocketConfig.create(rootPath, UnixSocketConfig.SYSVSHM_SERVER_PATH);
        sysVSharedMemory = new SysVSharedMemory();
        connector = new XConnectorEpoll(socketConfig, new SysVSHMConnectionHandler(sysVSharedMemory), new SysVSHMRequestHandler());
        connector.start();
        if (xServer != null) xServer.setSHMSegmentManager(new SHMSegmentManager(sysVSharedMemory));
    }

    public void stop() {
        if (connector != null) {
            connector.destroy();
            connector = null;
            sysVSharedMemory.deleteAll();
        }
    }
}
