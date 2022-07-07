package com.lvt4j.spider4videostation;

import static com.lvt4j.spider4videostation.Consts.ChromeArg_Headless;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * @author LV on 2022年6月30日
 */
@Getter@Setter
@RefreshScope
@Configuration
@ConfigurationProperties
public class Config {

    private String webDriverAddr;
    private boolean webDriverHeadless;
    private List<String> webDriverArgs;

    private List<String> staticWebDriverArgs;
    private long staticLoadTimeoutMillis;
    private String staticChromeDownloadFolder;
    
    private long cacheMaxSize;
    private long cacheExpireDuration;
    
    private String javdbOrigin;
    private String javdbTouchUrl;
    private int javdbTimeoutMillis;
    
    private String doubanDomain;
    private String doubanOrigin;
    private String doubanTouchUrl;
    private String doubanSearchDomain;
    private String doubanSearchMovieUrl;
    private String doubanMovieDomain;
    private String doubanMovieOrigin;
    private String doubanMovieItemPatterm;
    private long doubanLoginWaitTimeoutMillis;
    private List<String> doubanLoginCheckableDomain;
    private String doubanLoginCheckUrl;
    private String doubanLoginUrl;
    private long doubanTimeoutMillis;
    
    
    
    public List<String> getWebDriverArgs() {
        List<String> webDriverArgs = new ArrayList<>(this.webDriverArgs);
        if(webDriverHeadless) webDriverArgs.add(ChromeArg_Headless);
        return webDriverArgs;
    }
    public List<String> getStaticWebDriverArgs() {
        List<String> staticWebDriverArgs = new ArrayList<>(this.staticWebDriverArgs);
        if(webDriverHeadless) staticWebDriverArgs.add(ChromeArg_Headless);
        return staticWebDriverArgs;
    }
    
}