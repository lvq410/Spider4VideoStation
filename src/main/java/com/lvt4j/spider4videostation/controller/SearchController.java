package com.lvt4j.spider4videostation.controller;

import static com.lvt4j.spider4videostation.Consts.PluginTestUseTitle;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lvt4j.spider4videostation.PluginType;
import com.lvt4j.spider4videostation.Utils;
import com.lvt4j.spider4videostation.pojo.Args;
import com.lvt4j.spider4videostation.pojo.Movie;
import com.lvt4j.spider4videostation.pojo.Rst;
import com.lvt4j.spider4videostation.pojo.TvShow;
import com.lvt4j.spider4videostation.pojo.TvShowEpisode;
import com.lvt4j.spider4videostation.service.SpiderService;

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
    public Rst search(
            @RequestParam String publishPrefix,
            @RequestParam("pluginId") String pluginId,
            @RequestParam("pluginType") String pluginTypeStr,
            @RequestBody String body) throws Throwable {
        log.info("search {}", body);
        
        Rst rst = new Rst();
        
        PluginType pluginType = PluginType.find(pluginTypeStr);
        if(pluginType==null) {
            log.error("unknown pluginType {}", pluginType);
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
            switch(args.type){
            case "movie":
                rst.result.add(Movie.testUse(pluginId));
                break;
            case "tvshow":
                rst.result.add(TvShow.testUse(pluginId));
                break;
            case "tvshow_episode":
                TvShowEpisode episode = TvShowEpisode.testUse(pluginId);
                if(args.input.season!=null) episode.season = args.input.season;
                if(args.input.episode!=null) episode.episode = args.input.episode;
                episode.extra().tvshow = TvShow.testUse(pluginId);
                rst.result.add(episode);
                break;
            default:
                break;
            }
            
            return rst;
        }
        
        services.stream().filter(s->s.support(pluginType, args)).parallel()
            .forEach(s->s.search(pluginId, publishPrefix, pluginType, args, rst));
        
        rst.success = !rst.result.isEmpty();
        
        log.info("end {}", Utils.ObjectMapper.writeValueAsString(rst));
        return rst;
    }
}
