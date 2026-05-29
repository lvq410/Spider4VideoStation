package com.lvt4j.spider4videostation.service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.lvt4j.spider4videostation.Utils;

public class DsmApiClient {

    private final String baseUrl;
    private final String account;
    private final String passwd;

    private String cachedSid;

    public DsmApiClient(String baseUrl, String account, String passwd) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.account = account;
        this.passwd = passwd;
    }

    public static class Library {
        public int id;
        public String title;
        public String type;
        public String path;
        @Override public String toString() { return title + " (" + type + ")"; }
    }

    public static class InvalidVideo {
        public String type;
        public int id;
        public String title;
        public String sharepath;
    }

    public static class FileInfo {
        public String name;
        public String path;
        public boolean isdir;
        public long size;
    }

    // ==================== Video Station ====================

    public List<Library> listLibraries() throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("api", "SYNO.VideoStation.Library");
        params.put("version", "2");
        params.put("method", "list");
        JsonNode resp = post("/webapi/VideoStation/library.cgi", params);
        checkSuccess(resp, "获取视频库失败");
        List<Library> result = new ArrayList<>();
        for (JsonNode lib : resp.get("data").get("libraries")) {
            Library l = new Library();
            l.id = lib.get("id").asInt();
            l.title = lib.get("title").asText();
            l.type = lib.get("type").asText();
            if (lib.has("path")) l.path = lib.get("path").asText();
            result.add(l);
        }
        return result;
    }

    public static class PageResult<T> {
        public int total;
        public List<T> items;
        public PageResult(int total, List<T> items) {
            this.total = total;
            this.items = items;
        }
    }

    public PageResult<InvalidVideo> listMovies(int libraryId, int offset, int limit) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("api", "SYNO.VideoStation2.Movie");
        params.put("version", "1");
        params.put("method", "list");
        params.put("library_id", String.valueOf(libraryId));
        params.put("limit", String.valueOf(limit));
        params.put("offset", String.valueOf(offset));
        params.put("sort_by", "title");
        params.put("sort_direction", "asc");
        params.put("additional", "[\"file\"]");
        JsonNode resp = post("/webapi/entry.cgi", params);
        checkSuccess(resp, "获取电影列表失败");
        JsonNode data = resp.get("data");
        int total = data.get("total").asInt();
        List<InvalidVideo> items = new ArrayList<>();
        for (JsonNode m : data.get("movie")) {
            int mid = m.get("id").asInt();
            String title = m.get("title").asText();
            JsonNode files = m.path("additional").path("file");
            if (files.isArray()) {
                for (JsonNode f : files) {
                    InvalidVideo v = new InvalidVideo();
                    v.type = "movie";
                    v.id = mid;
                    v.title = title;
                    v.sharepath = f.get("sharepath").asText();
                    items.add(v);
                }
            }
        }
        return new PageResult<>(total, items);
    }

    public PageResult<InvalidVideo> listHomeVideos(int libraryId, int offset, int limit) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("api", "SYNO.VideoStation2.HomeVideo");
        params.put("version", "1");
        params.put("method", "list");
        params.put("limit", String.valueOf(limit));
        params.put("offset", String.valueOf(offset));
        params.put("library_id", String.valueOf(libraryId));
        params.put("sort_by", "title");
        params.put("sort_direction", "asc");
        params.put("additional", "[\"file\"]");
        JsonNode resp = post("/webapi/entry.cgi", params);
        checkSuccess(resp, "获取家庭视频列表失败");
        JsonNode data = resp.get("data");
        int total = data.get("total").asInt();
        List<InvalidVideo> items = new ArrayList<>();
        String listKey = data.has("video") ? "video" : "home_video";
        for (JsonNode m : data.get(listKey)) {
            int vid = m.get("id").asInt();
            String title = m.get("title").asText();
            JsonNode files = m.path("additional").path("file");
            if (files.isArray()) {
                for (JsonNode f : files) {
                    InvalidVideo v = new InvalidVideo();
                    v.type = "home_video";
                    v.id = vid;
                    v.title = title;
                    v.sharepath = f.get("sharepath").asText();
                    items.add(v);
                }
            }
        }
        return new PageResult<>(total, items);
    }

    public static class TVShow {
        public int id;
        public String title;
    }

    public PageResult<TVShow> listTVShows(int libraryId, int offset, int limit) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("api", "SYNO.VideoStation2.TVShow");
        params.put("version", "1");
        params.put("method", "list");
        params.put("limit", String.valueOf(limit));
        params.put("offset", String.valueOf(offset));
        params.put("library_id", String.valueOf(libraryId));
        params.put("sort_by", "title");
        params.put("sort_direction", "asc");
        JsonNode resp = post("/webapi/entry.cgi", params);
        checkSuccess(resp, "获取剧集列表失败");
        JsonNode data = resp.get("data");
        int total = data.get("total").asInt();
        List<TVShow> items = new ArrayList<>();
        for (JsonNode s : data.get("tvshow")) {
            TVShow t = new TVShow();
            t.id = s.get("id").asInt();
            t.title = s.get("title").asText();
            items.add(t);
        }
        return new PageResult<>(total, items);
    }

    public PageResult<InvalidVideo> listEpisodes(int libraryId, int showId, String showTitle, int offset, int limit) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("api", "SYNO.VideoStation2.TVShowEpisode");
        params.put("version", "1");
        params.put("method", "list");
        params.put("limit", String.valueOf(limit));
        params.put("offset", String.valueOf(offset));
        params.put("library_id", String.valueOf(libraryId));
        params.put("tvshow_id", String.valueOf(showId));
        params.put("additional", "[\"file\"]");
        JsonNode resp = post("/webapi/entry.cgi", params);
        checkSuccess(resp, "获取剧集详情失败");
        JsonNode data = resp.get("data");
        int total = data.get("total").asInt();
        List<InvalidVideo> items = new ArrayList<>();
        for (JsonNode ep : data.get("episode")) {
            int epId = ep.get("id").asInt();
            int season = ep.has("season") ? ep.get("season").asInt() : 0;
            int episode = ep.has("episode") ? ep.get("episode").asInt() : 0;
            String epTitle = showTitle + " S" + String.format("%02d", season) + "E" + String.format("%02d", episode);
            JsonNode files = ep.path("additional").path("file");
            if (files.isArray()) {
                for (JsonNode f : files) {
                    InvalidVideo v = new InvalidVideo();
                    v.type = "episode";
                    v.id = epId;
                    v.title = epTitle;
                    v.sharepath = f.get("sharepath").asText();
                    items.add(v);
                }
            }
        }
        return new PageResult<>(total, items);
    }

    // ==================== File Station ====================

    public List<FileInfo> listFolder(String folderPath) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("api", "SYNO.FileStation.List");
        params.put("version", "2");
        params.put("method", "list");
        params.put("folder_path", folderPath);
        params.put("additional", "[\"real_path\",\"size\"]");
        JsonNode resp = post("/webapi/entry.cgi", params);
        checkSuccess(resp, "列出文件夹失败: " + folderPath);
        List<FileInfo> result = new ArrayList<>();
        for (JsonNode f : resp.get("data").get("files")) {
            FileInfo fi = new FileInfo();
            fi.name = f.get("name").asText();
            fi.path = f.get("path").asText();
            fi.isdir = f.get("isdir").asBoolean();
            if (f.has("additional") && f.get("additional").has("size")) {
                fi.size = f.get("additional").get("size").asLong();
            }
            result.add(fi);
        }
        return result;
    }

    public String getLibraryFolderPath(String section, int libraryId) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action", "list");
        params.put("section", section);
        params.put("library_id", String.valueOf(libraryId));
        JsonNode resp = post("/webman/3rdparty/VideoStation/cgi/folder_manage.cgi", params);
        checkSuccess(resp, "获取库文件夹路径失败");
        JsonNode folders = resp.get("data").get("folders");
        if (folders.isArray() && folders.size() > 0) {
            return folders.get(0).get("path").asText();
        }
        return null;
    }

    public Map<String, Boolean> checkFilesExist(List<String> paths) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("api", "SYNO.FileStation.List");
        params.put("version", "2");
        params.put("method", "getinfo");
        params.put("path", Utils.ObjectMapper.writeValueAsString(paths));
        JsonNode resp = post("/webapi/entry.cgi", params);
        Map<String, Boolean> result = new HashMap<>();
        if (resp.get("success").asBoolean()) {
            Map<String, JsonNode> fileMap = new HashMap<>();
            for (JsonNode f : resp.get("data").get("files")) {
                fileMap.put(f.get("path").asText(), f);
            }
            for (String path : paths) {
                JsonNode info = fileMap.get(path);
                result.put(path, info != null && !info.has("code"));
            }
        } else {
            for (String path : paths) result.put(path, false);
        }
        return result;
    }

    // ==================== Delete ====================

    public void deleteMovies(List<Integer> ids) throws IOException {
        deleteByApi("SYNO.VideoStation2.Movie", ids);
    }

    public void deleteEpisodes(List<Integer> ids) throws IOException {
        deleteByApi("SYNO.VideoStation2.TVShowEpisode", ids);
    }

    public void deleteHomeVideos(List<Integer> ids) throws IOException {
        deleteByApi("SYNO.VideoStation2.HomeVideo", ids);
    }

    private void deleteByApi(String api, List<Integer> ids) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("api", api);
        params.put("version", "1");
        params.put("method", "delete");
        params.put("id", Utils.ObjectMapper.writeValueAsString(ids));
        JsonNode resp = post("/webapi/entry.cgi", params);
        checkSuccess(resp, "删除失败");
    }

    // ==================== Move ====================

    public void moveFiles(List<String> paths, String destFolderPath) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("api", "SYNO.FileStation.CopyMove");
        params.put("version", "3");
        params.put("method", "start");
        params.put("path", Utils.ObjectMapper.writeValueAsString(paths));
        params.put("dest_folder_path", destFolderPath);
        params.put("remove_src", "true");
        params.put("overwrite", "true");
        JsonNode resp = post("/webapi/entry.cgi", params);
        checkSuccess(resp, "移动文件失败");
    }

    // ==================== HTTP with transparent auth ====================

    private JsonNode post(String path, Map<String, String> params) throws IOException {
        params.put("_sid", getSid());
        JsonNode resp = postRaw(path, params);
        if (isAuthError(resp)) {
            cachedSid = null;
            params.put("_sid", getSid());
            resp = postRaw(path, params);
        }
        return resp;
    }

    private boolean isAuthError(JsonNode resp) {
        if (resp.has("success") && resp.get("success").asBoolean()) return false;
        if (!resp.has("error") || !resp.get("error").has("code")) return false;
        int code = resp.get("error").get("code").asInt();
        return code == 119;
    }

    private synchronized String getSid() throws IOException {
        if (cachedSid != null) return cachedSid;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("api", "SYNO.API.Auth");
        params.put("version", "6");
        params.put("method", "login");
        params.put("account", account);
        params.put("passwd", passwd);
        params.put("session", "VideoStation");
        params.put("format", "sid");
        JsonNode resp = postRaw("/webapi/auth.cgi", params);
        checkSuccess(resp, "登录失败");
        cachedSid = resp.get("data").get("sid").asText();
        return cachedSid;
    }

    private JsonNode postRaw(String path, Map<String, String> params) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                .append('=')
                .append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        URL url = new URL(baseUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bodyBytes);
        }
        byte[] respBytes;
        try {
            respBytes = IOUtils.toByteArray(conn.getInputStream());
        } catch (IOException e) {
            if (conn.getErrorStream() != null) {
                respBytes = IOUtils.toByteArray(conn.getErrorStream());
            } else {
                throw e;
            }
        } finally {
            conn.disconnect();
        }
        return Utils.ObjectMapper.readTree(respBytes);
    }

    private void checkSuccess(JsonNode resp, String msg) throws IOException {
        if (!resp.has("success") || !resp.get("success").asBoolean()) {
            int code = resp.has("error") && resp.get("error").has("code")
                    ? resp.get("error").get("code").asInt() : -1;
            throw new IOException(msg + " (error code: " + code + ")");
        }
    }
}
