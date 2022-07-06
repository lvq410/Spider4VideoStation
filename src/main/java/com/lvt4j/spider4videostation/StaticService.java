package com.lvt4j.spider4videostation;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import java.io.File;
import java.net.URL;
import java.net.URLDecoder;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author LV on 2022年7月5日
 */
@Slf4j
@Service
public class StaticService {

    private static final String TplScript = 
            "var body = document.getElementsByTagName('body')[0];" + 
            "var a = document.createElement('a');" + 
            "a.setAttribute('href', '@@OrigUrl@@');" + 
            "a.setAttribute('download', '');" + 
            "body.appendChild(a);" + 
            "a.click();";
    
    @Autowired
    private Config config;
    @Autowired
    private FileCacher cacher;
    
    @SneakyThrows
    public synchronized void downAsCache(RemoteWebDriver webDriver, String url) {
        File folder = new File(config.getStaticChromeDownloadFolder());
        if(log.isTraceEnabled()) log.trace("clean down folder {}", folder.getAbsolutePath());
        FileUtils.cleanDirectory(folder);
        
        log.info("down static {}", url);
        if(log.isTraceEnabled()) log.trace("open {}", url);
        webDriver.get(url);
        
        String script = TplScript.replace("@@OrigUrl@@", url);
        if(log.isTraceEnabled()) log.trace("send down script ：{}", script);
        webDriver.executeScript(script);
        
        if(log.isTraceEnabled()) log.trace("waiting file appear in down folder {}", folder.getAbsolutePath());
        Utils.waitUntil(()->validDownFileCount(folder)>0, config.getStaticLoadTimeoutMillis());
        
        File downFile = null;
        if(folder.list().length==1){ //下载文件夹中只有一个文件时，则其必然刚好是刚下载的文件
            downFile = folder.listFiles()[0];
        }else{ //有多个文件时，尝试根据文件名寻找
            String downFileNameInUrl = FilenameUtils.getName(new URL(url).getPath());
            if(StringUtils.isNotBlank(downFileNameInUrl)){
                downFileNameInUrl = URLDecoder.decode(downFileNameInUrl, "utf8");
            }
            downFile = new File(folder, downFileNameInUrl);
        }
        if(log.isTraceEnabled()) log.trace("down file {} exist：{}", downFile.getAbsolutePath(), downFile.exists());
        
        if(!downFile.exists())
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, String.format("download static failed：[%s]", url));
        
        Utils.waitFileNoChange(downFile, config.getStaticLoadTimeoutMillis());
        
        cacher.save(url, downFile);
    }
    
    private long validDownFileCount(File folder) {
        return Stream.of(folder.list()).filter(n->!n.endsWith(".crdownload")).count();
    }
    
}