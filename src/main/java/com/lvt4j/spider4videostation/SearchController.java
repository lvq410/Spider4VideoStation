package com.lvt4j.spider4videostation;

import static com.lvt4j.spider4videostation.Consts.PluginTestUseTitle;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author LV on 2022年7月4日
 */
@Slf4j
@RestController("search")
@RequestMapping("search")
public class SearchController {

    @Autowired
    private List<SpiderService> services;
    
    @PostMapping
    public Rst search(@RequestParam String pluginId,
            @RequestBody String body) throws Throwable {
        log.info("search {}", body);
        
        Rst rst = new Rst();
        
        Plugin plugin = Plugin.find(pluginId);
        if(plugin==null) {
            log.error("unknown pluginId {}", pluginId);
            return rst;
        }
        
        Args args;
        try{
            args = Args.parse(body);
        }catch(Exception e){
            log.error("error search args {}", body, e);
            return rst;
        }
        
        if(PluginTestUseTitle.equals(args.input.title)){
            rst.success = true;
            rst.result.add(Movie.testUse(pluginId));
            return rst;
        }
        
        services.stream().filter(s->s.support(plugin, args)).parallel()
            .forEach(s->s.search(plugin, args, rst));
        
        rst.success = !rst.result.isEmpty();
        
        log.info("end {}", Utils.ObjectMapper.writeValueAsString(rst));
        return rst;
    }
}
