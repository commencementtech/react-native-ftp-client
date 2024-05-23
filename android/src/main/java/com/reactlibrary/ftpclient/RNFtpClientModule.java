package com.reactlibrary.ftpclient;

import static com.reactlibrary.ftpclient.Utils.ISO8601StringFromCalender;
import static com.reactlibrary.ftpclient.Utils.getLocalFilePath;
import static com.reactlibrary.ftpclient.Utils.getStringByType;
import static com.reactlibrary.ftpclient.Utils.makeDownloadToken;
import static com.reactlibrary.ftpclient.Utils.makeToken;
import static com.reactlibrary.ftpclient.Utils.parseLine;

import android.util.Log;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

public class RNFtpClientModule extends ReactContextBaseJavaModule {

    private final FTPClientManager ftpClientManager = FTPClientManager.getInstance();
    private Promise connectPromise;

    private static final String TAG = "RNFtpClient";
    private final static int MAX_UPLOAD_COUNT = 10;
    private final static int MAX_DOWNLOAD_COUNT = 10;
    private final static String HTTPCLIENT_PROGRESS_EVENT_NAME = "HTTPCLIENT_PROGRESS_EVENT_NAME";
    private final static String HTTPCLIENT_ERROR_CODE_LOGIN = "HTTPCLIENT_ERROR_CODE_LOGIN";
    private final static String HTTPCLIENT_ERROR_CODE_SYSTEM = "HTTPCLIENT_ERROR_CODE_SYSTEM";
    private final static String HTTPCLIENT_ERROR_CODE_DELETE = "HTTPCLIENT_ERROR_CODE_DELETE";
    private final static String HTTPCLIENT_ERROR_CODE_LIST = "HTTPCLIENT_ERROR_CODE_LIST";
    private final static String HTTPCLIENT_ERROR_CODE_UPLOAD = "HTTPCLIENT_ERROR_CODE_UPLOAD";
    private final static String HTTPCLIENT_ERROR_CODE_CANCEL_UPLOAD = "HTTPCLIENT_ERROR_CODE_CANCEL_UPLOAD";
    private final static String HTTPCLIENT_ERROR_CODE_DOWNLOAD = "HTTPCLIENT_ERROR_CODE_DOWNLOAD";
    private final static String HTTPCLIENT_ERROR_CODE_DIRECTORY_CHANGE = "HTTPCLIENT_ERROR_CODE_DIRECTORY_CHANGE";
    private final static String HTTPCLIENT_ERROR_CODE_DISCONNECT = "HTTPCLIENT_ERROR_CODE_DISCONNECT";
    private final static String ERROR_MESSAGE_CANCELLED = "ERROR_MESSAGE_CANCELLED";

    private final static String HTTPCLIENT_SUCCESS_CODE_LOGIN = "HTTPCLIENT_SUCCESS_CODE_LOGIN";
    private final static String HTTPCLIENT_SUCCESS_CODE_SYSTEM = "HTTPCLIENT_SUCCESS_CODE_SYSTEM";
    private final static String HTTPCLIENT_SUCCESS_CODE_DIRECTORY_LIST = "HTTPCLIENT_SUCCESS_CODE_DIRECTORY_LIST";
    private final static String HTTPCLIENT_SUCCESS_CODE_DIRECTORY_CHANGE = "HTTPCLIENT_SUCCESS_CODE_DIRECTORY_CHANGE";
    private final static String HTTPCLIENT_SUCCESS_DIRECTORY_DELETE = "HTTPCLIENT_SUCCESS_DIRECTORY_DELETE";
    private final static String HTTPCLIENT_SUCCESS_DOWNLOAD = "HTTPCLIENT_SUCCESS_DOWNLOAD";
    private final static String HTTPCLIENT_SUCCESS_FILE_DELETE = "HTTPCLIENT_SUCCESS_FILE_DELETE";
    private final static String HTTPCLIENT_SUCCESS_FILE_UPLOAD = "HTTPCLIENT_SUCCESS_FILE_UPLOAD";
    private final static String HTTPCLIENT_SUCCESS_CANCEL_UPLOAD = "HTTPCLIENT_SUCCESS_CANCEL_UPLOAD";
    private final static String HTTPCLIENT_ERROR_CODE_REMOVE = "HTTPCLIENT_ERROR_CODE_REMOVE";
    private final static String HTTPCLIENT_ERROR_CODE_LOGOUT = "HTTPCLIENT_ERROR_CODE_LOGOUT";
    private static final String EXPECTED_FINGERPRINT = "EXPECTED_FINGERPRINT_HERE";
    private static final String FINGERPRINT_EVENT = "FINGERPRINT_EVENT";
    private final ReactApplicationContext reactContext;
    private String ip_address;
    private int port;
    private String username;
    private String password;
    private HashMap<String, Thread> uploadingTasks = new HashMap<>();
    private HashMap<String, Thread> downloadingTasks = new HashMap<>();

    public RNFtpClientModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put(ERROR_MESSAGE_CANCELLED, ERROR_MESSAGE_CANCELLED);
        return constants;
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
        this.sendEvent(this.reactContext, HTTPCLIENT_PROGRESS_EVENT_NAME, params);
    }

    private long getRemoteSize(FTPSClient client, String remoteFilePath) throws Exception {
        client.sendCommand("SIZE", remoteFilePath);
        String[] reply = client.getReplyStrings();
        String[] response = reply[0].split(" ");
        if (client.getReplyCode() != 213) {
            throw new Exception(String.format("ftp client size cmd response %d", client.getReplyCode()));
        }
        return Long.parseLong(response[1]);
    }

    @ReactMethod
    public void setup(String ip_address, int port, String username, String password) {
        this.ip_address = ip_address;
        this.port = port;
        this.username = username;
        this.password = password;
        FTPClientManager.getInstance().configure(ip_address, port, username, password);
    }

    @ReactMethod
    public void login(Promise promise) {
        new Thread(() -> {
            boolean isSuccess = false;
            WritableMap params = Arguments.createMap();
            try {
                FTPClientManager.getInstance().disconnect();

                FTPClientManager.getInstance().connect();
                FTPSClient client = FTPClientManager.getInstance().getFtpClient();

                params.putBoolean("status", true);
                params.putString("message", HTTPCLIENT_SUCCESS_CODE_LOGIN);
                params.putString("directory", client.printWorkingDirectory());
                isSuccess = true;

            } catch (IOException e) {
                params.putBoolean("status", false);
                params.putString("message", HTTPCLIENT_ERROR_CODE_LOGIN);
                params.putString("exception", e.getMessage());
            } finally {
                if (isSuccess) {
                    promise.resolve(params);
                } else {
                    String paramsJson = params.toString();
                    promise.reject("FTP_LOGIN_ERROR", paramsJson);
                }
            }
        }).start();
    }

    @ReactMethod
    public void getSystemDetails(final Promise promise) {
        WritableMap params = Arguments.createMap();
        boolean isSuccess = false;
        try {
            FTPSClient client = FTPClientManager.getInstance().getFtpClient();
            WritableMap data = Arguments.createMap();

            data.putString("systemType", client.getSystemType());
            data.putString("status", client.getStatus());
            data.putString("replyString", client.getReplyString());
            data.putString("controlEncoding", client.getControlEncoding());
            data.putInt("replyCode", client.getReplyCode());
            data.putInt("bufferSize", client.getBufferSize());
            data.putInt("localPort", client.getLocalPort());
            data.putInt("passivePort", client.getPassivePort());
            data.putInt("dataConnectionMode", client.getDataConnectionMode());
            data.putInt("defaultPort", client.getDefaultPort());
            data.putInt("receiveDataSocketBufferSize", client.getReceiveDataSocketBufferSize());
            data.putInt("sendDataSocketBufferSize", client.getSendDataSocketBufferSize());
            data.putBoolean("enableSessionCreation", client.getEnableSessionCreation());
            data.putInt("remotePort", client.getRemotePort());
            data.putString("systemName", client.getSystemName());
            if(client.getPassiveLocalIPAddress() != null) {
                data.putString("hostAddress", client.getPassiveLocalIPAddress().getHostAddress());
                data.putString("hostName", client.getPassiveLocalIPAddress().getHostName());
                data.putString("canonicalHostName", client.getPassiveLocalIPAddress().getCanonicalHostName());
                data.putString("address", Arrays.toString(client.getPassiveLocalIPAddress().getAddress()));
                data.putString("passiveHostAddress", client.getPassiveLocalIPAddress().getHostAddress());
                data.putString("passiveHost", client.getPassiveHost());
            }
            if(client.getLocalAddress() != null) {
                data.putString("localAddress", client.getLocalAddress().getHostAddress());
            }
            params.putBoolean("status", true);
            params.putString("message", HTTPCLIENT_SUCCESS_CODE_SYSTEM);
            params.putMap("data", data);
            isSuccess = true;
        } catch (Exception e) {
            Log.d("getSystemDetails", "getSystemDetails Exception", e);
            params.putBoolean("status", false);
            params.putString("message", HTTPCLIENT_ERROR_CODE_SYSTEM);
            params.putString("exception", e.getMessage());
        } finally {
            if (isSuccess) {
                promise.resolve(params);
            } else {
                String paramsJson = params.toString();
                promise.reject(HTTPCLIENT_ERROR_CODE_SYSTEM, paramsJson);
            }
        }
    }

    @ReactMethod
    public void getDirectory(final String path, final Promise promise) {
        new Thread(() -> {
            WritableMap params = Arguments.createMap();
            boolean isSuccess = false;
            try {
                FTPSClient client = FTPClientManager.getInstance().getFtpClient();
                FTPFile[] files = new FTPFile[0];
                if (client != null && client.isConnected()) {
                    WritableArray directory = Arguments.createArray();
                    files = client.listFiles(path);
                    for (FTPFile file : files) {
                        WritableMap tmp = Arguments.createMap();
                        String[] rawData = parseLine(file.getRawListing());
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
                        directory.pushMap(tmp);
                    }
                    isSuccess = true;
                    params.putBoolean("status", true);
                    params.putString("message", HTTPCLIENT_SUCCESS_CODE_DIRECTORY_LIST);
                    params.putArray("data", directory);
                } else {
                    params.putBoolean("status", false);
                    params.putString("message", HTTPCLIENT_ERROR_CODE_LIST);
                    params.putString("exception", "FTP Client is not connected.");
                }
            } catch (Exception e) {
                params.putBoolean("status", false);
                params.putString("message", HTTPCLIENT_ERROR_CODE_LIST);
                params.putString("exception", e.getMessage());
            } finally {
                if (isSuccess){
                    promise.resolve(params);
                }
                else{
                    String paramsJson = params.toString();
                    promise.reject(HTTPCLIENT_ERROR_CODE_LIST, paramsJson);
                }
            }
        }).start();
    }

    @ReactMethod
    public void changeDirectory(String directory, Promise promise) {
        new Thread(() -> {
            boolean isSuccess = false;
            WritableMap params = Arguments.createMap();
            try {
                FTPSClient client = FTPClientManager.getInstance().getFtpClient();
                if (client != null && client.isConnected()) {
                    client.changeWorkingDirectory(directory);
                    params.putBoolean("status", true);
                    params.putString("message", HTTPCLIENT_SUCCESS_CODE_DIRECTORY_CHANGE);
                    params.putString("data", directory);
                    isSuccess = true;
                } else {
                    params.putBoolean("status", false);
                    params.putString("message", HTTPCLIENT_ERROR_CODE_DIRECTORY_CHANGE);
                    params.putString("exception", "FTP Client is not connected.");
                }
            } catch (IOException e) {
                params.putBoolean("status", false);
                params.putString("message", HTTPCLIENT_ERROR_CODE_DIRECTORY_CHANGE);
                params.putString("exception", e.getMessage());
            } finally {
                if (isSuccess){
                    promise.resolve(params);
                }
                else{
                    String paramsJson = params.toString();
                    promise.reject(HTTPCLIENT_ERROR_CODE_DIRECTORY_CHANGE, paramsJson);
                }
            }
        }).start();
    }

    @ReactMethod
    public void delete(final String path, final Promise promise) {
        new Thread(() -> {
            boolean isSuccess = false;
            WritableMap params = Arguments.createMap();
            try {
                FTPSClient client = FTPClientManager.getInstance().getFtpClient();
                if (path.endsWith(File.separator)) {
                    client.removeDirectory(path);
                    params.putString("message", HTTPCLIENT_SUCCESS_DIRECTORY_DELETE);
                    params.putString("data", path + " directory successfully deleted ");
                } else {
                    client.deleteFile(path);
                    params.putString("message", HTTPCLIENT_SUCCESS_FILE_DELETE);
                    params.putString("data", path + " file successfully deleted ");
                }
                params.putBoolean("status", true);
                isSuccess = true;
            } catch (Exception e) {
                Log.d("delete", "delete Exception", e);
                params.putBoolean("status", false);
                params.putString("message", HTTPCLIENT_ERROR_CODE_DELETE);
                params.putString("exception", e.getMessage());
            } finally {
                if (isSuccess){
                    promise.resolve(params);
                }
                else{
                    String paramsJson = params.toString();
                    promise.reject(HTTPCLIENT_ERROR_CODE_DELETE, paramsJson);
                }
            }
        }).start();
    }

    @ReactMethod
    public void upload(final String path, final String remoteDestinationPath, final Promise promise) {
        final String token = makeToken(path, remoteDestinationPath);
        WritableMap params = Arguments.createMap();

        if (uploadingTasks.containsKey(token)) {
            params.putBoolean("status", false);
            params.putString("message", HTTPCLIENT_ERROR_CODE_UPLOAD);
            params.putString("exception", "Same file upload is running");
            String paramsJson = params.toString();
            promise.reject(HTTPCLIENT_ERROR_CODE_UPLOAD, paramsJson);
            return;
        }
        if (uploadingTasks.size() >= MAX_UPLOAD_COUNT) {
            params.putBoolean("status", false);
            params.putString("message", HTTPCLIENT_ERROR_CODE_UPLOAD);
            params.putString("exception", "Reached max uploading tasks");
            String paramsJson = params.toString();
            promise.reject(HTTPCLIENT_ERROR_CODE_UPLOAD, paramsJson);
            return;
        }
        final Thread t =
                new Thread(() -> {
                    boolean isSuccess = false;
                    try {
                        FTPSClient client = FTPClientManager.getInstance().getFtpClient();
                        if (client != null && client.isConnected()) {
                            client.setFileType(FTP.BINARY_FILE_TYPE);
                            File localFile = new File(path);
                            long totalBytes = localFile.length();
                            long finishBytes = 0;

                            InputStream inputStream = new FileInputStream(localFile);

                            Log.d(TAG, "Start uploading file");

                            OutputStream outputStream = client.storeFileStream(remoteDestinationPath);
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

                            if (!Thread.currentThread().isInterrupted()) {
                                boolean done = client.completePendingCommand();
                                if (done) {
                                    params.putBoolean("status", true);
                                    params.putString("message", HTTPCLIENT_SUCCESS_FILE_UPLOAD);
                                    params.putString("data", localFile.getName() + " uploaded successfully...");
                                    isSuccess = true;
                                    //promise.resolve(params);
                                } else {
                                    params.putBoolean("status", false);
                                    params.putString("message", HTTPCLIENT_ERROR_CODE_UPLOAD);
                                    params.putString("exception", localFile.getName() + " is not uploaded successfully...");
                                    //promise.reject("", params);
                                    client.deleteFile(remoteDestinationPath);
                                }
                            } else {
                                params.putBoolean("status", false);
                                params.putString("message", HTTPCLIENT_ERROR_CODE_UPLOAD);
                                params.putString("exception", localFile.getName() + " uploading interrupted..." + ERROR_MESSAGE_CANCELLED);
                                //promise.reject("", params);
                            }
                        } else {
                            params.putBoolean("status", false);
                            params.putString("message", HTTPCLIENT_ERROR_CODE_UPLOAD);
                            params.putString("exception", "FTP Client is not connected.");
                            //promise.reject("", params);
                        }
                    } catch (IOException e) {
                        params.putBoolean("status", false);
                        params.putString("message", HTTPCLIENT_ERROR_CODE_UPLOAD);
                        params.putString("exception", e.getMessage());
                        //promise.reject("", params);
                    } finally {
                        uploadingTasks.remove(token);
                        if (isSuccess){
                            promise.resolve(params);
                        }
                        else{
                            String paramsJson = params.toString();
                            promise.reject(HTTPCLIENT_ERROR_CODE_UPLOAD, paramsJson);
                        }
                    }
                });
        t.start();
        uploadingTasks.put(token, t);
    }

    @ReactMethod
    public void cancelUpload(final String token, final Promise promise) {
        Thread upload = uploadingTasks.get(token);
        WritableMap params = Arguments.createMap();
        boolean isSuccess = false;
        if (upload == null) {
            params.putBoolean("status", false);
            params.putString("message", HTTPCLIENT_ERROR_CODE_CANCEL_UPLOAD);
            params.putString("exception", "There is no uploading task found with provided token");
            String paramsJson = params.toString();
            promise.reject(HTTPCLIENT_ERROR_CODE_CANCEL_UPLOAD, paramsJson);
            return;
        }

        try {
            upload.interrupt();
            FTPSClient client = FTPClientManager.getInstance().getFtpClient();
            if (client != null && client.isConnected()) {
                upload.join();
                String remoteFile = token.split("=>")[1];
                client.deleteFile(remoteFile);
                uploadingTasks.remove(token);

                params.putBoolean("status", true);
                params.putString("message", HTTPCLIENT_SUCCESS_CANCEL_UPLOAD);
                params.putString("data", "Uploading cancelled successfully...");
                isSuccess = true;
                //promise.resolve(params);
            } else {
                params.putBoolean("status", false);
                params.putString("message", HTTPCLIENT_ERROR_CODE_CANCEL_UPLOAD);
                params.putString("exception", "FTP Client is not connected.");
                //promise.reject("", params);
            }
        } catch (InterruptedException | IOException e) {
            params.putBoolean("status", false);
            params.putString("message", HTTPCLIENT_ERROR_CODE_CANCEL_UPLOAD);
            params.putString("exception", e.getMessage());
            //promise.reject("", params);
            //throw new RuntimeException(e);
        } finally {
            uploadingTasks.remove(token);
            if (isSuccess){
                promise.resolve(params);
            }
            else{
                String paramsJson = params.toString();
                promise.reject(HTTPCLIENT_ERROR_CODE_CANCEL_UPLOAD, paramsJson);
            }
        }
    }

    @ReactMethod
    public void download(final String path, final String remoteDestinationPath, final Promise promise) {
        final String token = makeDownloadToken(path, remoteDestinationPath);
        WritableMap params = Arguments.createMap();

        if (downloadingTasks.containsKey(token)) {
            params.putBoolean("status", false);
            params.putString("message", HTTPCLIENT_ERROR_CODE_DOWNLOAD);
            params.putString("exception", "There is already same downloading task running");
            String paramsJson = params.toString();
            promise.reject(HTTPCLIENT_ERROR_CODE_DOWNLOAD, paramsJson);
            return;
        }
        if (downloadingTasks.size() >= MAX_DOWNLOAD_COUNT) {
            params.putBoolean("status", false);
            params.putString("message", HTTPCLIENT_ERROR_CODE_DOWNLOAD);
            params.putString("exception", "Reached maximum downloading tasks...");
            String paramsJson = params.toString();
            promise.reject(HTTPCLIENT_ERROR_CODE_DOWNLOAD, paramsJson);
            return;
        }
        if (remoteDestinationPath.endsWith("/")) {
            params.putBoolean("status", false);
            params.putString("message", HTTPCLIENT_ERROR_CODE_DOWNLOAD);
            params.putString("exception", "Provided correct remote path to download it...");
            String paramsJson = params.toString();
            promise.reject(HTTPCLIENT_ERROR_CODE_DOWNLOAD, paramsJson);
            return;
        }

        final Thread t =
                new Thread(() -> {
                    boolean isSuccess = false;
                    try {
                        FTPSClient client = FTPClientManager.getInstance().getFtpClient();
                        if (client != null && client.isConnected()) {
                            client.setFileType(FTP.BINARY_FILE_TYPE);

                            final long totalBytes = getRemoteSize(client, remoteDestinationPath);
                            File downloadFile = new File(getLocalFilePath(path, remoteDestinationPath));

                            if (downloadFile.exists()) {
                                params.putBoolean("status", false);
                                params.putString("message", HTTPCLIENT_ERROR_CODE_DOWNLOAD);
                                params.putString("data", String.format("Locally file already exist", downloadFile.getAbsolutePath()));
                                String paramsJson = params.toString();
                                promise.reject(HTTPCLIENT_ERROR_CODE_DOWNLOAD, paramsJson);
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

                            if (!Thread.currentThread().isInterrupted()) {
                                boolean done = client.completePendingCommand();
                                if (done) {
                                    params.putBoolean("status", true);
                                    params.putString("message", HTTPCLIENT_SUCCESS_DOWNLOAD);
                                    params.putString("data", downloadFile.getName() + " file/folder downloaded successfully...");
                                    isSuccess = true;
//                                    promise.resolve(params);
                                } else {
                                    params.putBoolean("status", false);
                                    params.putString("message", HTTPCLIENT_ERROR_CODE_DOWNLOAD);
                                    params.putString("exception", downloadFile.getName() + " file/folder download not successfully...");
//                                    promise.reject("", params);
                                    downloadFile.delete();
                                }
                            } else {
                                params.putBoolean("status", false);
                                params.putString("message", HTTPCLIENT_ERROR_CODE_DOWNLOAD);
                                params.putString("exception", downloadFile.getName() + " file/folder download interrupted..." + ERROR_MESSAGE_CANCELLED);
//                                promise.reject("", params);
                                downloadFile.delete();
                            }
                        } else {
                            params.putBoolean("status", false);
                            params.putString("message", HTTPCLIENT_ERROR_CODE_DOWNLOAD);
                            params.putString("exception", "FTP Client is not connected.");
//                            promise.reject("", params);
                        }
                    } catch (Exception e) {
                        params.putBoolean("status", false);
                        params.putString("message", HTTPCLIENT_ERROR_CODE_DOWNLOAD);
                        params.putString("exception", e.getMessage());
//                        promise.reject("", params);
//                        throw new RuntimeException(e);
                    } finally {
                        downloadingTasks.remove(token);
                        if (isSuccess){
                            promise.resolve(params);
                        }
                        else{
                            String paramsJson = params.toString();
                            promise.reject(HTTPCLIENT_ERROR_CODE_DOWNLOAD, paramsJson);
                        }
                    }
                });
        t.start();
        downloadingTasks.put(token, t);
    }

    @ReactMethod
    public void cancelDownload(final String token, final Promise promise) {

        Thread download = downloadingTasks.get(token);

        if (download == null) {
            promise.reject(HTTPCLIENT_ERROR_CODE_DOWNLOAD, "token is wrong");
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


    @ReactMethod
    public void disconnect(final Promise promise) {
        FTPSClient client = new FTPSClient();
        try {
            logout(client);
            promise.resolve(true);
        } catch (Exception e) {
            Log.d(TAG, "disconnect error", e);
            promise.reject(HTTPCLIENT_ERROR_CODE_DISCONNECT, e.getMessage());
        } finally {
            logout(client);
            promise.resolve(true);
        }
        promise.resolve(false);
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


    @ReactMethod
    public void performHandshakeConfirmation(Promise promise) {
        new Thread(() -> {
            WritableMap params = Arguments.createMap();
            StringWriter handshakeLogWriter = new StringWriter();
            try (PrintWriter handshakePrintWriter = new PrintWriter(handshakeLogWriter)) {
                FTPSClient ftpClient = new FTPSClient("TLS");
                ftpClient.addProtocolCommandListener(new PrintCommandListener(handshakePrintWriter));
                ftpClient.setAutodetectUTF8(true);
                ftpClient.setControlEncoding("UTF-8");
                ftpClient.setConnectTimeout(60000);
                ftpClient.setBufferSize(10240);
                ftpClient.setDefaultTimeout(10000);

                // Enable SSL debugging
                System.setProperty("javax.net.debug", "ssl,handshake");

                // Setting the protocols and cipher suites explicitly if needed
                ftpClient.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
                ftpClient.setEnabledCipherSuites(new String[]{
                        "TLS_AES_128_GCM_SHA256",
                        "TLS_AES_256_GCM_SHA384",
                        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                        "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"
                });

                ftpClientManager.setFtpClient(ftpClient);

                ftpClient.connect(ftpClientManager.getIpAddress(), ftpClientManager.getPort());
                ftpClient.execPBSZ(0);
                ftpClient.execPROT("P");
                ftpClient.enterLocalPassiveMode();

                // Perform SSL handshake verification
                if (ftpClient.getNeedClientAuth()) {
                    throw new SSLException("Client authentication required by server.");
                }

                // Handshake successful
                String handshakeLogs = handshakeLogWriter.toString();
                params.putBoolean("status", true);
                params.putString("message", "SSL handshake successful. Do you want to proceed?");
                params.putString("handshakeLogs", handshakeLogs);
                promise.resolve(params);
            } catch (Exception e) {
                Log.e("RNFtpClientModule", "Handshake failed", e);
                String handshakeLogs = handshakeLogWriter.toString();
                params.putBoolean("status", false);
                params.putString("message", "SSL handshake failed: " + e.getMessage());
                params.putString("handshakeLogs", handshakeLogs);
                promise.reject("HANDSHAKE_ERROR", params);
            }
        }).start();
    }

    @ReactMethod
    public void userConfirmation(boolean confirmed) {
        if (confirmed) {
            try {
                ftpClientManager.connect();
                WritableMap params = Arguments.createMap();
                params.putBoolean("status", true);
                params.putString("message", "Login successful");
                params.putString("directory", ftpClientManager.getFtpClient().printWorkingDirectory());
                connectPromise.resolve(params);
            } catch (IOException e) {
                WritableMap params = Arguments.createMap();
                params.putBoolean("status", false);
                params.putString("message", "Login failed");
                params.putString("exception", e.getMessage());
                connectPromise.reject("LOGIN_ERROR", params);
            }
        } else {
            WritableMap params = Arguments.createMap();
            params.putBoolean("status", false);
            params.putString("message", "User cancelled the action");
            connectPromise.reject("USER_CANCELLED", params);
        }
    }

    @ReactMethod
    public void logins(Promise promise) {
        this.connectPromise = promise;
        performHandshakeConfirmation(promise);
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