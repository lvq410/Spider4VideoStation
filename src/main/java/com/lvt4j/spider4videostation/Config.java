package com.lvt4j.spider4videostation;

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
    private List<String> webDriverArgs;
    public long webDriverSearcherTimeoutMillis;
    public long webDriverStaticerTimeoutMillis;

    private List<String> staticWebDriverArgs;
    private long staticLoadTimeoutMillis;
    private String staticChromeDownloadFolder;
    
    private long cacheMaxSize;
    private long cacheExpireDuration;
    
    private int fileEpOffset;
    private int siteEpOffset;
    
    private String originalAvailable;
    
    private String javdbDomain;
    private String javdbOrigin;
    private String javdbTouchUrl;
//    private int javdbTimeoutMillis;
    private String javdbProxy;
    
    private String doubanDomain;
    private String doubanOrigin;
    private String doubanTouchUrl;
    private String doubanSearchDomain;
    private String doubanSearchMovieUrl;
    private String doubanMovieDomain;
    private String doubanMovieOrigin;
    private String doubanMovieItemPattern;
    private long doubanLoginWaitTimeoutMillis;
    private List<String> doubanLoginCheckableDomain;
    private String doubanLoginCheckUrl;
    private String doubanLoginUrl;
//    private long doubanTimeoutMillis;
    private int doubanMaxLimit;
    
    private String baikeBaiduDomain;
    private String baikeBaiduOrigin;
    private String baikeBaiduSearchUrl;
//    private long baikeBaiduTimeoutMillis;
    private int baikeBaiduMaxLimit;
    
    public Integer fileEpIdx2SiteEpIdx(Integer fileEpIdx) {
        if(fileEpIdx==null) return null;
        int standardEpIdx = fileEpIdx + fileEpOffset;
        int siteEpIdx = standardEpIdx - siteEpOffset;
        return siteEpIdx;
    }
    
    public Integer siteEpIdx2FileEpIdx(Integer siteEpIdx) {
        if(siteEpIdx==null) return null;
        int standardEpIdx = siteEpIdx + siteEpOffset;
        int fileEpIdx = standardEpIdx - fileEpOffset;
        return fileEpIdx;
    }
    
}