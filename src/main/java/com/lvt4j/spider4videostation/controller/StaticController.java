package com.lvt4j.spider4videostation.controller;

import java.net.URL;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.lvt4j.spider4videostation.Config;
import com.lvt4j.spider4videostation.Consts;
import com.lvt4j.spider4videostation.Utils;
import com.lvt4j.spider4videostation.service.FileCacher;
import com.lvt4j.spider4videostation.service.StaticService;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * 没有较强防抓包的静态资源网站的资源下载
 * @author LV on 2022年7月2日
 */
@Slf4j
@RestController("static")
@RequestMapping("static")
public class StaticController {

    @Autowired
    private Config config;
    @Autowired
    private FileCacher cacher;
    
    @Autowired
    private StaticService service;
    
    private RemoteWebDriver webDriver;
    private long latestWebDriverTouchTime;
    
    @PreDestroy
    private void destory() {
        if(webDriver==null) return;
        try{
            webDriver.quit();
        }catch(Exception ig){
            log.warn("err on web quit {}", ig);
        }finally {
            webDriver = null;
        }
    }
    
    @GetMapping
    public void proxy(HttpServletResponse response,
            @RequestParam String url,
            @RequestParam(required=false) String mediaType) throws Throwable {
        byte[] bs = cacher.load(url);
        if(bs!=null){
            responseData(url, mediaType, bs, response);
            return;
        }
        
        synchronized(this){
            Utils.retry(()->{
                driver();
                service.downAsCache(webDriver, url);
            }, (e, n)->{
                log.error("err on web load : {}", url, e);
                destory();
                if(n==1) return true;
                throw new RuntimeException("err on web load", e);
            });
        }
        
        Utils.waitUntil(()->cacher.exist(url), config.getStaticLoadTimeoutMillis());
        bs = cacher.load(url);
        
        if(bs!=null){
            responseData(url, mediaType, bs, response);
            return;
        }else{
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
    @SneakyThrows
    private synchronized RemoteWebDriver driver() {
        if(webDriver!=null) {
            latestWebDriverTouchTime = System.currentTimeMillis();
            return webDriver;
        }
        
        ChromeOptions options = new ChromeOptions();
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        options.addArguments(config.getStaticWebDriverArgs());
        
        log.info("init webdriver {}", config.getWebDriverAddr());
        webDriver = new RemoteWebDriver(new URL(config.getWebDriverAddr()), options);
        
        webDriver.manage().timeouts()
            .pageLoadTimeout(config.getStaticLoadTimeoutMillis(), TimeUnit.MILLISECONDS)
            .setScriptTimeout(config.getStaticLoadTimeoutMillis(), TimeUnit.MILLISECONDS);
        
        return webDriver;
    }
    @SneakyThrows
    private void responseData(String url, String mediaTypeStr, byte[] bs, HttpServletResponse response) {
        MediaType mediaType = null;
        try{
            if(StringUtils.isNotBlank(mediaTypeStr)) mediaType = MediaType.valueOf(mediaTypeStr);
        }catch(Exception ig){}
        if(mediaType==null) mediaType = MediaTypeFactory.getMediaType(FilenameUtils.getName(url))
            .orElse(MediaType.APPLICATION_OCTET_STREAM);
        response.setContentType(mediaType.toString());
        response.setContentLength(bs.length);
        IOUtils.write(bs, response.getOutputStream());
    }
    @Scheduled(cron="0 * * * * ?")
    public synchronized void driverKeepAlive() {
        if(webDriver==null) return;
        if(System.currentTimeMillis()-latestWebDriverTouchTime<Consts.WebDriverHeartbeatGap) return;
        if(log.isTraceEnabled()) log.trace("webdriver heartbeat");
        webDriver.executeScript("console.log('heartbeat')");
        latestWebDriverTouchTime = System.currentTimeMillis();
    }
    
    public String jpgWrap(String publishPrefix, String url) {
        return staticWrap(publishPrefix, url, MediaType.IMAGE_JPEG_VALUE);
    }
    private String staticWrap(String publishPrefix, String url, String mediaType) {
        return UriComponentsBuilder.fromHttpUrl(publishPrefix)
                .path("static")
                .queryParam("url", url)
                .queryParam("mediaType", mediaType)
                .toUriString();
    }
    
}