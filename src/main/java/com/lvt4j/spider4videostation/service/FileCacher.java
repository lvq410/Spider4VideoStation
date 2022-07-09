package com.lvt4j.spider4videostation.service;

import static com.lvt4j.spider4videostation.Consts.Folder_Cache;

import java.io.File;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.lvt4j.spider4videostation.Config;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author LV on 2022年7月2日
 */
@Slf4j
@Service
@ManagedResource(objectName="!cache:type=FileCacher")
public class FileCacher {

    @Autowired
    private Config config;
    
    public boolean exist(String url) {
        return exist(cacheFile(url));
    }
    private boolean exist(File cacheFile) {
        if(!cacheFile.exists()) return false;
        if(System.currentTimeMillis()-cacheFile.lastModified()>config.getCacheExpireDuration()){
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
        if(log.isTraceEnabled()) log.trace("save cache {}", url);
        File cacheFile = cacheFile(url);
        FileUtils.deleteQuietly(cacheFile);
        FileUtils.writeByteArrayToFile(cacheFile, cnt);
    }
    @SneakyThrows
    public File save(String url, File origFile) {
        if(log.isTraceEnabled()) log.trace("save cache {}", url);
        File cacheFile = cacheFile(url);
        FileUtils.deleteQuietly(cacheFile);
        origFile.renameTo(cacheFile);
        return cacheFile;
    }
    
    @SneakyThrows
    static File cacheFile(String url) {
        URL u = new URL(url);
        String domain = u.getHost();
        
        File domainFolder = new File(Folder_Cache, domain);
        domainFolder.mkdirs();
        
        String path = u.getPath();
        while(path.startsWith("/")) path = path.substring(1);
        if(path.endsWith("/")) path += ".cache";
        
        File ctxFolder = new File(domainFolder, path).getParentFile(); ctxFolder.mkdirs();
       
        String name = FilenameUtils.getName(path);
        
        if(StringUtils.isNotBlank(u.getQuery())) name += "？"+u.getQuery();
        name = name.replaceAll("[\t]", " ").replaceAll("[/]", "／")
                .replaceAll("[\\\\]", "＼").replaceAll("[:]", "：").replaceAll("[\\*]", "＊")
                .replaceAll("[\\?]", "？").replaceAll("[\\\"]", "＂").replaceAll("[<]", "＜")
                .replaceAll("[>]", "＞").replaceAll("[|]", "｜");
        
        if(!name.endsWith(".cache")) name += ".cache";
        
        return new File(ctxFolder, name);
    }
    
    @ManagedOperation
    @Scheduled(cron="0 0 0 * * ?")
    public void scheduleClean() {
        if(config.getCacheMaxSize()<=0) return;
        List<File> all = new LinkedList<>();
        listFolderFiles(all, Folder_Cache);
        all.sort((f1,f2)->Long.compare(f1.lastModified(), f2.lastModified()));
        long size = all.stream().map(f->f.length()).reduce(0L, (a,b)->a+b);
        while(size>config.getCacheMaxSize() && all.size()>0){
            File file = all.remove(0);
            size -= file.length();
            deleteFileParently(file);
        }
    }
    @ManagedOperation
    public long clean() throws Exception {
        long size = FileUtils.sizeOf(Folder_Cache);
        FileUtils.cleanDirectory(Folder_Cache);
        return size;
    }
    
    private void deleteFileParently(File file) {
        file.delete();
        File folder = file.getParentFile();
        if(folder.list().length==0) deleteFileParently(folder);
    }
    private void listFolderFiles(List<File> rsts, File folder) {
        for(File file : folder.listFiles()){
            if(file.isDirectory()){
                listFolderFiles(rsts, file);
            }else{
                rsts.add(file);
            }
        }
    }
    
}