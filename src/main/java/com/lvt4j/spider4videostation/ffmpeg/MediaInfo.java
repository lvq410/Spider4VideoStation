package com.lvt4j.spider4videostation.ffmpeg;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class MediaInfo {

    public List<StreamInfo> streams;
    public FormatInfo format;

    @Data
    public static class StreamInfo {
        public int index;
        public String codec_type;
        public String codec_name;
        public String codec_long_name;
        public Tags tags;

        @Data
        public static class Tags {
            public String language;
            public String title;
            public String DURATION;
        }
    }

    @Data
    public static class FormatInfo {
        public String duration;
        public Map<String, String> tags;

        public long parseDuration() {
            return FFmpegUtils.parseDuration(duration);
        }
    }
}
