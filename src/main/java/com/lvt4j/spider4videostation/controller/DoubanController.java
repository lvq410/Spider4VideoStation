package com.lvt4j.spider4videostation.controller;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lvt4j.spider4videostation.service.DoubanService;
import com.lvt4j.spider4videostation.service.DoubanService.LoginState;

/**
 *
 * @author LV on 2022年7月7日
 */
@RestController("douban")
@RequestMapping("douban")
public class DoubanController {

    @Autowired
    private DoubanService service;
    
    @PostMapping("login")
    public LoginState login(HttpServletResponse response,
            @RequestParam String publishPrefix,
            @RequestParam(required=false) boolean waitSuccess) throws IOException {
        LoginState state = null;
        if(waitSuccess){
            state = service.checkLoginSuccess();
        }else{
            state = service.login(publishPrefix);
        }
        return state;
    }
    
    @GetMapping("cover")
    public void cover(HttpServletResponse response,
            @RequestParam String pre) throws IOException {
        byte[] bs = service.coverPre(pre);
        if(bs==null){
            response.setStatus(404);
            return;
        }
        
        response.setContentType(MediaType.IMAGE_JPEG_VALUE);
        response.setContentLength(bs.length);
        IOUtils.write(bs, response.getOutputStream());
    }
    
}