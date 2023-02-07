package com.lvt4j.spider4videostation.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author LV on 2023年2月7日
 */
@RestController("prop")
@RequestMapping("prop")
public class PropController {

    @Autowired
    private Environment env;
    
    @Autowired
    private ContextRefresher contextRefresher;
    
    @PostMapping("gets")
    public Map<String, String> gets(
            @RequestBody List<String> keys) {
        return keys.stream().collect(Collectors.toMap(k->k, k->env.getProperty(k)));
    }
    
    @PostMapping("set")
    public void set(
            @RequestParam String key,
            @RequestParam String val) {
        System.setProperty(key, val);
        contextRefresher.refresh();
    }
    
}