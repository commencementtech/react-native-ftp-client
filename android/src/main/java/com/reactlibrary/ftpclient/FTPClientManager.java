package com.reactlibrary.ftpclient;

import org.apache.commons.net.ftp.FTPSClient;

import java.io.IOException;

public class FTPClientManager {
    private static FTPClientManager instance;
    private FTPSClient ftpClient;
    private String ipAddress;
    private int port;
    private String username;
    private String password;

    private FTPClientManager() {
    }

    public static synchronized FTPClientManager getInstance() {
        if (instance == null) {
            instance = new FTPClientManager();
        }
        return instance;
    }

    public void configure(String ipAddress, int port, String username, String password) {
        this.ipAddress = ipAddress;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public void connect() throws IOException {
        if (ftpClient == null || !ftpClient.isConnected()) {
            ftpClient = new FTPSClient();
            ftpClient.connect(ipAddress, port);
            ftpClient.execPBSZ(0);
            ftpClient.execPROT("P");
            ftpClient.enterLocalPassiveMode();
            ftpClient.login(username, password);
        }
    }

    public void disconnect() throws IOException {
        if (ftpClient != null && ftpClient.isConnected()) {
            ftpClient.logout();
            ftpClient.disconnect();
        }
    }

    public FTPSClient getFtpClient() {
        return ftpClient;
    }
}
