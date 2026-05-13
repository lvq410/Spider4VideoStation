package com.lvt4j.spider4videostation.ffmpeg;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;

import com.lvt4j.spider4videostation.Utils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FFmpegUtils {

    public static final String FFmpegHome = System.getProperty("ffmpeg_path",
        "D:\\ffmpeg\\ffmpeg-n5.1.2-12-g7268323193-win64-gpl-5.1");
    public static final String FFmpegExe = System.getProperty("ffmpeg_exe",
        FFmpegHome + "\\bin\\ffmpeg.exe");
    public static final String FFprobeExe = System.getProperty("ffprobe_exe",
        FFmpegHome + "\\bin\\ffprobe.exe");

    private static final Pattern DurationPattern = Pattern.compile("(\\d+):(\\d+):(\\d+)\\.(\\d+)");

    public static long parseDuration(String duration) {
        Matcher matcher = DurationPattern.matcher(duration);
        if (!matcher.find()) return 0;
        long hour = Long.parseLong(matcher.group(1));
        long minute = Long.parseLong(matcher.group(2));
        long second = Long.parseLong(matcher.group(3));
        String milliStr = matcher.group(4);
        milliStr = milliStr.substring(0, Math.min(3, milliStr.length()));
        long millisecond = Long.parseLong(milliStr);
        return TimeUnit.HOURS.toMillis(hour)
            + TimeUnit.MINUTES.toMillis(minute)
            + TimeUnit.SECONDS.toMillis(second)
            + millisecond;
    }

    public static String formatDuration(long duration) {
        long hour = duration / 3600000;
        long minute = (duration % 3600000) / 60000;
        long second = (duration % 60000) / 1000;
        long millisecond = duration % 1000;
        return String.format("%02d:%02d:%02d.%03d", hour, minute, second, millisecond);
    }

    public static MediaInfo mediaInfo(File file) throws Exception {
        String cmd = FFprobeExe + " -v error -hide_banner -pretty -sexagesimal -print_format json -show_format -show_streams \"" + file.getAbsolutePath() + "\"";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exec(cmd, null, out);
        return Utils.ObjectMapper.readValue(out.toByteArray(), MediaInfo.class);
    }

    public static void snapshot(File video, String position, File snapshot) throws Exception {
        String cmd = FFmpegExe + " -hide_banner -y -i \"" + video.getAbsolutePath() + "\" -ss " + position + " -vframes 1 \"" + snapshot.getAbsolutePath() + "\"";
        exec(cmd, null, null);
    }

    private static void exec(String cmd, File folder, ByteArrayOutputStream captureOut) throws Exception {
        if (SystemUtils.IS_OS_WINDOWS) {
            File tmpFile = folder == null ? new File("tmp.bat") : new File(folder, "tmp.bat");
            try {
                String pre = "@echo off\r\n" + "chcp 65001 >nul\r\n";
                FileUtils.write(tmpFile, pre + cmd, Charset.defaultCharset());
                Process process = Runtime.getRuntime().exec("cmd /c tmp.bat", null, folder);
                handleProcess(process, captureOut);
            } finally {
                tmpFile.deleteOnExit();
            }
        } else {
            File tmpFile = folder == null ? new File("tmp.sh") : new File(folder, "tmp.sh");
            try {
                FileUtils.write(tmpFile, cmd, Charset.defaultCharset());
                Process process = Runtime.getRuntime().exec("/bin/sh tmp.sh");
                handleProcess(process, captureOut);
            } finally {
                tmpFile.deleteOnExit();
            }
        }
    }

    private static void handleProcess(Process process, ByteArrayOutputStream captureOut) throws Exception {
        IOTransfer outTransfer = new IOTransfer(process.getInputStream(),
            captureOut != null ? captureOut : new ByteArrayOutputStream(), false);
        IOTransfer errTransfer = new IOTransfer(process.getErrorStream(),
            new ByteArrayOutputStream(), true);
        outTransfer.start();
        errTransfer.start();
        int rst = process.waitFor();
        outTransfer.join();
        errTransfer.join();
        if (rst != 0) {
            throw new IOException("ffmpeg exit " + rst + ": " + new String(errTransfer.getCachedOut()));
        }
    }
}
