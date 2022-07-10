package com.lvt4j.spider4videostation.service;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import java.io.File;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.lvt4j.spider4videostation.Config;
import com.lvt4j.spider4videostation.Utils;

import lombok.extern.slf4j.Slf4j;

/**
 * 浏览器真实发起下载实现下载静态资源
 * @author LV on 2022年7月5日
 */
@Slf4j
@Service
@ConditionalOnProperty(name="staticMode",havingValue="chromeDown")
class StaticServiceByChromeDown implements StaticService {

    private static final String Tpl_Script = Utils.res("/script/tpl_down_a.js");
    
    @Autowired
    private Config config;
    @Autowired
    private Drivers drivers;
    
    private File folder;
    
    @PostConstruct
    private void init() {
        folder = new File(config.getStaticChromeDownloadFolder());
        folder.mkdirs();
    }
    
    @Override
    public byte[] down(String url) throws Throwable {
        log.info("downloading static {}", url);
        synchronized(folder){
            return drivers.staticOpen(url, driver->{
                return sendScriptAndWaitFile(driver);
            });
        }
    }
    
//    @SneakyThrows
//    public synchronized byte[] downCurAsBs(RemoteWebDriver webDriver) {
//        String url = webDriver.getCurrentUrl();
//        log.info("down static {}", url);
//        
//        File downFile = sendScriptAndWaitFile(webDriver);
//        
//        byte[] bs = FileUtils.readFileToByteArray(downFile);
//        
//        return bs;
//    }
    
    private byte[] sendScriptAndWaitFile(RemoteWebDriver webDriver) throws Throwable {
        if(log.isTraceEnabled()) log.trace("clean down folder {}", folder.getAbsolutePath());
        FileUtils.cleanDirectory(folder);
        
        String url = webDriver.getCurrentUrl();
        
        String script = Tpl_Script.replace("@@OrigUrl@@", url);
        if(log.isTraceEnabled()) log.trace("send down script");
        webDriver.executeScript(script);
        
        if(log.isTraceEnabled()) log.trace("waiting file appear in down folder {}", folder.getAbsolutePath());
        Utils.waitUntil(()->validDownFileCount(folder)==1, config.getStaticLoadTimeoutMillis());
        
        File downFile = folder.listFiles()[0];
        if(log.isTraceEnabled()) log.trace("down file {} exist：{}", downFile.getAbsolutePath(), downFile.exists());
        
        if(!downFile.exists())
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, String.format("download static failed：[%s]", url));
        
        Utils.waitFileNoChange(downFile, config.getStaticLoadTimeoutMillis());
        
        byte[] bs = FileUtils.readFileToByteArray(downFile);
        downFile.delete();
        return bs;
    }
    
    private long validDownFileCount(File folder) {
        return Stream.of(folder.list()).filter(n->!n.endsWith(".crdownload")).count();
    }
    
}