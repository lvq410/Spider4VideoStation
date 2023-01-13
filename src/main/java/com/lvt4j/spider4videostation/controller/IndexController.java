package com.lvt4j.spider4videostation.controller;

import static com.lvt4j.spider4videostation.Utils.ObjectMapper;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lvt4j.spider4videostation.Plugin;
import com.lvt4j.spider4videostation.service.FileCacher;

/**
 *
 * @author LV on 2022年7月7日
 */
@RestController
@RequestMapping
public class IndexController {

    @Autowired
    private FileCacher fileCacher;
    
    @RequestMapping(produces=MediaType.TEXT_HTML_VALUE)
    public String index() throws Exception {
        String indexHtml = IOUtils.toString(IndexController.class.getResourceAsStream("/index.html"), Charset.defaultCharset());
        indexHtml = indexHtml.replace("@@Plugins@@", ObjectMapper.writeValueAsString(pluginInfos()));
        return indexHtml;
    }
    
    public Map<String, Object> pluginInfos() {
        Map<String, Object> infos = new LinkedHashMap<>();
        
        for(Plugin plugin : Plugin.values()){
            Map<String, Object> info = new HashMap<>();
            info.put("types", plugin.types);
            info.put("languages", plugin.languages);
            infos.put(plugin.id, info);
        }
        
        return infos;
    }
    
    @RequestMapping("cleanCache")
    public long cleanCache() throws Exception {
        return fileCacher.clean();
    }
    
}