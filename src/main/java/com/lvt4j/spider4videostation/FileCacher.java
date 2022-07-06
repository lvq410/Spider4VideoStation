package com.lvt4j.spider4videostation;

import java.io.File;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author LV on 2022年7月2日
 */
@Slf4j
@Service
public class FileCacher {

    @Value("${cache.expireInterval:3600000}")
    private long expireInterval;
    
    private static File rootFolder = new File("cache");
    
    static {
        rootFolder.mkdirs();
    }
    
    public boolean exist(String url) {
        return exist(cacheFile(url));
    }
    private boolean exist(File cacheFile) {
        if(!cacheFile.exists()) return false;
        if(System.currentTimeMillis()-cacheFile.lastModified()>expireInterval){
            cacheFile.delete();
            return false;
        }
        return true;
    }
    
    @SneakyThrows
    public String loadAsStr(String url) {
        byte[] cnt = load(url);
        if(cnt==null) return null;
        return new String(cnt);
    }
    
    @SneakyThrows
    public byte[] load(String url) {
        File cacheFile = cacheFile(url);
        if(!exist(cacheFile)) return null;
        
        return FileUtils.readFileToByteArray(cacheFile);
    }
    
    @SneakyThrows
    public void saveAsStr(String url, String cnt) {
        save(url, cnt.getBytes());
    }
    @SneakyThrows
    public void save(String url, byte[] cnt) {
        File cacheFile = cacheFile(url);
        FileUtils.deleteQuietly(cacheFile);
        FileUtils.writeByteArrayToFile(cacheFile, cnt);
    }
    @SneakyThrows
    public void save(String url, File origFile) {
        if(log.isTraceEnabled()) log.trace("save cache {}", url);
        File cacheFile = cacheFile(url);
        FileUtils.deleteQuietly(cacheFile);
        origFile.renameTo(cacheFile);
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