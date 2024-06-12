package com.lvt4j.spider4videostation.controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;
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

import lombok.extern.slf4j.Slf4j;

/**
 * 没有较强防抓包的静态资源网站的资源下载
 * @author LV on 2022年7月2日
 */
@Slf4j
@RestController("static")
@RequestMapping("static")
public class StaticController {

    private final int MaxImgSize = 4 * 1024 * 1024;
    
    @Autowired
    private FileCacher cacher;
    
    @Autowired
    private StaticService service;
    
    @GetMapping
    public void proxy(HttpServletResponse response,
            @RequestParam String url,
            @RequestParam("service") String serviceName,
            @RequestParam(value="mediaType",required=false) String mediaTypeStr) throws Throwable {
        byte[] bs = cacher.load(url);
        if(bs==null) {
            bs = service.down(url, serviceName);
            if(bs==null){
                response.setStatus(404);
                return;
            }
            cacher.save(url, bs);
        }
        
        if(bs.length>=MaxImgSize) bs = tryShrink(bs, url);
        
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
    
    private byte[] tryShrink(byte[] data, String url) {
        while(data.length>=MaxImgSize){
            try{
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
                double rate = Math.sqrt((double)MaxImgSize / data.length);
                
                int w = (int)(image.getWidth() * rate);
                int h = (int)(image.getHeight() * rate);
                BufferedImage dest = new BufferedImage(w, h, 1);
                dest.getGraphics().drawImage(image, 0, 0, w, h, null);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(dest, "jpg", baos);
                data = baos.toByteArray();
            }catch(Exception e){
                log.warn("shrink img fail : {}", url, e);
                return data;
            }
        }
        return data;
    }
    
    public String jpgWrap(String publishPrefix, String url, String service) {
        return staticWrap(publishPrefix, url, service, MediaType.IMAGE_JPEG_VALUE);
    }
    private String staticWrap(String publishPrefix, String url, String service, String mediaType) {
        return UriComponentsBuilder.fromHttpUrl(publishPrefix)
                .path("static")
                .queryParam("url", url)
                .queryParam("service", service)
                .queryParam("mediaType", mediaType)
                .toUriString();
    }
    
}