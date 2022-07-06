package com.lvt4j.spider4videostation;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.UriComponentsBuilder;

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

    public String publishPrefix;
    public String staticUrl;
    
    public String webDriverAddr;
    public boolean webDriverHeadless;
    public List<String> webDriverArgs;

    public List<String> staticWebDriverArgs;
    public long staticLoadTimeoutMillis;
    public String staticChromeDownloadFolder;
    
    public String javdbOrigin;
    public String javdbTouchUrl;
    public int javdbTimeoutMillis;
    
    public List<String> getWebDriverArgs() {
        List<String> webDriverArgs = new ArrayList<>(this.webDriverArgs);
        if(webDriverHeadless) webDriverArgs.add("--headless");
        return webDriverArgs;
    }
    public List<String> getStaticWebDriverArgs() {
        List<String> staticWebDriverArgs = new ArrayList<>(this.staticWebDriverArgs);
        if(webDriverHeadless) staticWebDriverArgs.add("--headless");
        return staticWebDriverArgs;
    }
    
    public String staticWrap(String url) {
        return UriComponentsBuilder.fromHttpUrl(staticUrl)
            .queryParam("url", url).toUriString();
    }
    
}