package com.lvt4j.spider4videostation.service;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.lvt4j.spider4videostation.Config;
import com.lvt4j.spider4videostation.Utils;
import com.lvt4j.spider4videostation.service.Drivers.Type;

import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author LV on 2022年7月10日
 */
@Slf4j
@Service
@ConditionalOnProperty(name="staticMode",havingValue="xhrSync")
class StaticServiceByXhrSync implements StaticService {

    private static final String Tpl_Script = Utils.res("/script/tpl_down_xhr.js");
    
    @Autowired
    private Config config;
    
    @Autowired
    private Drivers drivers;
    
    @Override
    public byte[] down(String url, String service) throws Throwable {
        log.info("downloading static {}", url);
        String script = Tpl_Script.replace("@@OrigUrl@@", url);
        String touchUrl = null;
        switch (service){
        case DoubanService.Name:
            touchUrl = config.getDoubanTouchUrl();
            break;
        case JavdbService.Name:
            touchUrl = config.getJavdbTouchUrl();
            break;
        default:
            touchUrl = "about:blank";
            break;
        }
        return drivers.driver(service, Type.Staticer).open(touchUrl, driver->{
            log.trace("send down xhr script");
            String b64 = (String) driver.executeScript(script);
            log.trace("downed b64 length : {}", b64.length());
            return Base64.getDecoder().decode(b64);
        });
    }
    
}