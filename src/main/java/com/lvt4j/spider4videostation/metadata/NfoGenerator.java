package com.lvt4j.spider4videostation.metadata;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Kodi/Jellyfin NFO 文件生成器
 *
 * @author LV on 2023年2月9日
 */
public class NfoGenerator {

    public static void generateMovie(File nfoFile, JsonNode node) throws Exception {
        String base = baseName(nfoFile);
        File dir = nfoFile.getParentFile();

        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<movie>\n");
        tagRaw(sb, "lockdata", "true");
        tag(sb, "title", node.get("title"));
        tag(sb, "tagline", node.get("tagline"));
        tag(sb, "plot", node.get("summary"));
        String date = text(node.get("original_available"));
        if (!date.isEmpty()) {
            tagRaw(sb, "premiered", date);
            if (date.length() >= 4) tagRaw(sb, "year", date.substring(0, 4));
        }
        arr(sb, "genre", node.get("genre"));
        arr(sb, "actor", node.get("actor"), "name");
        arr(sb, "director", node.get("director"));
        arr(sb, "credits", node.get("writer"));
        tag(sb, "mpaa", node.get("certificate"));

        JsonNode extra = node.get("extra");
        copyAndTagImages(sb, extra, base, dir);
        tagRating(sb, extra);

        sb.append("</movie>\n");
        writeFile(nfoFile, sb.toString());
    }

    public static void generateEpisode(File nfoFile, JsonNode node, int season, int episode) throws Exception {
        String base = baseName(nfoFile);
        File dir = nfoFile.getParentFile();

        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<episodedetails>\n");
        tagRaw(sb, "lockdata", "true");
        tag(sb, "title", node.get("title"));
        tagRaw(sb, "season", String.valueOf(season));
        tagRaw(sb, "episode", String.valueOf(episode));
        tag(sb, "plot", node.get("summary"));
        String date = text(node.get("original_available"));
        if (!date.isEmpty()) tagRaw(sb, "aired", date);
        arr(sb, "genre", node.get("genre"));
        arr(sb, "actor", node.get("actor"), "name");
        arr(sb, "director", node.get("director"));
        arr(sb, "credits", node.get("writer"));
        tag(sb, "mpaa", node.get("certificate"));

        JsonNode extra = node.get("extra");
        copyAndTagImages(sb, extra, base, dir);

        sb.append("</episodedetails>\n");
        writeFile(nfoFile, sb.toString());
    }

    private static void copyAndTagImages(StringBuilder sb, JsonNode extra, String base, File dir) {
        if (extra == null) return;
        StringBuilder art = new StringBuilder();
        String poster = findInExtraDeep(extra, "poster");
        String thumbName = null;
        if (poster != null) {
            thumbName = copyImage(poster, base + "-thumb", dir);
            if (thumbName != null) {
                tagRaw(sb, "thumb", thumbName);
                art.append("    <poster>").append(esc(thumbName)).append("</poster>\n");
            }
        }
        String backdrop = findInExtraDeep(extra, "backdrop");
        String fanartName = null;
        if (backdrop != null) {
            fanartName = copyImage(backdrop, base + "-fanart", dir);
            if (fanartName != null) {
                tagRaw(sb, "fanart", fanartName);
                art.append("    <fanart>").append(esc(fanartName)).append("</fanart>\n");
            }
        }
        if (art.length() > 0) sb.append("  <art>\n").append(art).append("  </art>\n");
    }

    /** 从 extra 中提取评分 */
    private static void tagRating(StringBuilder sb, JsonNode extra) {
        if (extra == null) return;
        for (JsonNode siteNode : extra) {
            JsonNode rating = siteNode.get("rating");
            if (rating == null) continue;
            // rating 本身是个对象，取第一个数值
            for (JsonNode val : rating) {
                if (val.isNumber()) {
                    sb.append("  <rating>").append(val.asText()).append("</rating>\n");
                    return;
                }
            }
        }
    }

    /** 将缓存图片复制到目标目录，返回文件名 */
    private static String copyImage(String srcPath, String baseName, File dir) {
        try {
            File src = new File(srcPath);
            if (!src.exists()) return null;
            String ext = extension(src.getName());
            File dest = new File(dir, baseName + "." + ext);
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return dest.getName();
        } catch (Exception e) {
            return null;
        }
    }

    private static String baseName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "jpg";
        String ext = name.substring(dot + 1).toLowerCase();
        return ext.equals("cache") ? "webp" : ext;
    }

    private static String findInExtra(JsonNode extra, String field) {
        for (JsonNode siteNode : extra) {
            JsonNode arr = siteNode.get(field);
            if (arr != null && arr.isArray() && arr.size() > 0)
                return arr.get(0).asText();
        }
        return null;
    }

    private static String findInExtraDeep(JsonNode extra, String field) {
        String path = findInExtra(extra, field);
        if (path != null) return path;
        for (JsonNode siteNode : extra) {
            JsonNode tvshow = siteNode.get("tvshow");
            if (tvshow == null) continue;
            JsonNode tvExtra = tvshow.get("extra");
            if (tvExtra == null) continue;
            path = findInExtra(tvExtra, field);
            if (path != null) return path;
        }
        return null;
    }

    private static void tag(StringBuilder sb, String tag, JsonNode node) {
        String val = text(node);
        if (!val.isEmpty()) sb.append("  <").append(tag).append(">").append(esc(val)).append("</").append(tag).append(">\n");
    }

    private static void tagRaw(StringBuilder sb, String tag, String val) {
        if (val != null && !val.isEmpty()) sb.append("  <").append(tag).append(">").append(esc(val)).append("</").append(tag).append(">\n");
    }

    private static void arr(StringBuilder sb, String tag, JsonNode arr) {
        arr(sb, tag, arr, null);
    }

    private static void arr(StringBuilder sb, String tag, JsonNode arr, String subTag) {
        if (arr == null || !arr.isArray()) return;
        for (JsonNode item : arr) {
            String val = item.isTextual() ? item.asText() : (subTag != null ? text(item.get(subTag)) : item.asText());
            if (val.isEmpty()) continue;
            sb.append("  <").append(tag).append(">");
            if (subTag != null) sb.append("<").append(subTag).append(">").append(esc(val)).append("</").append(subTag).append(">");
            else sb.append(esc(val));
            sb.append("</").append(tag).append(">\n");
        }
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static void writeFile(File file, String content) throws Exception {
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }
}
