package com.lvt4j.spider4videostation.controller;

import java.nio.charset.Charset;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        String pluginOptions = Stream.of(Plugin.values()).map(p->"<option>"+p.id+"</option>").collect(Collectors.joining(" "));
        indexHtml = indexHtml.replace("@@PluginIds@@", pluginOptions);
        return indexHtml;
    }
    
    @RequestMapping("cleanCache")
    public long cleanCache() throws Exception {
        return fileCacher.clean();
    }
    
}