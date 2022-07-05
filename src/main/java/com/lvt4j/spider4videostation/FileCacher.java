package com.lvt4j.spider4videostation;

import java.io.File;
import java.net.URL;

import javax.annotation.PostConstruct;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.SneakyThrows;

/**
 *
 * @author LV on 2022年7月2日
 */
@Service
public class FileCacher {

    @Value("${cache.expireInterval:3600000}")
    private long expireInterval;
    
    private static File rootFolder = new File("cache");
    
    static {
        rootFolder.mkdirs();
    }
    
    @PostConstruct
    private void init() {
    }
    
    @SneakyThrows
    public String loadCacheAsStr(String url) {
        byte[] cnt = loadCache(url);
        if(cnt==null) return null;
        return new String(cnt);
    }
    
    @SneakyThrows
    public byte[] loadCache(String url) {
        File cacheFile = cacheFile(url);
        if(!cacheFile.exists()) return null;
        
        if(System.currentTimeMillis()-cacheFile.lastModified()>expireInterval){
            cacheFile.delete();
            return null;
        }
        
        return FileUtils.readFileToByteArray(cacheFile);
    }
    
    @SneakyThrows
    public void saveCacheAsStr(String url, String cnt) {
        saveCache(url, cnt.getBytes());
    }
    @SneakyThrows
    public void saveCache(String url, byte[] cnt) {
        FileUtils.writeByteArrayToFile(cacheFile(url), cnt);
    }
    
    @SneakyThrows
    static File cacheFile(String url) {
        URL u = new URL(url);
        String domain = u.getHost();
        
        File domainFolder = new File(rootFolder, domain);
        domainFolder.mkdirs();
        
        String path = u.getPath();
        while(path.startsWith("/")) path = path.substring(1);
        
        File ctxFolder = new File(domainFolder, path).getParentFile(); ctxFolder.mkdirs();
       
        String name = FilenameUtils.getName(path);
        
        if(StringUtils.isNotBlank(u.getQuery())) name += "？"+u.getQuery();
        name = name.replaceAll("[\t]", " ").replaceAll("[/]", "／")
                .replaceAll("[\\\\]", "＼").replaceAll("[:]", "：").replaceAll("[\\*]", "＊")
                .replaceAll("[\\?]", "？").replaceAll("[\\\"]", "＂").replaceAll("[<]", "＜")
                .replaceAll("[>]", "＞").replaceAll("[|]", "｜");
        
        name += ".cache";
        
        return new File(ctxFolder, name);
    }
    
}