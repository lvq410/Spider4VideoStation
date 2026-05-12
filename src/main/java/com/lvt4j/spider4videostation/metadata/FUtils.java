package com.lvt4j.spider4videostation.metadata;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class FUtils {

    private static final Set<String> VideoExts = new HashSet<>(Arrays.asList(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "mpg", "mpeg",
        "rm", "rmvb", "ts", "m2ts", "ogv", "mts", "divx", "xvid", "3gp", "3g2",
        "asf", "vob", "f4v", "m2v", "mxf"
    ));

    public static File[] listVideoFiles(File folder) {
        return Stream.of(folder.listFiles()).filter(FUtils::isVideoFile).toArray(File[]::new);
    }

    public static boolean isVideoFile(File file) {
        return VideoExts.contains(extension(file.getName()));
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "";
        return name.substring(dot + 1).toLowerCase();
    }
}