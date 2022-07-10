package com.lvt4j.spider4videostation.service;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.lvt4j.spider4videostation.Utils;

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
    private Drivers drivers;
    
    @Override
    public byte[] down(String url) throws Throwable {
        log.info("downloading static {}", url);
        String script = Tpl_Script.replace("@@OrigUrl@@", url);
        return drivers.staticOpen("about:blank", driver->{
            log.trace("send down xhr script");
            String b64 = (String) driver.executeScript(script);
            log.trace("downed b64 length : {}", b64.length());
            return Base64.getDecoder().decode(b64);
        });
    }
    
}