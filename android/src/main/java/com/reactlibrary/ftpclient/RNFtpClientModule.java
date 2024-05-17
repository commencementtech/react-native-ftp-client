package com.reactlibrary.ftpclient;

import androidx.annotation.Nullable;

import android.os.Build;
import android.util.Log;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import java.io.*;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.*;

import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;

import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import com.facebook.react.bridge.*;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.Arguments;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;

import org.apache.commons.net.util.TrustManagerUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RNFtpClientModule extends ReactContextBaseJavaModule {

    private static final String TAG = "RNFtpClient";
    private final static int MAX_UPLOAD_COUNT = 10;
    private final static int MAX_DOWNLOAD_COUNT = 10;
    private final static String RNFTPCLIENT_PROGRESS_EVENT_NAME = "Progress";
    private final static String RNFTPCLIENT_ERROR_CODE_LOGIN = "RNFTPCLIENT_ERROR_CODE_LOGIN";
    private final static String RNFTPCLIENT_ERROR_CODE_LIST = "RNFTPCLIENT_ERROR_CODE_LIST";
    private final static String RNFTPCLIENT_ERROR_CODE_UPLOAD = "RNFTPCLIENT_ERROR_CODE_UPLOAD";
    private final static String RNFTPCLIENT_ERROR_CODE_CANCELUPLOAD = "RNFTPCLIENT_ERROR_CODE_CANCELUPLOAD";
    private final static String RNFTPCLIENT_ERROR_CODE_REMOVE = "RNFTPCLIENT_ERROR_CODE_REMOVE";
    private final static String RNFTPCLIENT_ERROR_CODE_LOGOUT = "RNFTPCLIENT_ERROR_CODE_LOGOUT";
    private final static String RNFTPCLIENT_ERROR_CODE_DOWNLOAD = "RNFTPCLIENT_ERROR_CODE_DOWNLOAD";
    private final static String RNFTPCLIENT_ERROR_CODE_SYSTEMNAME = "RNFTPCLIENT_ERROR_CODE_SYSTEMNAME";
    private final static String RNFTPCLIENT_ERROR_CODE_DICONNECT = "RNFTPCLIENT_ERROR_CODE_DICONNECT";
    private static final String EXPECTED_FINGERPRINT = "EXPECTED_FINGERPRINT_HERE";
    private final static String ERROR_MESSAGE_CANCELLED = "ERROR_MESSAGE_CANCELLED";
    private static final String FINGERPRINT_EVENT = "FINGERPRINT_EVENT";
    private final ReactApplicationContext reactContext;
    private String ip_address;
    private int port;
    private String username;
    private String password;
    private HashMap<String, Thread> uploadingTasks = new HashMap<>();
    private HashMap<String, Thread> downloadingTasks = new HashMap<>();
    private Promise fingerprintPromise;
    private String expectedFingerprint;
//    private FTPClient client;

    public RNFtpClientModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    private static String[] parseLine(String line) {
        String[] parts = line.split("\\s+");

        String permissions = !parts[0].trim().isEmpty() ? parts[0].trim() : null;
        String owner = !parts[2].trim().isEmpty() ? parts[2].trim() : null;
        String group = !parts[3].trim().isEmpty() ? parts[3].trim() : null;
        String size = !parts[4].trim().isEmpty() ? parts[4].trim() : null;
        return new String[]{permissions, owner, group, size};
    }

//    private void login(FTPSClient client) throws Exception {
//        client.connect(this.ip_address, this.port);
//        client.execPBSZ(0);
//        client.execPROT("P");
//        client.enterLocalPassiveMode();
//        if (!verifyServerFingerprint(client)) {
//            throw new IOException("Server fingerprint does not match the expected value.");
//        }
//        client.login(this.username, this.password);
//    }

    @ReactMethod
    public void setup(String ip_address, int port, String username, String password) {
        this.ip_address = ip_address;
        this.port = port;
        this.username = username;
        this.password = password;
        FTPClientManager.getInstance().configure(ip_address, port, username, password);
    }

//    private void login(FTPSClient client, final Promise promise) throws Exception {
    private void login(FTPClient client, final Promise promise) throws Exception {
        client = new FTPSClient();
        client.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));
        client.setAutodetectUTF8(true);
        client.setControlEncoding("UTF-8");
        client.setConnectTimeout(120000);
        client.setBufferSize(10240);
        client.setDefaultTimeout(10000);
        client.setFileType(FTP.BINARY_FILE_TYPE);

        client.connect(this.ip_address, this.port);

        int reply = client.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            client.disconnect();
            throw new IOException("Exception in connecting to FTP Server");
        }

        client.login(this.username, this.password);
        client.enterLocalPassiveMode();

        if (client instanceof FTPSClient) {
            ((FTPSClient) client).execPBSZ(0);
            ((FTPSClient) client).execPROT("P");
        }

        client.enterLocalPassiveMode();
        client.login(this.username, this.password);

        Log.d("Login|R| ", String.valueOf(client.getReplyCode()));
        Log.d("Login|CE| ", client.getControlEncoding());
        Log.d("Login|ST| ", client.getSystemType());
        Log.d("Login|RS| ", client.getReplyString());
        Log.d("Login|S| ", client.getStatus());
        Log.d("Login|C| ", String.valueOf(client.getCharset()));
        Log.d("Login|LHF| ", String.valueOf(client.getListHiddenFiles()));
        Log.d("Login|PWD| ", client.printWorkingDirectory());
        Log.d("Login|AAT| ", TrustManagerUtils.getAcceptAllTrustManager().toString());
    }

    private void logout(FTPClient client) {
        try {
            client.logout();
        } catch (IOException e) {
            Log.d(TAG, "logout error", e);
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
        } catch (IOException e) {
            Log.d(TAG, "logout disconnect error", e);
        }
    }

    private String getStringByType(int type) {
        switch (type) {
            case FTPFile.DIRECTORY_TYPE:
                return "dir";
            case FTPFile.FILE_TYPE:
                return "file";
            case FTPFile.SYMBOLIC_LINK_TYPE:
                return "link";
            case FTPFile.UNKNOWN_TYPE:
            default:
                return "unknown";
        }
    }

    private String ISO8601StringFromCalender(Calendar calendar) {
        Date date = calendar.getTime();

        SimpleDateFormat sdf = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        }
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(date);
    }

    private String makeToken(final String path, final String remoteDestinationDir) {
        return String.format("%s=>%s", path, remoteDestinationDir);
    }

    private String makeDownloadToken(final String path, final String remoteDestinationDir) {
        return String.format("%s<=%s", path, remoteDestinationDir);
    }

    private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    private void sendProgressEventToToken(String token, int percentage) {
        WritableMap params = Arguments.createMap();
        params.putString("token", token);
        params.putInt("percentage", percentage);

        Log.d(TAG, "send progress " + percentage + " to:" + token);
        this.sendEvent(this.reactContext, RNFTPCLIENT_PROGRESS_EVENT_NAME, params);
    }

    private String getLocalFilePath(String path, String remotePath) {
        if (path.endsWith("/")) {
            int index = remotePath.lastIndexOf("/");
            return path + remotePath.substring(index + 1);
        } else {
            return path;
        }
    }

    private long getRemoteSize(FTPClient client, String remoteFilePath) throws Exception {
        client.sendCommand("SIZE", remoteFilePath);
        String[] reply = client.getReplyStrings();
        String[] response = reply[0].split(" ");
        if (client.getReplyCode() != 213) {
            throw new Exception(String.format("ftp client size cmd response %d", client.getReplyCode()));
        }
        return Long.parseLong(response[1]);
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap();
        constants.put(ERROR_MESSAGE_CANCELLED, ERROR_MESSAGE_CANCELLED);
        return constants;
    }

    @ReactMethod
    public void loginFTP(Promise promise) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FTPClientManager.getInstance().connect();
                    promise.resolve(true);
                } catch (IOException e) {
                    promise.reject("ERROR_CONNECTING", e.getMessage());
                }
            }
        }).start();
    }
//    public void loginFTP(final Promise promise) throws IOException {
//        try{
//            FTPClient client = new FTPClient();
//            client.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));
//            client.setAutodetectUTF8(true);
//            client.setControlEncoding("UTF-8");
//            client.setConnectTimeout(120000);
//            client.setBufferSize(10240);
//            client.setDefaultTimeout(10000);
////            client.setFileType(FTP.BINARY_FILE_TYPE);
//            client.enterLocalPassiveMode();
//
//            if (client instanceof FTPSClient) {
//                ((FTPSClient) client).execPBSZ(0);
//                ((FTPSClient) client).execPROT("P");
//            }
//
//            client.connect(this.ip_address, this.port);
//            client.login(this.username, this.password);
//            int reply = client.getReplyCode();
//            if (!FTPReply.isPositiveCompletion(reply)) {
//                client.disconnect();
//                throw new IOException("Exception in connecting to FTP Server");
//            }
//
////            client.login(this.username, this.password);
////            client.enterLocalPassiveMode();
//
////            if (client instanceof FTPSClient) {
////                ((FTPSClient) client).execPBSZ(0);
////                ((FTPSClient) client).execPROT("P");
////            }
//
////            client.enterLocalPassiveMode();
////            client.login(this.username, this.password);
//
//            Log.d("Login|R| ", String.valueOf(client.getReplyCode()));
//            Log.d("Login|CE| ", client.getControlEncoding());
//            Log.d("Login|ST| ", client.getSystemType());
//            Log.d("Login|RS| ", client.getReplyString());
//            Log.d("Login|S| ", client.getStatus());
//            Log.d("Login|C| ", String.valueOf(client.getCharset()));
//            Log.d("Login|LHF| ", String.valueOf(client.getListHiddenFiles()));
//            Log.d("Login|PWD| ", client.printWorkingDirectory());
//            Log.d("Login|AAT| ", TrustManagerUtils.getAcceptAllTrustManager().toString());
//
//            promise.resolve(client);
//        } catch (Exception e) {
//            promise.reject("ERROR", e.getMessage());
//            Log.e("Erroor", Objects.requireNonNull(e.getLocalizedMessage()));
//        } finally {
//            promise.resolve(true);
//        }
//    }

    @ReactMethod
    public void list(final String path, final Promise promise) {

//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//
//
//                        promise.reject("ERROR_NOT_CONNECTED", "FTP Client is not connected.");
//                    }
//                } catch (IOException e) {
//                    promise.reject("ERROR_CHANGING_DIRECTORY", e.getMessage());
//                }
//            }
//        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                FTPFile[] files = new FTPFile[0];
                try {
                    FTPSClient client = FTPClientManager.getInstance().getFtpClient();
                    if (client != null && client.isConnected()) {
                        //login(client, promise);
                        files = client.listFiles(path);
                        WritableArray arrfiles = Arguments.createArray();
                        for (FTPFile file : files) {
                            String[] rawData = parseLine(file.getRawListing());
                            WritableMap tmp = Arguments.createMap();
                            tmp.putString("group", file.getGroup());
                            tmp.putInt("hardLinkCount", file.getHardLinkCount());
                            tmp.putString("link", file.getLink());
                            tmp.putString("link", file.getLink());
                            tmp.putString("name", file.getName());
                            tmp.putString("rawListing", file.getRawListing());
                            tmp.putInt("size", (int) file.getSize());
                            tmp.putString("timestamp", ISO8601StringFromCalender(file.getTimestamp()));
                            tmp.putString("type", getStringByType(file.getType()));
                            tmp.putString("user", file.getUser());
                            tmp.putBoolean("isDirectory", file.isDirectory());
                            tmp.putBoolean("isFile", file.isFile());
                            tmp.putBoolean("isSymbolicLink", file.isSymbolicLink());
                            tmp.putBoolean("isUnknown", file.isUnknown());
                            tmp.putBoolean("isValid", file.isValid());
                            tmp.putString("toFormattedString", file.toFormattedString());
                            tmp.putString("toString", file.toString());
                            tmp.putString("permissions", rawData[0]);
                            tmp.putString("owner", rawData[1]);

                            arrfiles.pushMap(tmp);
                        }
                        promise.resolve(arrfiles);
                    }
                    else {
                        promise.reject("ERROR_NOT_CONNECTED", "FTP Client is not connected.");
                    }
                } catch (Exception e) {
                    promise.reject(RNFTPCLIENT_ERROR_CODE_LIST, e.getMessage());
                } finally {
                    //logout(client);
                    promise.reject(RNFTPCLIENT_ERROR_CODE_LOGIN, "Connection is not open");
                }
            }
        }).start();
    }

    @ReactMethod
    public void remove(final String path, final Promise promise) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                FTPSClient client = new FTPSClient();
                try {
                   // login(client, promise);
                    if (path.endsWith(File.separator)) {
                        client.removeDirectory(path);
                    } else {
                        client.deleteFile(path);
                    }
                    promise.resolve(true);
                } catch (Exception e) {
                    promise.reject("ERROR", e.getMessage());
                } finally {
                    logout(client);
                }
            }
        }).start();
    }

    @ReactMethod
    public void uploadFile(final String path, final String remoteDestinationPath, final Promise promise) {
        final String token = makeToken(path, remoteDestinationPath);
        if (uploadingTasks.containsKey(token)) {
            promise.reject(RNFTPCLIENT_ERROR_CODE_UPLOAD, "same upload is runing");
            return;
        }
        if (uploadingTasks.size() >= MAX_UPLOAD_COUNT) {
            promise.reject(RNFTPCLIENT_ERROR_CODE_UPLOAD, "has reach max uploading tasks");
            return;
        }
        final Thread t =
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        FTPSClient client = new FTPSClient();
                        try {
                            login(client, promise);
                            client.setFileType(FTP.BINARY_FILE_TYPE);
                            File localFile = new File(path);
                            long totalBytes = localFile.length();
                            long finishBytes = 0;

                            String remoteFile = remoteDestinationPath;
                            InputStream inputStream = new FileInputStream(localFile);

                            Log.d(TAG, "Start uploading file");

                            OutputStream outputStream = client.storeFileStream(remoteFile);
                            byte[] bytesIn = new byte[4096];
                            int read = 0;

                            sendProgressEventToToken(token, 0);
                            Log.d(TAG, "Resolve token:" + token);
                            int lastPercentage = 0;
                            while ((read = inputStream.read(bytesIn)) != -1 && !Thread.currentThread().isInterrupted()) {
                                outputStream.write(bytesIn, 0, read);
                                finishBytes += read;
                                int newPercentage = (int) (finishBytes * 100 / totalBytes);
                                if (newPercentage > lastPercentage) {
                                    sendProgressEventToToken(token, newPercentage);
                                    lastPercentage = newPercentage;
                                }
                            }
                            inputStream.close();
                            outputStream.close();
                            Log.d(TAG, "Finish uploading");

                            //if not interrupted
                            if (!Thread.currentThread().isInterrupted()) {
                                boolean done = client.completePendingCommand();

                                if (done) {
                                    promise.resolve(true);
                                } else {
                                    promise.reject(RNFTPCLIENT_ERROR_CODE_UPLOAD, localFile.getName() + " is not uploaded successfully.");
                                    client.deleteFile(remoteFile);
                                }
                            } else {
                                //interupted, the file will deleted by cancel update operation
                                promise.reject(RNFTPCLIENT_ERROR_CODE_UPLOAD, ERROR_MESSAGE_CANCELLED);
                            }
                        } catch (Exception e) {
                            promise.reject(RNFTPCLIENT_ERROR_CODE_UPLOAD, e.getMessage());
                        } finally {
                            uploadingTasks.remove(token);
                            logout(client);
                        }
                    }
                });
        t.start();
        uploadingTasks.put(token, t);
    }

    @ReactMethod
    public void cancelUploadFile(final String token, final Promise promise) {

        Thread upload = uploadingTasks.get(token);

        if (upload == null) {
            promise.reject(RNFTPCLIENT_ERROR_CODE_UPLOAD, "token is wrong");
            return;
        }
        upload.interrupt();
        FTPSClient client = new FTPSClient();
        try {
            upload.join();
            login(client, promise);
            String remoteFile = token.split("=>")[1];
            client.deleteFile(remoteFile);
        } catch (Exception e) {
            Log.d(TAG, "cancel upload error", e);
        } finally {
            logout(client);
        }
        uploadingTasks.remove(token);
        promise.resolve(true);
    }

    @ReactMethod
    public void disconnect(final Promise promise) {
        FTPSClient client = new FTPSClient();
        try {
            logout(client);
            promise.resolve(true);
        } catch (Exception e) {
            Log.d(TAG, "disconnect error", e);
            promise.reject(RNFTPCLIENT_ERROR_CODE_DICONNECT, e.getMessage());
        } finally {
            logout(client);
            promise.resolve(true);
        }
        promise.resolve(false);
    }

    @ReactMethod
    public void getSystemName(final Promise promise) {
        FTPSClient client = new FTPSClient();
        try {
            String systemType = client.getSystemType();
            promise.resolve(systemType);
        } catch (Exception e) {
            Log.d(TAG, "getSystemName error", e);
            promise.reject(RNFTPCLIENT_ERROR_CODE_SYSTEMNAME, e.getMessage());
        }
    }

    @ReactMethod
    public void downloadFile(final String path, final String remoteDestinationPath, final Promise promise) {
        final String token = makeDownloadToken(path, remoteDestinationPath);
        if (downloadingTasks.containsKey(token)) {
            promise.reject(RNFTPCLIENT_ERROR_CODE_DOWNLOAD, "same downloading task is runing");
            return;
        }
        if (downloadingTasks.size() >= MAX_DOWNLOAD_COUNT) {
            promise.reject(RNFTPCLIENT_ERROR_CODE_DOWNLOAD, "has reach max downloading tasks");
            return;
        }
        if (remoteDestinationPath.endsWith("/")) {
            promise.reject(RNFTPCLIENT_ERROR_CODE_DOWNLOAD, "remote path can not be a dir");
            return;
        }

        final Thread t =
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        FTPSClient client = new FTPSClient();
                        try {
                            login(client, promise);
                            client.setFileType(FTP.BINARY_FILE_TYPE);

                            final long totalBytes = getRemoteSize(client, remoteDestinationPath);
                            File downloadFile = new File(getLocalFilePath(path, remoteDestinationPath));
                            if (downloadFile.exists()) {
                                throw new Error(String.format("local file exist", downloadFile.getAbsolutePath()));
                            }
                            File parentDir = downloadFile.getParentFile();
                            if (parentDir != null && !parentDir.exists()) {
                                parentDir.mkdirs();
                            }
                            downloadFile.createNewFile();
                            long finishBytes = 0;

                            Log.d(TAG, "Start downloading file");

                            OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(downloadFile));
                            InputStream inputStream = client.retrieveFileStream(remoteDestinationPath);
                            byte[] bytesIn = new byte[4096];
                            int read = 0;

                            sendProgressEventToToken(token, 0);
                            Log.d(TAG, "Resolve token:" + token);
                            int lastPercentage = 0;

                            while ((read = inputStream.read(bytesIn)) != -1 && !Thread.currentThread().isInterrupted()) {
                                outputStream.write(bytesIn, 0, read);
                                finishBytes += read;
                                int newPercentage = (int) (finishBytes * 100 / totalBytes);
                                if (newPercentage > lastPercentage) {
                                    sendProgressEventToToken(token, newPercentage);
                                    lastPercentage = newPercentage;
                                }
                            }
                            inputStream.close();
                            outputStream.close();
                            Log.d(TAG, "Finish uploading");

                            //if not interrupted
                            if (!Thread.currentThread().isInterrupted()) {
                                boolean done = client.completePendingCommand();

                                if (done) {
                                    promise.resolve(true);
                                } else {
                                    promise.reject(RNFTPCLIENT_ERROR_CODE_DOWNLOAD, downloadFile.getName() + " is not download successfully.");
                                    downloadFile.delete();
                                }
                            } else {
                                //interupted, the file will deleted by cancel download operation
                                promise.reject(RNFTPCLIENT_ERROR_CODE_DOWNLOAD, ERROR_MESSAGE_CANCELLED);
                                downloadFile.delete();
                            }
                        } catch (Exception e) {
                            promise.reject(RNFTPCLIENT_ERROR_CODE_DOWNLOAD, e.getMessage());
                        } finally {
                            downloadingTasks.remove(token);
                            logout(client);
                        }
                    }
                });
        t.start();
        downloadingTasks.put(token, t);
    }

    @ReactMethod
    public void cancelDownloadFile(final String token, final Promise promise) {

        Thread download = downloadingTasks.get(token);

        if (download == null) {
            promise.reject(RNFTPCLIENT_ERROR_CODE_DOWNLOAD, "token is wrong");
            return;
        }
        download.interrupt();
        FTPSClient client = new FTPSClient();
        try {
            download.join();
        } catch (Exception e) {
            Log.d(TAG, "cancel download error", e);
        }
        downloadingTasks.remove(token);
        promise.resolve(true);
    }

    @ReactMethod
    public void getServerFingerprint(final Promise promise) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                FTPSClient client = new FTPSClient("TLS");
                try {
                    client.connect(ip_address, port);
                    client.execPBSZ(0);
                    client.execPROT("P");
                    client.enterLocalPassiveMode();
                    client.login(username, password);

                    // Use the list method to trigger data connection and SSL negotiation
                    FTPFile[] files = client.listFiles();

                    // Get the server's SSL certificate from the SSL session
                    Method getSSLSocketMethod = client.getClass().getDeclaredMethod("getSSLSocket");
                    Object sslSocket = getSSLSocketMethod.invoke(client);
                    Method getSessionMethod = sslSocket.getClass().getMethod("getSession");
                    SSLSession session = (SSLSession) getSessionMethod.invoke(sslSocket);
                    Certificate[] certs = session.getPeerCertificates();

                    // Generate the fingerprint (SHA-256) of the certificate
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    byte[] fingerprint = md.digest(certs[0].getEncoded());
                    StringBuilder sb = new StringBuilder();
                    for (byte b : fingerprint) {
                        sb.append(String.format("%02X:", b));
                    }
                    if (sb.length() > 0) {
                        sb.deleteCharAt(sb.length() - 1);
                    }
                    String serverFingerprint = sb.toString();
                    promise.resolve(serverFingerprint);
                } catch (Exception e) {
                    promise.reject("ERROR_GETTING_FINGERPRINT", e.getMessage());
                } finally {
                    try {
                        if (client.isConnected()) {
                            client.disconnect();
                        }
                    } catch (IOException e) {
                        // Ignore
                    }
                }
            }
        }).start();
    }

    @Override
    public String getName() {
        return "RNFtpClient";
    }

//    @ReactMethod
//    public void disconnect(Promise promise) {
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    FTPClientManager.getInstance().disconnect();
//                    promise.resolve(true);
//                } catch (IOException e) {
//                    promise.reject("ERROR_DISCONNECTING", e.getMessage());
//                }
//            }
//        }).start();
//    }

//    @ReactMethod
//    public void changeDirectory(String directory, Promise promise) {
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    FTPSClient client = FTPClientManager.getInstance().getFtpClient();
//                    if (client != null &amp;&amp; client.isConnected()) {
//                        client.changeWorkingDirectory(directory);
//                        promise.resolve(true);
//                    } else {
//                        promise.reject("ERROR_NOT_CONNECTED", "FTP Client is not connected.");
//                    }
//                } catch (IOException e) {
//                    promise.reject("ERROR_CHANGING_DIRECTORY", e.getMessage());
//                }
//            }
//        }).start();
//    }

}