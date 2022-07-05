package com.lvt4j.spider4videostation;

import java.net.URL;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author LV on 2022年7月2日
 */
@Slf4j
@RestController("static")
@RequestMapping("static")
public class StaticProxyController {

    @Autowired
    private Config config;
    @Autowired
    private FileCacher cacher;
    
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
            @RequestParam String url) throws Throwable {
        byte[] bs = cacher.loadCache(url);
        if(bs!=null){
            responseData(url, bs, response);
            return;
        }
        
        String saveUrl = UriComponentsBuilder.fromHttpUrl(config.getStaticSaveUrl())
            .queryParam("url", url)
            .build().toUriString();
        
        synchronized (this) {
            Utils.retry(()->{
                driver().get(saveUrl);
            }, (e, n)->{
                log.error("err on web load {}", url, e);
                destory();
                if(n==1) return true;
                throw new RuntimeException("err on web load", e);
            });
            
            long waitedTime = 0;
            while(bs==null && waitedTime<config.getStaticLoadTimeoutMillis()){
                Thread.sleep(100);
                waitedTime += 100;
                bs = cacher.loadCache(url);
            }
        }
        
        if(bs!=null){
            responseData(url, bs, response);
            return;
        }else{
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
    @SneakyThrows
    private synchronized WebDriver driver() {
        if(webDriver!=null) {
            latestWebDriverTouchTime = System.currentTimeMillis();
            return webDriver;
        }
        
        ChromeOptions options = new ChromeOptions();
        options.addArguments(config.getStaticWebDriverArgs());
        
        log.info("init webdriver {}", config.getWebDriverAddr());
        webDriver = new RemoteWebDriver(new URL(config.getWebDriverAddr()), options);
        
        webDriver.manage().timeouts()
            .pageLoadTimeout(config.getStaticLoadTimeoutMillis(), TimeUnit.MILLISECONDS)
            .setScriptTimeout(config.getStaticLoadTimeoutMillis(), TimeUnit.MILLISECONDS);
        
        return webDriver;
    }
    @SneakyThrows
    private void responseData(String url, byte[] bs, HttpServletResponse response) {
        MediaType mediaType = MediaTypeFactory.getMediaType(FilenameUtils.getName(url))
            .orElse(MediaType.APPLICATION_OCTET_STREAM);
        response.setContentType(mediaType.toString());
        response.setContentLength(bs.length);
        IOUtils.write(bs, response.getOutputStream());
    }
    @Scheduled(cron="0/5 * * * * ?")
    public synchronized void driverKeepAlive() {
        if(webDriver==null) return;
        if(System.currentTimeMillis()-latestWebDriverTouchTime<Consts.WebDriverHeartbeatGap) return;
        if(log.isTraceEnabled()) log.trace("webdriver heartbeat");
        webDriver.executeScript("console.log('heartbeat')");
        latestWebDriverTouchTime = System.currentTimeMillis();
    }
    
    @PostMapping("saveAs64")
    public void saveAs64(
            @RequestParam String url,
            @RequestBody String b64) {
        byte[] bs = Base64.getDecoder().decode(b64);
        log.info("cache static {} {}", url, bs.length);
        cacher.saveCache(url, bs);
    }
}