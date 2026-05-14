package com.lvt4j.spider4videostation.metadata;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lvt4j.spider4videostation.TargetSite;
import com.lvt4j.spider4videostation.Utils;
import com.lvt4j.spider4videostation.ffmpeg.FFmpegUtils;
import com.lvt4j.spider4videostation.ffmpeg.MediaInfo;
import com.lvt4j.spider4videostation.pojo.Args;
import com.lvt4j.spider4videostation.pojo.Rst;
import com.lvt4j.spider4videostation.service.SearchOrchestratorService;

import lombok.extern.slf4j.Slf4j;

/**
 * 元数据文件生成器 —— 支持 vsmeta 和 NFO 两种格式
 *
 * @author LV on 2023年2月9日
 */
@Slf4j
@Service
public class MetadataGenerator {

    private static final Pattern EpPattern = Pattern.compile("(.+)\\.S(\\d{1,2})\\.E(\\d{1,4})(\\.|$)");

    @Autowired
    private SearchOrchestratorService searchOrchestrator;

    /** 单文件模式：为指定文件生成 vsmeta */
    public void generateVsmeta(File target, Object result, Consumer<String> onProgress, int index, int total) throws Exception {
        JsonNode node = Utils.ObjectMapper.valueToTree(result);
        VSmeta vsmeta = new VSmeta();
        boolean isEpisode = node.has("season") && !node.get("season").isNull();

        vsmeta.type = isEpisode ? VSmeta.TypeEpisode : VSmeta.TypeMovie;
        vsmeta.showTitle = vsmeta.showTitle2 = text(node.get("title"));
        vsmeta.episodeTitle = text(node.get("tagline"));
        if (vsmeta.episodeTitle.isEmpty()) vsmeta.episodeTitle = text(node.get("title"));

        String date = text(node.get("original_available"));
        if (date.length() >= 4) vsmeta.year = Integer.parseInt(date.substring(0, 4));
        if (!date.isEmpty()) vsmeta.episodeReleaseDate = date;

        vsmeta.chapterSummary = text(node.get("summary"));
        vsmeta.episodeLocked = 1;

        arr2list(node.get("actor"), vsmeta.casts);
        arr2list(node.get("director"), vsmeta.directors);
        arr2list(node.get("genre"), vsmeta.genres);
        arr2list(node.get("writer"), vsmeta.writers);
        vsmeta.classification = text(node.get("certificate"));

        JsonNode extra = node.get("extra");
        String posterData = null, posterMd5 = null;
        if (extra != null) {
            // 评分
            outer:
            for (JsonNode siteNode : extra) {
                JsonNode rating = siteNode.get("rating");
                if (rating == null) continue;
                for (JsonNode val : rating) {
                    if (val.isNumber()) {
                        int r = (int) Math.round(val.asDouble());
                        vsmeta.rating = new byte[]{(byte) r};
                        break outer;
                    }
                }
            }
            String poster = findInExtraDeep(extra, "poster");
            if (poster != null) {
                File imgFile = new File(poster);
                if (imgFile.exists()) {
                    posterData = VSmeta.readImgData(imgFile);
                    posterMd5 = md5(imgFile);
                    vsmeta.episodeThumbData = posterData;
                    vsmeta.episodeThumbMd5 = posterMd5;
                }
            }
            String backdrop = findInExtraDeep(extra, "backdrop");
            if (backdrop != null) {
                File imgFile = new File(backdrop);
                if (imgFile.exists()) {
                    vsmeta.backdropData = VSmeta.readImgData(imgFile);
                    vsmeta.backdropMd5 = md5(imgFile);
                }
            }
        }

        if (isEpisode) {
            vsmeta.season = node.get("season").asInt();
            vsmeta.episode = node.get("episode").asInt();
            vsmeta.tvShowYear = vsmeta.year;
            vsmeta.releaseDateTvShow = vsmeta.episodeReleaseDate;
            vsmeta.locked = 1;
            vsmeta.tvshowSummary = text(node.get("summary"));

            // 尝试从视频截图作为剧集缩略图
            if (target.isFile()) {
                try {
                    if (onProgress != null)
                        onProgress.accept(String.format("正在探测视频信息 S%02dE%02d (%d/%d)...", vsmeta.season, vsmeta.episode, index, total));
                    MediaInfo mediaInfo = FFmpegUtils.mediaInfo(target);
                    long position = (long)(mediaInfo.format.parseDuration() * 0.618);
                    String name = target.getName();
                    int dot = name.lastIndexOf('.');
                    String baseName = dot > 0 ? name.substring(0, dot) : name;
                    File snapshot = new File(target.getParentFile(), baseName + ".thumb.tmp.jpg");
                    if (onProgress != null)
                        onProgress.accept(String.format("正在截取视频画面 S%02dE%02d (%d/%d)...", vsmeta.season, vsmeta.episode, index, total));
                    FFmpegUtils.snapshot(target, FFmpegUtils.formatDuration(position), snapshot);
                    if (snapshot.exists() && snapshot.length() > 0) {
                        vsmeta.episodeThumbData = VSmeta.readImgData(snapshot);
                        vsmeta.episodeThumbMd5 = md5(snapshot);
                        snapshot.delete();
                    }
                } catch (Exception e) {
                    log.warn("视频截图失败，使用海报作为缩略图: {}", target.getAbsolutePath(), e);
                }
            }

            if (posterData != null) {
                vsmeta.posterData = posterData;
                vsmeta.posterMd5 = posterMd5;
            }
        }
        vsmeta.timestamp = (int)(System.currentTimeMillis() / 1000);

        File vsmetaFile = vsmetaFile(target);
        vsmeta.write(vsmetaFile);
        log.info("vsmeta generated: {}", vsmetaFile.getAbsolutePath());
    }

    /** 单文件模式：生成 NFO */
    public void generateNfo(File target, Object result) throws Exception {
        JsonNode node = Utils.ObjectMapper.valueToTree(result);
        File nfoFile = target.isDirectory() ? new File(target, "movie.nfo") : nfoFileForVideo(target);
        if (node.has("season") && !node.get("season").isNull()) {
            int season = node.get("season").asInt();
            int episode = node.get("episode").asInt();
            NfoGenerator.generateEpisode(nfoFile, node, season, episode);
        } else {
            NfoGenerator.generateMovie(nfoFile, node);
        }
        log.info("nfo generated for: {}", target.getAbsolutePath());
    }

    private static File nfoFileForVideo(File videoFile) {
        String name = videoFile.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return new File(videoFile.getParentFile(), base + ".nfo");
    }

    /** 批量剧集模式：遍历文件夹中视频文件，逐一搜索并生成元数据 */
    public int generateBatch(File folder, String showTitle, TargetSite ts, String lang,
            boolean vsmeta, boolean nfo, Consumer<String> onProgress) throws Exception {
        File[] videoFiles = FUtils.listVideoFiles(folder);
        int total = videoFiles.length, count = 0;
        for (int i = 0; i < total; i++) {
            File vf = videoFiles[i];
            String name = vf.getName();
            Matcher m = EpPattern.matcher(name);
            if (!m.find()) {
                log.info("skip non-episode file: {}", name);
                continue;
            }
            int season = Integer.parseInt(m.group(2));
            int episode = Integer.parseInt(m.group(3));

            if (onProgress != null)
                onProgress.accept(String.format("正在搜索 S%02dE%02d (%d/%d)...", season, episode, i + 1, total));

            Args.Input input = new Args.Input();
            input.title = showTitle;
            input.season = season;
            input.episode = episode;

            String body = "--type tvshow_episode --lang " + lang + " --input " +
                Utils.ObjectMapper.writeValueAsString(input) + " --limit 1";
            Args args = Args.parse(body);

            Rst rst = searchOrchestrator.search(ts, args);
            List<Object> results = rst.result;
            if (results.isEmpty()) {
                log.warn("no result for S{:02d}E{:02d} {}", season, episode, name);
                continue;
            }

            if (onProgress != null)
                onProgress.accept(String.format("正在生成 S%02dE%02d (%d/%d)...", season, episode, i + 1, total));

            Object result = results.get(0);
            if (vsmeta) generateVsmeta(vf, result, onProgress, i + 1, total);
            if (nfo) generateNfo(vf, result);
            count++;
        }
        return count;
    }

    private static File vsmetaFile(File target) {
        File dir = target.isDirectory() ? target : target.getParentFile();
        return new File(dir, target.getName() + ".vsmeta");
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText();
    }

    private static void arr2list(JsonNode arr, List<String> list) {
        if (arr == null || !arr.isArray()) return;
        for (JsonNode item : arr) list.add(item.asText());
    }

    private static String findInExtra(JsonNode extra, String field) {
        if (extra == null) return null;
        for (JsonNode siteNode : extra) {
            String path = firstLocalPath(siteNode.get(field));
            if (path != null) return path;
        }
        return null;
    }

    /** 深层查找：先查 extra.site.field，再查 extra.site.tvshow.extra.site.field */
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

    private static String firstLocalPath(JsonNode arr) {
        if (arr == null || !arr.isArray() || arr.size() == 0) return null;
        return arr.get(0).asText();
    }

    private static String md5(File file) {
        try {
            byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            log.warn("md5 fail: {}", file.getAbsolutePath(), e);
            return "";
        }
    }
}