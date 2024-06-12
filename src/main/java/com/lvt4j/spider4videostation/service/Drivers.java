package com.lvt4j.spider4videostation.service;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import org.apache.commons.collections4.map.LazyMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.lang3.tuple.Pair;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.lvt4j.spider4videostation.Config;
import com.lvt4j.spider4videostation.Consts;
import com.lvt4j.spider4videostation.Utils;
import com.lvt4j.spider4videostation.Utils.ThrowableBiFunction;
import com.lvt4j.spider4videostation.Utils.ThrowableConsumer;
import com.lvt4j.spider4videostation.Utils.ThrowableFunction;

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
    
    private Map<Pair<String, Type>, DriverMeta> drivers = LazyMap.lazyMap(new HashMap<>(), (k)->{
        return new DriverMeta(k.getLeft(), k.getRight());
    });
    
    @PreDestroy
    private void destory() {
        drivers.values().forEach(DriverMeta::destory);
    }
    
    public DriverMeta driver(String service, Type type){
        return drivers.get(Pair.of(service, type));
    }
    

//    
//    public void searchDo(ThrowableConsumer<RemoteWebDriver> run) throws Throwable {
//        synchronized(searcher){
//            searcher();
//            run.accept(searcher.driver);
//        }
//    }
//    public <T> T searchExec(String script, ThrowableBiFunction<RemoteWebDriver, String, T> run) throws Throwable {
//        synchronized(searcher){
//            MutableObject<String> rstRef = new MutableObject<>();
//            Utils.retry(()->{
//                searcher();
//                log.trace("searcher exe script : {}", script);
//                rstRef.setValue((String)searcher.driver.executeScript(script));
//            }, (e, n)->{
//                log.warn("searcher exe script {}", script, e);
//                searcher.destory();
//                if(n==1) return true;
//                throw new RuntimeException("searcher exe script err", e);
//            });
//            return run.apply(searcher.driver, rstRef.getValue());
//        }
//    }
//    public <T> T searchOpen(String url, ThrowableBiFunction<RemoteWebDriver, String, T> run) throws Throwable {
//        synchronized(searcher){
//            Utils.retry(()->{
//                searcher();
//                log.trace("searcher open url : {}", url);
//                searcher.driver.get(url);
//            }, (e, n)->{
//                log.warn("searcher open url err {}", url, e);
//                searcher.destory();
//                if(n==1) return true;
//                throw new RuntimeException("searcher open url err", e);
//            });
//            String pageSource = searcher.driver.getPageSource();
//            return run.apply(searcher.driver, pageSource);
//        }
//    }
//
//    public boolean isSearcherInited() {
//        return searcher.driver!=null;
//    }
//
//    private RemoteWebDriver searcher() throws Throwable {
//        if(searcher.driver!=null){
//            searcher.latestTouchTime = System.currentTimeMillis();
//            return searcher.driver;
//        }
//        
//        ChromeOptions options = new ChromeOptions();
//        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
//        options.addArguments(config.getWebDriverArgs());
//        
//        log.info("init searcher {}", config.getWebDriverAddr());
//        RemoteWebDriver driver = searcher.driver = new RemoteWebDriver(new URL(config.getWebDriverAddr()), options);
//        
//        driver.manage().timeouts()
//            .implicitlyWait(config.getWebDriverSearcherTimeoutMillis(), TimeUnit.MILLISECONDS)
//            .setScriptTimeout(config.getWebDriverSearcherTimeoutMillis(), TimeUnit.MILLISECONDS)
//            .pageLoadTimeout(config.getWebDriverSearcherTimeoutMillis(), TimeUnit.MILLISECONDS);
//        
//        initCookie(driver);
//        
//        return driver;
//    }
//    public <T> T staticOpen(String url, ThrowableFunction<RemoteWebDriver, T> func) throws Throwable {
//        synchronized(staticer){
//            Utils.retry(()->{
//                staticer();
//                log.trace("staticer open url : {}", url);
//                staticer.driver.get(url);
//            }, (e, n)->{
//                log.warn("staticer open url err {}", url, e);
//                staticer.destory();
//                if(n==1) return true;
//                throw new RuntimeException("staticer open url err", e);
//            });
//            return func.apply(staticer.driver);
//        }
//    }
//    public void staticerDestory() {
//        staticer.destory();
//    }
//    private RemoteWebDriver staticer() throws Throwable {
//        if(staticer.driver!=null){
//            staticer.latestTouchTime = System.currentTimeMillis();
//            return staticer.driver;
//        }
//        
//        ChromeOptions options = new ChromeOptions();
//        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
//        options.addArguments(config.getStaticWebDriverArgs());
//        
//        log.info("init staticer {}", config.getWebDriverAddr());
//        RemoteWebDriver driver = staticer.driver = new RemoteWebDriver(new URL(config.getWebDriverAddr()), options);
//        
//        driver.manage().timeouts()
//            .implicitlyWait(config.getWebDriverStaticerTimeoutMillis(), TimeUnit.MILLISECONDS)
//            .setScriptTimeout(config.getWebDriverStaticerTimeoutMillis(), TimeUnit.MILLISECONDS)
//            .pageLoadTimeout(config.getWebDriverStaticerTimeoutMillis(), TimeUnit.MILLISECONDS);
//        
//        initCookie(driver);
//        
//        driver.get("about:blank");
//        
//        return driver;
//    }
    
//    private void initCookie(RemoteWebDriver driver) {
//        initJavdb(driver);
//        initDouban(driver);
//    }
//
//    private void initJavdb(RemoteWebDriver driver) {
//        if(log.isTraceEnabled()) log.trace("open javdb touchUrl : {}", config.getJavdbTouchUrl());
//        driver.get(config.getJavdbTouchUrl());
//        Date cookieExpire = new Date(System.currentTimeMillis()+365*24*60*60*1000);
//        driver.manage().addCookie(new Cookie("list_mode", "v",    null, "/", cookieExpire));
//        driver.manage().addCookie(new Cookie("over18",    "1",    null, "/", cookieExpire));
//        driver.manage().addCookie(new Cookie("locale",    "zh",   null, "/", cookieExpire));
//        driver.manage().addCookie(new Cookie("theme",     "auto", null, "/", cookieExpire));
//    }
//
//    private void initDouban(RemoteWebDriver driver) {
//        if(log.isTraceEnabled()) log.trace("open douban touchUrl : {}", config.getDoubanTouchUrl());
//        driver.get(config.getDoubanTouchUrl());
//        File cookiesFile = new File(Folder_Cookies, config.getDoubanDomain());
//        loadCookie(driver, cookiesFile);
//    }

    public void loadCookie(RemoteWebDriver driver, File cookiesFile) {
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
        drivers.values().forEach(DriverMeta::keepAlive);
    }

    
    public enum Type {
        Searcher,
        Staticer;
    }
    
    @RequiredArgsConstructor
    public class DriverMeta {
        private final String service;
        private final Type type;
        private ThrowableConsumer<ChromeOptions> optionsCustomizer;
        private ThrowableConsumer<RemoteWebDriver> driverCustomizer;
        
        private RemoteWebDriver driver;
        private long latestTouchTime;
        
        private String mainWindowHandler;
        
        public void optionsCustom(ThrowableConsumer<ChromeOptions> customizer){
            optionsCustomizer = customizer;
        }

        public void driverCustom(ThrowableConsumer<RemoteWebDriver> customizer){
            driverCustomizer = customizer;
        }
        
        public boolean isInited() {
            return driver!=null;
        }
        
        private synchronized RemoteWebDriver driver() throws Throwable {
            if(driver!=null){
                latestTouchTime = System.currentTimeMillis();
                return driver;
            }
            
            log.info("init driver : {} {}", service, type);
            ChromeOptions options = new ChromeOptions();
            switch (type){
            case Searcher:
                options.addArguments(config.getWebDriverArgs());
                break;
            case Staticer:
                options.addArguments(config.getStaticWebDriverArgs());
                break;
            }
            if(optionsCustomizer!=null) optionsCustomizer.accept(options);
            
            driver = new RemoteWebDriver(new URL(config.getWebDriverAddr()), options);
            
            switch (type){
            case Searcher:
                driver.manage().timeouts()
                    .implicitlyWait(config.getWebDriverSearcherTimeoutMillis(), TimeUnit.MILLISECONDS)
                    .setScriptTimeout(config.getWebDriverSearcherTimeoutMillis(), TimeUnit.MILLISECONDS)
                    .pageLoadTimeout(config.getWebDriverSearcherTimeoutMillis(), TimeUnit.MILLISECONDS);
                break;
            case Staticer:
                driver.manage().timeouts()
                    .implicitlyWait(config.getWebDriverStaticerTimeoutMillis(), TimeUnit.MILLISECONDS)
                    .setScriptTimeout(config.getWebDriverStaticerTimeoutMillis(), TimeUnit.MILLISECONDS)
                    .pageLoadTimeout(config.getWebDriverStaticerTimeoutMillis(), TimeUnit.MILLISECONDS);
                break;
            }
            
            if(driverCustomizer!=null) driverCustomizer.accept(driver);
            
            mainWindowHandler = driver.getWindowHandle();
            return driver;
        }
        
        public synchronized <T> T open(String url, ThrowableBiFunction<RemoteWebDriver, String, T> run) throws Throwable {
            Utils.retry(()->{
                log.trace("{} {} open url : {}", service, type, url);
                driver().get(url);
            }, (e, n)->{
                log.warn("{} {} open url err {}", service, type, url, e);
                destory();
                if(n==1) return true;
                throw new RuntimeException("open url err", e);
            });
            String pageSource = driver.getPageSource();
            return run.apply(driver, pageSource);
        }
        public synchronized <T> T open(String url, ThrowableFunction<RemoteWebDriver, T> func) throws Throwable {
            Utils.retry(()->{
                log.trace("{} {} open url : {}", service, type, url);
                driver().get(url);
            }, (e, n)->{
                log.warn("{} {} open url err {}", service, type, url, e);
                destory();
                if(n==1) return true;
                throw new RuntimeException("open url err", e);
            });
            return func.apply(driver);
        }
        public synchronized <T> T exec(String script, ThrowableBiFunction<RemoteWebDriver, String, T> run) throws Throwable {
            MutableObject<String> rstRef = new MutableObject<>();
            Utils.retry(()->{
                log.trace("{} {} exe script : {}", service, type, script);
                rstRef.setValue((String)driver().executeScript(script));
            }, (e, n)->{
                log.warn("{} {} exe script {}", service, type, script, e);
                destory();
                if(n==1) return true;
                throw new RuntimeException("exe script err", e);
            });
            return run.apply(driver, rstRef.getValue());
        }
        public synchronized void _do(ThrowableConsumer<RemoteWebDriver> run) throws Throwable {
            run.accept(driver());
        }
        
        public synchronized void switchToNewWindow(){
            for(String handle : driver.getWindowHandles()) {
                if(handle.equals(mainWindowHandler)) continue;
                driver.switchTo().window(handle);
                break;
            }
        }
        
        public synchronized void reduceToMainWindow(){
            for(String handle : driver.getWindowHandles()) {
                if(handle.equals(mainWindowHandler)) continue;
                driver.switchTo().window(handle).close();
            }
            driver.switchTo().window(mainWindowHandler);
        }
        
        private synchronized void keepAlive() {
            try{
                if(driver==null) return;
                if(driver==null) return;
                if(System.currentTimeMillis()-latestTouchTime>Consts.WebDriverHeartbeatGap) return;
                if(log.isTraceEnabled()) log.trace("{} {} heartbeat", service, type);
                driver.executeScript("console.log('heartbeat')");
                latestTouchTime = System.currentTimeMillis();
            }catch(Throwable e){
                log.warn("heartbeat for driver [{} {}] err", service, type, e);
            }
        }
        
        public synchronized void destory() {
            if(driver==null) return;
            try{
                driver.quit();
            }catch(Exception ig){
            }finally {
                driver = null;
            }
        }
    }
    
}