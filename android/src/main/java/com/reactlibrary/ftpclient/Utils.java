package com.reactlibrary.ftpclient;

import android.annotation.SuppressLint;
import android.os.Build;

import org.apache.commons.net.ftp.FTPFile;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class Utils {
    private static final String dateformat = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    public static String[] parseLine(String line) {
        String[] parts = line.split("\\s+");

        String permissions = !parts[0].trim().isEmpty() ? parts[0].trim() : null;
        String owner = !parts[2].trim().isEmpty() ? parts[2].trim() : null;
        String group = !parts[3].trim().isEmpty() ? parts[3].trim() : null;
        String size = !parts[4].trim().isEmpty() ? parts[4].trim() : null;
        return new String[]{permissions, owner, group, size};
    }

    public static String getStringByType(int type) {
        return switch (type) {
            case FTPFile.DIRECTORY_TYPE -> "dir";
            case FTPFile.FILE_TYPE -> "file";
            case FTPFile.SYMBOLIC_LINK_TYPE -> "link";
            default -> "unknown";
        };
    }

    public static String ISO8601StringFromCalender(Calendar calendar) {
        Date date = calendar.getTime();

        SimpleDateFormat sdf = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sdf = new SimpleDateFormat(dateformat);
        }
        assert sdf != null;
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(date);
    }

    public static String makeToken(final String path, final String remoteDestinationDir) {
        return String.format("%s=>%s", path, remoteDestinationDir);
    }

    public static String makeDownloadToken(final String path, final String remoteDestinationDir) {
        return String.format("%s<=%s", path, remoteDestinationDir);
    }

    public static String getLocalFilePath(String path, String remotePath) {
        if (path.endsWith("/")) {
            int index = remotePath.lastIndexOf("/");
            return path + remotePath.substring(index + 1);
        } else {
            return path;
        }
    }

}
