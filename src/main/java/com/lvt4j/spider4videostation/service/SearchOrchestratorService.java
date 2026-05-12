package com.lvt4j.spider4videostation.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.lvt4j.spider4videostation.Consts;
import com.lvt4j.spider4videostation.TargetSite;
import com.lvt4j.spider4videostation.pojo.Args;
import com.lvt4j.spider4videostation.pojo.Movie;
import com.lvt4j.spider4videostation.pojo.Rst;
import com.lvt4j.spider4videostation.pojo.TvShow;
import com.lvt4j.spider4videostation.pojo.TvShowEpisode;

import lombok.extern.slf4j.Slf4j;

/**
 * 搜索编排服务 —— 从原 SearchController 提取
 *
 * @author LV on 2022年7月4日
 */
@Slf4j
@Service
public class SearchOrchestratorService {

    @Autowired
    private List<SpiderService> services;

    public Rst search(TargetSite targetSite, Args args) {
        log.info("search {}", args);

        Rst rst = new Rst();

        if (args == null || targetSite == null) return rst;

        if (Consts.PluginTestUseTitle.equals(args.input.title)) {
            rst.success = true;
            String id = targetSite.name;
            switch (args.type) {
            case "movie":
                rst.result.add(Movie.testUse(id));
                break;
            case "tvshow":
                rst.result.add(TvShow.testUse(id));
                break;
            case "tvshow_episode":
                TvShowEpisode episode = TvShowEpisode.testUse(id);
                if (args.input.season != null) episode.season = args.input.season;
                if (args.input.episode != null) episode.episode = args.input.episode;
                episode.extra().tvshow = TvShow.testUse(id);
                rst.result.add(episode);
                break;
            }
            return rst;
        }

        services.stream().filter(s -> s.support(targetSite, args)).parallel()
            .forEach(s -> s.search(targetSite, args, rst));

        rst.success = !rst.result.isEmpty();

        log.info("end {}", rst);
        return rst;
    }
}