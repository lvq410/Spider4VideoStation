package com.lvt4j.spider4videostation.controller;

import static com.lvt4j.spider4videostation.Consts.PluginTestUseTitle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import com.lvt4j.spider4videostation.Plugin;
import com.lvt4j.spider4videostation.Utils;
import com.lvt4j.spider4videostation.pojo.Args;

import lombok.Cleanup;

/**
 *
 * @author LV on 2022年7月4日
 */
@Controller
@RequestMapping("plugin")
public class PluginController {

    private String tpl_loader;
    
    @PostConstruct
    public void init() throws Exception {
        tpl_loader = IOUtils.toString(PluginController.class.getResourceAsStream("/plugin/tpl_loader.sh"), Charset.defaultCharset());
    }
    
    @GetMapping
    public void plugin(HttpServletResponse response,
            @RequestParam String publishPrefix) throws Exception {
        @Cleanup ByteArrayOutputStream baos = new ByteArrayOutputStream();
        @Cleanup ZipOutputStream zioOut = new ZipOutputStream(baos);
        
        for(Plugin plugin : Plugin.values()){
            zioOut.putNextEntry(new ZipEntry(plugin.id+".zip"));
            IOUtils.write(singlePluginZip(publishPrefix, plugin), zioOut);
        }
        
        zioOut.close(); baos.close();
        
        byte[] data = baos.toByteArray();
        
        String fileName = "VideoStation插件[解压我得到插件].zip";
        fileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString());
        response.setContentLength(data.length);
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"; filename*=utf-8''" + fileName);
        
        IOUtils.write(data, response.getOutputStream());
    }
    
    byte[] singlePluginZip(String publishPrefix, Plugin plugin) throws IOException {
        @Cleanup ByteArrayOutputStream baos = new ByteArrayOutputStream();
        @Cleanup ZipOutputStream zioOut = new ZipOutputStream(baos);
        
        Info info = new Info();
        info.id = plugin.id;
        info.type = plugin.types;
        info.language = plugin.languages;
        for(String type : plugin.types){
            Args.Input input = new Args.Input();
            input.title = PluginTestUseTitle;
            info.test_example.put(type, input);
        }
        zioOut.putNextEntry(new ZipEntry(plugin.id+"/INFO"));
        IOUtils.write(Utils.ObjectMapper.writeValueAsBytes(info), zioOut);
        
        String searchUrl = UriComponentsBuilder.fromHttpUrl(publishPrefix)
            .path("search")
            .queryParam("pluginId", plugin.id)
            .queryParam("publishPrefix", publishPrefix)
            .toUriString();
        String loaderCnt = tpl_loader.replace("@@SearchUrl@@", searchUrl);
        zioOut.putNextEntry(new ZipEntry(plugin.id+"/loader.sh"));
        IOUtils.write(loaderCnt.getBytes(), zioOut);
        
        zioOut.close(); baos.close();
        
        return baos.toByteArray();
    }
    
    static class Info {
        public String id;
        public String entry_file = "loader.sh";
        public List<String> type;
        public List<String> language;
        public Map<String, Args.Input> test_example = new HashMap<>();
    }
    
}