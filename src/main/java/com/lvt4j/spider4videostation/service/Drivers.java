package com.lvt4j.spider4videostation.service;

import static com.lvt4j.spider4videostation.Consts.Folder_Cookies;

import java.io.File;
import java.net.URL;
import java.util.Date;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.lvt4j.spider4videostation.Config;
import com.lvt4j.spider4videostation.Consts;
import com.lvt4j.spider4videostation.Utils;
import com.lvt4j.spider4videostation.Utils.ThrowableBiConsumer;
import com.lvt4j.spider4videostation.Utils.ThrowableConsumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author LV on 2022年7月9日
 */
@Slf4j
@Service
public class Drivers {

    @Autowired
    private Config config;
    
    private DriverMeta searcher = new DriverMeta("searcher");
    private DriverMeta staticer = new DriverMeta("staticer");
    
    @PreDestroy
    private void destory() {
        searcher.destory();
        staticer.destory();
    }
    
    public void search(ThrowableConsumer<RemoteWebDriver> run) throws Throwable {
        synchronized(searcher){
            searcher();
            run.accept(searcher.driver);
        }
    }

    public String search(String url, ThrowableBiConsumer<RemoteWebDriver, String> run) throws Throwable {
        String pageSource;
        synchronized(searcher){
            Utils.retry(()->{
                searcher();
                log.trace("searcher open url : {}", url);
                searcher.driver.get(url);
            }, (e, n)->{
                log.error("searcher open url err {}", url, e);
                searcher.destory();
                if(n==1) return true;
                throw new RuntimeException("searcher open url err", e);
            });
            pageSource = searcher.driver.getPageSource();
            run.accept(searcher.driver, pageSource);
        }
        return pageSource;
    }

    public boolean isSearcherInited() {
        return searcher.driver!=null;
    }

    private RemoteWebDriver searcher() throws Throwable {
        if(searcher.driver!=null){
            searcher.latestTouchTime = System.currentTimeMillis();
            return searcher.driver;
        }
        
        ChromeOptions options = new ChromeOptions();
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        options.addArguments(config.getWebDriverArgs());
        
        log.info("init searcher {}", config.getWebDriverAddr());
        RemoteWebDriver driver = searcher.driver = new RemoteWebDriver(new URL(config.getWebDriverAddr()), options);
        
        driver.manage().timeouts()
            .implicitlyWait(10, TimeUnit.SECONDS)
            .setScriptTimeout(config.getWebDriverSearcherTimeoutMillis(), TimeUnit.MILLISECONDS)
            .pageLoadTimeout(config.getWebDriverSearcherTimeoutMillis(), TimeUnit.MILLISECONDS);
        
        initCookie(driver);
        
        return driver;
    }
    public void staticOpen(String url, ThrowableConsumer<RemoteWebDriver> run) throws Throwable {
        synchronized(staticer){
            Utils.retry(()->{
                staticer();
                log.trace("staticer open url : {}", url);
                staticer.driver.get(url);
            }, (e, n)->{
                log.error("staticer open url err {}", url, e);
                staticer.destory();
                if(n==1) return true;
                throw new RuntimeException("staticer open url err", e);
            });
            run.accept(staticer.driver);
        }
    }
    public void staticerDestory() {
        staticer.destory();
    }

    private RemoteWebDriver staticer() throws Throwable {
        if(staticer.driver!=null){
            staticer.latestTouchTime = System.currentTimeMillis();
            return staticer.driver;
        }
        
        ChromeOptions options = new ChromeOptions();
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        options.addArguments(config.getStaticWebDriverArgs());
        
        log.info("init staticer {}", config.getWebDriverAddr());
        RemoteWebDriver driver = staticer.driver = new RemoteWebDriver(new URL(config.getWebDriverAddr()), options);
        
        driver.manage().timeouts()
            .implicitlyWait(10, TimeUnit.SECONDS)
            .setScriptTimeout(config.getWebDriverStaticerTimeoutMillis(), TimeUnit.MILLISECONDS)
            .pageLoadTimeout(config.getWebDriverStaticerTimeoutMillis(), TimeUnit.MILLISECONDS);
        
        initCookie(driver);
        
        return driver;
    }
    
    private void initCookie(RemoteWebDriver driver) {
        initJavdb(driver);
        initDouban(driver);
    }

    private void initJavdb(RemoteWebDriver driver) {
        if(log.isTraceEnabled()) log.trace("open javdb touchUrl : {}", config.getJavdbTouchUrl());
        driver.get(config.getJavdbTouchUrl());
        Date cookieExpire = new Date(System.currentTimeMillis()+365*24*60*60*1000);
        driver.manage().addCookie(new Cookie("list_mode", "v",    null, "/", cookieExpire));
        driver.manage().addCookie(new Cookie("over18",    "1",    null, "/", cookieExpire));
        driver.manage().addCookie(new Cookie("locale",    "zh",   null, "/", cookieExpire));
        driver.manage().addCookie(new Cookie("theme",     "auto", null, "/", cookieExpire));
    }

    private void initDouban(RemoteWebDriver driver) {
        if(log.isTraceEnabled()) log.trace("open douban touchUrl : {}", config.getDoubanTouchUrl());
        driver.get(config.getDoubanTouchUrl());
        File cookiesFile = new File(Folder_Cookies, config.getDoubanDomain());
        loadCookie(driver, cookiesFile);
    }

    private void loadCookie(RemoteWebDriver driver, File cookiesFile) {
        if(!cookiesFile.exists()) return;
        if(log.isTraceEnabled()) log.trace("cookiesFile[{}] exist, try init cookies", cookiesFile.getPath());
        HashSet<Cookie> cookies;
        try{
            cookies = SerializationUtils.deserialize(FileUtils.readFileToByteArray(cookiesFile));
        }catch(Exception ig){
            log.warn("deserialize cookies file err", ig);
            FileUtils.deleteQuietly(cookiesFile);
            return;
        }
        for(Cookie cookie : cookies){
            try{
                driver.manage().addCookie(cookie);
                if(log.isTraceEnabled()) log.trace("init cookie : {}", cookie);
            }catch(Exception e){
                if(log.isTraceEnabled()) log.trace("init cookie ignore : {}", cookie, e);
            }
        }
    }

    @Scheduled(cron="0/30 * * * * ?")
    public synchronized void driverKeepAlive() {
        driverKeepAlive(searcher);
        driverKeepAlive(staticer);
    }
    private void driverKeepAlive(DriverMeta meta) {
        try{
            if(meta.driver==null) return;
            synchronized(meta){
                if(meta.driver==null) return;
                if(System.currentTimeMillis()-meta.latestTouchTime<Consts.WebDriverHeartbeatGap) return;
                if(log.isTraceEnabled()) log.trace("{} heartbeat", meta.name);
                meta.driver.executeScript("console.log('heartbeat')");
                meta.latestTouchTime = System.currentTimeMillis();
            }
        }catch(Throwable e){
            log.warn("heartbeat for driver [%s] err", meta.name, e);
        }
    }
    
    @RequiredArgsConstructor
    class DriverMeta {
        private final String name;
        private RemoteWebDriver driver;
        private long latestTouchTime;
        
        public synchronized void destory() {
            if(driver==null) return;
            try{
                driver.quit();
            }catch(Exception e){
            }finally {
                driver = null;
            }
        }
    }
    
}