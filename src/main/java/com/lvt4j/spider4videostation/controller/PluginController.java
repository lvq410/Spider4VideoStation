package com.lvt4j.spider4videostation.controller;

import static com.lvt4j.spider4videostation.Consts.PluginTestUseTitle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import com.lvt4j.spider4videostation.PluginType;
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

    private static final String Tpl_Loader = Utils.res("/plugin/tpl_loader.sh");
    
    @GetMapping
    public void plugin(HttpServletResponse response,
            @RequestParam String publishPrefix) throws Exception {
        @Cleanup ByteArrayOutputStream baos = new ByteArrayOutputStream();
        @Cleanup ZipOutputStream zioOut = new ZipOutputStream(baos);
        
        String host = new URL(publishPrefix).getHost();
        
        for(PluginType pluginType : PluginType.values()){
            String id = pluginType.name+"("+host+")";
            zioOut.putNextEntry(new ZipEntry(id+".zip"));
            IOUtils.write(singlePluginZip(id, publishPrefix, pluginType), zioOut);
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
    
    byte[] singlePluginZip(String id, String publishPrefix, PluginType pluginType) throws IOException {
        @Cleanup ByteArrayOutputStream baos = new ByteArrayOutputStream();
        @Cleanup ZipOutputStream zioOut = new ZipOutputStream(baos);
        
        Info info = new Info();
        info.id = id;
        info.type = pluginType.infoTypes;
        info.language = pluginType.languages;
        for(String type : pluginType.infoTypes){
            Args.Input input = new Args.Input();
            input.title = PluginTestUseTitle;
            switch(type){
            case "tvshow":
                info.test_example.put(type, input);
                
                Args.Input episodeInput = SerializationUtils.clone(input);
                episodeInput.season = 1; episodeInput.episode = 1;
                info.test_example.put("tvshow_episode", episodeInput);
                break;
            default:
                info.test_example.put(type, input);
                break;
            }
        }
        zioOut.putNextEntry(new ZipEntry(id+"/INFO"));
        IOUtils.write(Utils.ObjectMapper.writeValueAsBytes(info), zioOut);
        
        String searchUrl = UriComponentsBuilder.fromHttpUrl(publishPrefix)
            .path("search")
            .queryParam("pluginId", id)
            .queryParam("pluginType", pluginType.name)
            .queryParam("publishPrefix", publishPrefix)
            .toUriString();
        String loaderCnt = Tpl_Loader.replace("@@SearchUrl@@", searchUrl);
        zioOut.putNextEntry(new ZipEntry(id+"/loader.sh"));
        IOUtils.write(loaderCnt.getBytes(), zioOut);
        
        zioOut.close(); baos.close();
        
        return baos.toByteArray();
    }
    
    static class Info {
        public String id;
        public String entry_file = "loader.sh";
        public List<String> type;
        public List<String> language;
        public Map<String, Args.Input> test_example = new LinkedHashMap<>();
    }
    
}