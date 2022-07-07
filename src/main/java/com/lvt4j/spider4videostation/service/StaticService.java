package com.lvt4j.spider4videostation.service;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import java.io.File;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.lvt4j.spider4videostation.Config;
import com.lvt4j.spider4videostation.Utils;

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
    
    private File folder;
    
    @PostConstruct
    private void init() {
        folder = new File(config.getStaticChromeDownloadFolder());
    }
    
    @SneakyThrows
    public synchronized void downAsCache(RemoteWebDriver webDriver, String url) {
        log.info("down static {}", url);
        if(log.isTraceEnabled()) log.trace("open {}", url);
        webDriver.get(url);
        
        File downFile = sendScriptAndWaitFile(webDriver);
        
        cacher.save(url, downFile);
    }
    
    @SneakyThrows
    public synchronized byte[] downCurAsBs(RemoteWebDriver webDriver) {
        String url = webDriver.getCurrentUrl();
        log.info("down static {}", url);
        
        File downFile = sendScriptAndWaitFile(webDriver);
        
        byte[] bs = FileUtils.readFileToByteArray(downFile);
        
        return bs;
    }
    
    @SneakyThrows
    private File sendScriptAndWaitFile(RemoteWebDriver webDriver) {
        if(log.isTraceEnabled()) log.trace("clean down folder {}", folder.getAbsolutePath());
        FileUtils.cleanDirectory(folder);
        
        String url = webDriver.getCurrentUrl();
        
        String script = TplScript.replace("@@OrigUrl@@", url);
        if(log.isTraceEnabled()) log.trace("send down script ：{}", script);
        webDriver.executeScript(script);
        
        if(log.isTraceEnabled()) log.trace("waiting file appear in down folder {}", folder.getAbsolutePath());
        Utils.waitUntil(()->validDownFileCount(folder)==1, config.getStaticLoadTimeoutMillis());
        
        File downFile = folder.listFiles()[0];
        if(log.isTraceEnabled()) log.trace("down file {} exist：{}", downFile.getAbsolutePath(), downFile.exists());
        
        if(!downFile.exists())
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, String.format("download static failed：[%s]", url));
        
        Utils.waitFileNoChange(downFile, config.getStaticLoadTimeoutMillis());
        
        return downFile;
    }
    
    private long validDownFileCount(File folder) {
        return Stream.of(folder.list()).filter(n->!n.endsWith(".crdownload")).count();
    }
    
}