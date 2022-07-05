package com.lvt4j.spider4videostation;

import java.io.File;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.io.IOUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 *
 * @author LV on 2022年7月5日
 */
//@Configuration
@RestController("hls")
@RequestMapping("hls")
public class HLSTest implements WebMvcConfigurer {

    @RequestMapping("key")
    public void key(
            HttpServletRequest request,
            HttpServletResponse response) throws Throwable {
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        String key = "76a6c65c5ea762046bd749a2e632ccbb";
        byte[] a= DatatypeConverter.parseHexBinary(key);
        response.setContentLength(a.length);
        IOUtils.write(a, response.getOutputStream());
    }
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        
    }
    
}
