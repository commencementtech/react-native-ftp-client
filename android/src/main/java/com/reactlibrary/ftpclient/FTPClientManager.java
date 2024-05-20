package com.reactlibrary.ftpclient;

import android.util.Log;

import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;

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
        try {
            if (ftpClient == null || !ftpClient.isConnected()) {
                ftpClient = new FTPSClient("TLS");
                ftpClient.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));
                ftpClient.setAutodetectUTF8(true);
                ftpClient.setControlEncoding("UTF-8");
                ftpClient.setConnectTimeout(60000);
                ftpClient.setBufferSize(10240);
                ftpClient.setDefaultTimeout(10000);

                // Enable SSL debugging
                System.setProperty("javax.net.debug", "ssl,handshake");

                // Setting the protocols and cipher suites explicitly if needed
                ftpClient.setEnabledProtocols(new String[] {"TLSv1.2", "TLSv1.3"});
                ftpClient.setEnabledCipherSuites(new String[] {
                        "TLS_AES_128_GCM_SHA256",
                        "TLS_AES_256_GCM_SHA384",
                        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                        "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"
                });

                ftpClient.connect(ipAddress, port);
                ftpClient.execPBSZ(0);
                ftpClient.execPROT("P");
                ftpClient.enterLocalPassiveMode();
                ftpClient.login(username, password);
                int reply = ftpClient.getReplyCode();
                if (!FTPReply.isPositiveCompletion(reply)) {
                    ftpClient.disconnect();
                    throw new IOException("Exception in connecting to FTP Server: " + reply);
                }
            }
        } catch (IOException e) {
            Log.e("FTPClientManager", "Connection failed", e);
            throw e;
        } catch (Exception e) {
            Log.e("FTPClientManager", "Unexpected exception", e);
            throw new IOException("Unexpected exception: " + e.getMessage(), e);
        }
    }

    public void disconnect() throws IOException {
        if (ftpClient != null && ftpClient.isConnected()) {
            try {
                ftpClient.logout();
                ftpClient.disconnect();
            } catch (IOException e) {
                ftpClient.disconnect();
                ftpClient = null;
                Log.e("FTPClientManager", "Disconnection failed", e);
                throw e;
            }
        }
    }

    public FTPSClient getFtpClient() {
        return ftpClient;
    }

    public void setFtpClient(FTPSClient ftpClient) {
        this.ftpClient = ftpClient;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}