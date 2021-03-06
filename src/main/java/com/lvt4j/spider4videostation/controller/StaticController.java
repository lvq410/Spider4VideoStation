package com.lvt4j.spider4videostation.controller;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.lvt4j.spider4videostation.service.FileCacher;
import com.lvt4j.spider4videostation.service.StaticService;

/**
 * 没有较强防抓包的静态资源网站的资源下载
 * @author LV on 2022年7月2日
 */
@RestController("static")
@RequestMapping("static")
public class StaticController {

    @Autowired
    private FileCacher cacher;
    
    @Autowired
    private StaticService service;
    
    @GetMapping
    public void proxy(HttpServletResponse response,
            @RequestParam String url,
            @RequestParam(value="mediaType",required=false) String mediaTypeStr) throws Throwable {
        byte[] bs = cacher.load(url);
        if(bs==null) {
            bs = service.down(url);
            if(bs==null){
                response.setStatus(404);
                return;
            }
            cacher.save(url, bs);
        }
        
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