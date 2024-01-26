package com.lvt4j.spider4videostation.service;

import static com.lvt4j.spider4videostation.Consts.PathMatcher;
import static com.lvt4j.spider4videostation.Utils.isUrl;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.lvt4j.spider4videostation.Config;
import com.lvt4j.spider4videostation.Consts;
import com.lvt4j.spider4videostation.PluginType;
import com.lvt4j.spider4videostation.Utils;
import com.lvt4j.spider4videostation.controller.DoubanController;
import com.lvt4j.spider4videostation.controller.StaticController;
import com.lvt4j.spider4videostation.pojo.Args;
import com.lvt4j.spider4videostation.pojo.Movie;
import com.lvt4j.spider4videostation.pojo.Rst;
import com.lvt4j.spider4videostation.pojo.TvShow;
import com.lvt4j.spider4videostation.pojo.TvShowEpisode;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author LV on 2022年7月6日
 */
@Slf4j
@Service
public class DoubanService implements SpiderService {

    private static final String SubjectPattern = "/subject/*/";
    private static final String EpisodePattern = "/subject/{subjectId}/episode/{epIdx}/";
    
    @Autowired
    private Config config;
    
    @Autowired
    private FileCacher cacher;
    @Autowired
    private Drivers drivers;
    @Autowired
    private StaticService staticService;
    
    @Autowired@Lazy
    private StaticController staticController;
    @Autowired@Lazy
    private DoubanController doubanController;
    
    @Override
    public boolean support(PluginType plugin, Args args) {
        if(PluginType.Douban!=plugin) return false;
        if(!plugin.searchTypes.contains(args.type)) return false;
        if(!plugin.languages.contains(args.lang)) return false;
        return true;
    }

    @Override
    public synchronized void search(String pluginId, String publishPrefix, PluginType pluginType, Args args, Rst rst) {
        switch(args.type){
        case "movie":
            movie_search(pluginId, publishPrefix, args, rst);
            break;
        case "tvshow":
            tvshow_search(pluginId, publishPrefix, args, rst);
            break;
        case "tvshow_episode":
            tvshow_episode_search(pluginId, publishPrefix, args, rst);
            break;
        default: break;
        }
    }
    
    @SneakyThrows
    private void movie_search(String pluginId, String publishPrefix, Args args, Rst rst) {
        args.limit = Math.min(args.limit, config.getDoubanMaxLimit());
        
        if(isUrl(args.input.title)){
            if(isSubjectUrl(args.input.title)){
                String detailUrl = args.input.title;
                Movie movie = null;
                try{
                    movie = movie_loadItem(pluginId, publishPrefix, detailUrl);
                }catch(Exception e){
                    log.error("error load detail {}", detailUrl, e);
                    return;
                }
                if(movie==null) return;
                
                rst.result.add(movie);
                return;
            }
            return;
        }
        
        String searchUrl = UriComponentsBuilder.fromHttpUrl(config.getDoubanSearchMovieUrl())
            .queryParam("search_text", args.input.title).toUriString();
        log.info("open search {}", searchUrl);
        String searchCnt;
        try{
            searchCnt = loadPage(searchUrl);
            if(log.isTraceEnabled()) log.trace("search rst {}", searchCnt);
        }catch(Exception e){
            log.error("error on search {}", searchUrl, e);
            return;
        }
        
        Document doc = Jsoup.parse(searchCnt);
        doc.setBaseUri(searchUrl);
        
        int rstNum = 0;
        
        Elements items = doc.select("div.item-root");
        for(Element item : items){
            Element detailA = item.selectFirst("a");
            if(detailA==null) continue;
            String detailUrl = detailA.absUrl("href");
            if(StringUtils.isBlank(detailUrl)) continue;
            if(!detailUrl.startsWith(config.getDoubanMovieItemPattern())) continue;
            Element detailDiv = item.selectFirst("div.detail");
            if(detailDiv==null) continue;
            
            //提取标题
            String title = null;
            Element titleDiv = detailDiv.selectFirst("div.title");
            if(titleDiv!=null) {
                title = StringUtils.strip(titleDiv.text().trim(), "　");
                if(title.indexOf('(')>0){
                    title = title.substring(0, title.indexOf('(')).trim();
                }
            }
            
            //提取竖版图片备用
            String coverUrl = null;
            Element coverImg = detailA.selectFirst("img");
            if(coverImg!=null) {
                coverUrl = coverImg.absUrl("src");
                if(StringUtils.isNotBlank(coverUrl)){
                    coverUrl = staticController.jpgWrap(publishPrefix, coverUrl);
                }
            }
            
            Movie movie = null;
            try{
                movie = movie_loadItem(pluginId, publishPrefix, detailUrl);
            }catch(Exception e){
                log.error("error load detail {}", detailUrl, e);
                continue;
            }
            
            if(movie==null){ //未从详情页提取出信息
                if(StringUtils.isNotBlank(title) && StringUtils.isNotBlank(coverUrl)){ //但列表上有点数据
                    //用列表上的数据
                    movie = new Movie(pluginId);
                    if(StringUtils.isNotBlank(title)) movie.title = title;
                    if(StringUtils.isNotBlank(coverUrl)) {
                        movie.extra().poster.add(0, coverUrl);
                        movie.extra().backdrop.add(0, coverUrl);
                    }
                }else{
                    continue;
                }
            }
            
            rst.result.add(movie);
            if(args.reachLimit(++rstNum)) break;
        }
    }
    private Movie movie_loadItem(String pluginId, String publishPrefix, String detailUrl) {
        Movie movie = new Movie(pluginId);
        
        log.info("load detail {}", detailUrl);
        String detailCnt = loadPage(detailUrl);
        if(log.isTraceEnabled()) log.trace("load detail cnt {}", detailCnt);
        Document detailHtml = Jsoup.parse(detailCnt);
        
        Element contentDiv = detailHtml.selectFirst("#content");
        if(contentDiv==null) return null;
        
        Element titleSpan = contentDiv.selectFirst("h1 span");
        if(titleSpan!=null){
            movie.title = StringUtils.strip(titleSpan.text().trim(), "　");
        }
        
        Element mainpicDiv = contentDiv.selectFirst("#mainpic");
        if(mainpicDiv!=null){
            Element mainpicImg = mainpicDiv.selectFirst("img");
            if(mainpicImg!=null){
                String posterUrl = mainpicImg.absUrl("src");
                if(StringUtils.isNotBlank(posterUrl)){
                    posterUrl = staticController.jpgWrap(publishPrefix, posterUrl);
                    movie.extra().poster.add(posterUrl);
                }
            }
            Element mainpicA = mainpicDiv.selectFirst("a");
            if(mainpicA!=null){
                String mainpicUrl = mainpicA.absUrl("href");
                if(StringUtils.isNotBlank(mainpicUrl)){
                    loadBackdrops(mainpicUrl, movie.extra().backdrop, publishPrefix);
                }
            }
        }
        
        Elements pls = contentDiv.select("#info span.pl");
        for(Element pl : pls){
            String name = pl.text().trim();
            if(StringUtils.isBlank(name)) continue;
            
            switch(name){
            case "导演":
                Elements directorAs = pl.nextElementSibling().select("a");
                for(Element directorA : directorAs){
                    String director = directorA.text().trim();
                    if(StringUtils.isBlank(director)) continue;
                    movie.director.add(director);
                }
                break;
            case "编剧":
                Elements writerAs = pl.nextElementSibling().select("a");
                for(Element writerA : writerAs){
                    String writer = writerA.text().trim();
                    if(StringUtils.isBlank(writer)) continue;
                    movie.writer.add(writer);
                }
                break;
            case "主演":
                Elements actorAs = pl.nextElementSibling().select("a[rel='v:starring']");
                for(Element actorA : actorAs){
                    String actor = actorA.text().trim();
                    if(StringUtils.isBlank(actor)) continue;
                    movie.actor.add(actor);
                }
                break;
            case "类型:":
                Elements genreSpans = contentDiv.select("#info span[property='v:genre']");
                for(Element genreSpan : genreSpans){
                    String genre = genreSpan.text().trim();
                    if(StringUtils.isBlank(genre)) continue;
                    movie.genre.add(genre);
                }
                break;
            case "上映日期:":
            case "首播:":
                Element initialReleaseDateSpan = contentDiv.selectFirst("#info span[property='v:initialReleaseDate']");
                if(initialReleaseDateSpan!=null){
                    String initialReleaseDate = initialReleaseDateSpan.text().trim();
                    if(StringUtils.isNotBlank(initialReleaseDate)){
                        if(initialReleaseDate.indexOf('(')>0) initialReleaseDate = initialReleaseDate.substring(0, initialReleaseDate.indexOf('('));
                        movie.original_available = initialReleaseDate;
                    }
                }
                break;
            default: break;
            }
        }
        Element ratingStrong = contentDiv.selectFirst("strong[property='v:average']");
        if(ratingStrong!=null){
            String rating = ratingStrong.text().trim();
            if(NumberUtils.isParsable(rating)){
                BigDecimal rate = new BigDecimal(rating)
                    .setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
                movie.extra().rating(rate);
            }
        }
        
        Element summarySpan = contentDiv.selectFirst("span[property='v:summary']");
        if(summarySpan!=null){
            movie.summary = summarySpan.text().trim();
        }
        
        return movie;
    }
    
    @SneakyThrows
    private void tvshow_search(String pluginId, String publishPrefix, Args args, Rst rst) {
        args.limit = Math.min(args.limit, config.getDoubanMaxLimit());
        
        if(isUrl(args.input.title)){
            if(isSubjectUrl(args.input.title)){
                String detailUrl = args.input.title;
                TvShow tvShow = null;
                try{
                    tvShow = tvshow_loadItem(pluginId, publishPrefix, detailUrl);
                }catch(Exception e){
                    log.error("error load detail {}", detailUrl, e);
                    return;
                }
                if(tvShow==null) return;
                
                tvShow.detailModeChange(detailUrl);
                rst.result.add(tvShow);
                return;
            }
            return;
        }
        
        String searchUrl = UriComponentsBuilder.fromHttpUrl(config.getDoubanSearchMovieUrl())
            .queryParam("search_text", args.input.title).toUriString();
        log.info("open search {}", searchUrl);
        String searchCnt;
        try{
            searchCnt = loadPage(searchUrl);
            if(log.isTraceEnabled()) log.trace("search rst {}", searchCnt);
        }catch(Exception e){
            log.error("error on search {}", searchUrl, e);
            return;
        }
        
        Document doc = Jsoup.parse(searchCnt);
        doc.setBaseUri(searchUrl);
        
        int rstNum = 0;
        
        Elements items = doc.select("div.item-root");
        for(Element item : items){
            Element detailA = item.selectFirst("a");
            if(detailA==null) continue;
            String detailUrl = detailA.absUrl("href");
            if(StringUtils.isBlank(detailUrl)) continue;
            if(!detailUrl.startsWith(config.getDoubanMovieItemPattern())) continue;
            Element detailDiv = item.selectFirst("div.detail");
            if(detailDiv==null) continue;
            
            //提取标题
            String title = null;
            Element titleDiv = detailDiv.selectFirst("div.title");
            if(titleDiv!=null) {
                title = StringUtils.strip(titleDiv.text().trim(), "　");
                if(title.indexOf('(')>0){
                    title = title.substring(0, title.indexOf('(')).trim();
                }
            }
            
            //提取竖版图片备用
            String coverUrl = null;
            Element coverImg = detailA.selectFirst("img");
            if(coverImg!=null) {
                coverUrl = coverImg.absUrl("src");
                if(StringUtils.isNotBlank(coverUrl)){
                    coverUrl = staticController.jpgWrap(publishPrefix, coverUrl);
                }
            }
            
            TvShow tvShow = null;
            try{
                tvShow = tvshow_loadItem(pluginId, publishPrefix, detailUrl);
            }catch(Exception e){
                log.error("error load detail {}", detailUrl, e);
                continue;
            }
            
            if(tvShow==null){ //未从详情页提取出信息
                if(StringUtils.isNotBlank(title) && StringUtils.isNotBlank(coverUrl)){ //但列表上有点数据
                    //用列表上的数据
                    tvShow = new TvShow(pluginId);
                    if(StringUtils.isNotBlank(title)) tvShow.title = title;
                    if(StringUtils.isNotBlank(coverUrl)) {
                        tvShow.extra().poster.add(0, coverUrl);
                        tvShow.extra().backdrop.add(0, coverUrl);
                    }
                }else{
                    continue;
                }
            }
            
            rst.result.add(tvShow);
            if(args.reachLimit(++rstNum)) break;
        }
    }
    private TvShow tvshow_loadItem(String pluginId, String publishPrefix, String detailUrl) {
        TvShow tvShow = new TvShow(pluginId);
        
        log.info("load detail {}", detailUrl);
        String detailCnt = loadPage(detailUrl);
        if(log.isTraceEnabled()) log.trace("load detail cnt {}", detailCnt);
        Document detailHtml = Jsoup.parse(detailCnt);
        
        Element contentDiv = detailHtml.selectFirst("#content");
        if(contentDiv==null) return null;
        
        Element titleSpan = contentDiv.selectFirst("h1 span");
        if(titleSpan!=null){
            tvShow.title = StringUtils.strip(titleSpan.text().trim(), "　");
        }
        
        Element mainpicDiv = contentDiv.selectFirst("#mainpic");
        if(mainpicDiv!=null){
            Element mainpicImg = mainpicDiv.selectFirst("img");
            if(mainpicImg!=null){
                String posterUrl = mainpicImg.absUrl("src");
                if(StringUtils.isNotBlank(posterUrl)){
                    posterUrl = staticController.jpgWrap(publishPrefix, posterUrl);
                    tvShow.extra().poster.add(posterUrl);
                }
            }
            Element mainpicA = mainpicDiv.selectFirst("a");
            if(mainpicA!=null){
                String mainpicUrl = mainpicA.absUrl("href");
                if(StringUtils.isNotBlank(mainpicUrl)){
                    loadBackdrops(mainpicUrl, tvShow.extra().backdrop, publishPrefix);
                }
            }
        }
        
        Elements pls = contentDiv.select("#info span.pl");
        for(Element pl : pls){
            String name = pl.text().trim();
            if(StringUtils.isBlank(name)) continue;
            
            switch(name){
            case "上映日期:":
            case "首播:":
                Element initialReleaseDateSpan = contentDiv.selectFirst("#info span[property='v:initialReleaseDate']");
                if(initialReleaseDateSpan!=null){
                    String initialReleaseDate = initialReleaseDateSpan.text().trim();
                    if(StringUtils.isNotBlank(initialReleaseDate)){
                        if(initialReleaseDate.indexOf('(')>0) initialReleaseDate = initialReleaseDate.substring(0, initialReleaseDate.indexOf('('));
                        tvShow.original_available = initialReleaseDate;
                    }
                }
                break;
            default: break;
            }
        }
        if(StringUtils.isNotBlank(config.getOriginalAvailable())) tvShow.original_available = config.getOriginalAvailable();
        
        Element summarySpan = contentDiv.selectFirst("span[property='v:summary']");
        if(summarySpan!=null){
            tvShow.summary = summarySpan.text().trim();
        }
        
        return tvShow;
    }
    
    @SneakyThrows
    private void tvshow_episode_search(String pluginId, String publishPrefix, Args args, Rst rst) {
        args.limit = Math.min(args.limit, config.getDoubanMaxLimit());
        
        if(isUrl(args.input.title)){
            if(isSubjectUrl(args.input.title)){
                String detailUrl = args.input.title;
                List<TvShowEpisode> episodes = null;
                try{
                    episodes = tvshow_episode_loadItem(pluginId, publishPrefix, detailUrl, args.input.season, args.input.episode, null);
                    episodes.forEach(ep->ep.detailModeChange(detailUrl));
                }catch(Exception e){
                    log.error("error load detail {}", detailUrl, e);
                    return;
                }
                rst.result.addAll(episodes);
                return;
            }
            if(isEpisodeUrl(args.input.title)){
                String episodeUrl = args.input.title;
                Map<String, String> vars = PathMatcher.extractUriTemplateVariables(EpisodePattern, new URL(episodeUrl).getPath());
                String subjectId = vars.get("subjectId");
                String detailUrl = UriComponentsBuilder.fromHttpUrl(args.input.title).replacePath("/subject/"+subjectId+"/").toUriString();
                List<TvShowEpisode> episodes = null;
                try{
                    episodes = tvshow_episode_loadItem(pluginId, publishPrefix, detailUrl, args.input.season, args.input.episode, episodeUrl);
                }catch(Exception e){
                    log.error("error load detail {}", detailUrl, e);
                    return;
                }
                rst.result.addAll(episodes);
                return;
            }
            return;
        }
        
        String searchUrl = UriComponentsBuilder.fromHttpUrl(config.getDoubanSearchMovieUrl())
            .queryParam("search_text", args.input.title).toUriString();
        log.info("open search {}", searchUrl);
        String searchCnt;
        try{
            searchCnt = loadPage(searchUrl);
            if(log.isTraceEnabled()) log.trace("search rst {}", searchCnt);
        }catch(Exception e){
            log.error("error on search {}", searchUrl, e);
            return;
        }
        
        Document doc = Jsoup.parse(searchCnt);
        doc.setBaseUri(searchUrl);
        
        int rstNum = 0;
        
        Elements items = doc.select("div.item-root");
        for(Element item : items){
            Element detailA = item.selectFirst("a");
            if(detailA==null) continue;
            String detailUrl = detailA.absUrl("href");
            if(StringUtils.isBlank(detailUrl)) continue;
            if(!detailUrl.startsWith(config.getDoubanMovieItemPattern())) continue;
            Element detailDiv = item.selectFirst("div.detail");
            if(detailDiv==null) continue;
            
            //提取标题
            String title = null;
            Element titleDiv = detailDiv.selectFirst("div.title");
            if(titleDiv!=null) {
                title = StringUtils.strip(titleDiv.text().trim(), "　");
                if(title.indexOf('(')>0){
                    title = title.substring(0, title.indexOf('(')).trim();
                }
            }
            
            //提取竖版图片备用
            String coverUrl = null;
            Element coverImg = detailA.selectFirst("img");
            if(coverImg!=null) {
                coverUrl = coverImg.absUrl("src");
                if(StringUtils.isNotBlank(coverUrl)){
                    coverUrl = staticController.jpgWrap(publishPrefix, coverUrl);
                }
            }
            
            List<TvShowEpisode> episodes = null;
            try{
                episodes = tvshow_episode_loadItem(pluginId, publishPrefix, detailUrl, args.input.season, args.input.episode, null);
            }catch(Exception e){
                log.error("error load detail {}", detailUrl, e);
                continue;
            }
            
            rst.result.addAll(episodes);
            if(args.reachLimit(++rstNum)) break;
        }
    }
    private List<TvShowEpisode> tvshow_episode_loadItem(String pluginId, String publishPrefix, String detailUrl, Integer season, Integer epIdx, String episodeUrl) {
        List<TvShowEpisode> episodes = new LinkedList<>();
        TvShowEpisode base = new TvShowEpisode(pluginId);
        if(season!=null) base.season = season;
        TvShow tvShow = new TvShow(pluginId);
        
        log.info("load detail {}", detailUrl);
        String detailCnt = loadPage(detailUrl);
        if(log.isTraceEnabled()) log.trace("load detail cnt {}", detailCnt);
        Document detailHtml = Jsoup.parse(detailCnt); detailHtml.setBaseUri(detailUrl);
        
        Element contentDiv = detailHtml.selectFirst("#content");
        if(contentDiv==null) return episodes;
        
        Element titleSpan = contentDiv.selectFirst("h1 span");
        if(titleSpan!=null){
            tvShow.title = base.title = StringUtils.strip(titleSpan.text().trim(), "　");
        }
        
        Element mainpicDiv = contentDiv.selectFirst("#mainpic");
        if(mainpicDiv!=null){
            Element mainpicImg = mainpicDiv.selectFirst("img");
            if(mainpicImg!=null){
                String posterUrl = mainpicImg.absUrl("src");
                if(StringUtils.isNotBlank(posterUrl)){
                    posterUrl = staticController.jpgWrap(publishPrefix, posterUrl);
                    tvShow.extra().poster.add(posterUrl);
                }
            }
            Element mainpicA = mainpicDiv.selectFirst("a");
            if(mainpicA!=null){
                String mainpicUrl = mainpicA.absUrl("href");
                if(StringUtils.isNotBlank(mainpicUrl)){
                    loadBackdrops(mainpicUrl, tvShow.extra().backdrop, publishPrefix);
                }
            }
        }
        
        Elements pls = contentDiv.select("#info span.pl");
        for(Element pl : pls){
            String name = pl.text().trim();
            if(StringUtils.isBlank(name)) continue;
            
            switch(name){
            case "导演":
                Elements directorAs = pl.nextElementSibling().select("a");
                for(Element directorA : directorAs){
                    String director = directorA.text().trim();
                    if(StringUtils.isBlank(director)) continue;
                    base.director.add(director);
                }
                break;
            case "编剧":
                Elements writerAs = pl.nextElementSibling().select("a");
                for(Element writerA : writerAs){
                    String writer = writerA.text().trim();
                    if(StringUtils.isBlank(writer)) continue;
                    base.writer.add(writer);
                }
                break;
            case "主演":
                Elements actorAs = pl.nextElementSibling().select("a[rel='v:starring']");
                for(Element actorA : actorAs){
                    String actor = actorA.text().trim();
                    if(StringUtils.isBlank(actor)) continue;
                    base.actor.add(actor);
                }
                break;
            case "类型:":
                Elements genreSpans = contentDiv.select("#info span[property='v:genre']");
                for(Element genreSpan : genreSpans){
                    String genre = genreSpan.text().trim();
                    if(StringUtils.isBlank(genre)) continue;
                    base.genre.add(genre);
                }
                break;
            case "首播:":
            case "上映日期:":
                Element initialReleaseDateSpan = contentDiv.selectFirst("#info span[property='v:initialReleaseDate']");
                if(initialReleaseDateSpan!=null){
                    String initialReleaseDate = initialReleaseDateSpan.text().trim();
                    if(StringUtils.isNotBlank(initialReleaseDate)){
                        if(initialReleaseDate.indexOf('(')>0) initialReleaseDate = initialReleaseDate.substring(0, initialReleaseDate.indexOf('('));
                        tvShow.original_available = base.original_available = initialReleaseDate;
                    }
                }
                break;
            case "季数:":
                Node seasonNode = pl.nextSibling();
                if(seasonNode!=null){
                    String seasonStr = seasonNode.toString().trim();
                    if(NumberUtils.isDigits(seasonStr)){
                        base.season = Integer.valueOf(seasonStr);
                    }
                }
                break;
            default: break;
            }
        }
        if(StringUtils.isNotBlank(config.getOriginalAvailable())) tvShow.original_available = config.getOriginalAvailable();
        
        Element ratingStrong = contentDiv.selectFirst("strong[property='v:average']");
        if(ratingStrong!=null){
            String rating = ratingStrong.text().trim();
            if(NumberUtils.isParsable(rating)){
                BigDecimal rate = new BigDecimal(rating)
                    .setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
                base.extra().rating(rate);
            }
        }
        
        Element summarySpan = contentDiv.selectFirst("span[property='v:summary']");
        if(summarySpan!=null){
            tvShow.summary = summarySpan.text().trim();
        }
        
        base.extra().tvshow = tvShow;
        
        
        Integer siteEpIdx = config.fileEpIdx2SiteEpIdx(epIdx);
        
        if(StringUtils.isBlank(episodeUrl)){
            Elements epAs = contentDiv.select(".episode_list a.item");
            
            if(epAs.isEmpty()){
                if(siteEpIdx!=null){
                    TvShowEpisode episode = SerializationUtils.clone(base);
                    episode.episode = siteEpIdx;
                    episode.summary = tvShow.summary;
                    episodes.add(episode);
                }
            }else{
                Map<Integer, Element> epAsMap = IntStream.range(0, epAs.size()).mapToObj(i->i).collect(Collectors.toMap(i->i+1, i->epAs.get(i)));
                Map<Integer, Element> toLoadEpAs = new HashMap<>();
                if(epAsMap.containsKey(siteEpIdx)){
                    toLoadEpAs.put(siteEpIdx, epAsMap.get(siteEpIdx));
                }else{
                    if(siteEpIdx==null){
                        toLoadEpAs.putAll(epAsMap);
                    }
                }
                
                toLoadEpAs.forEach((idx, a)->{
                    TvShowEpisode episode = tvshow_episode_loadEpisode(a.absUrl("href"), base);
                    episode.episode = idx;
                    episodes.add(episode);
                });
            }
        }else{
            TvShowEpisode episode = tvshow_episode_loadEpisode(episodeUrl, base);
            if(siteEpIdx!=null){
                episode.episode = siteEpIdx;
            }
            episodes.add(episode);
        }
        
        for(TvShowEpisode ep : episodes){
            ep.episode = config.siteEpIdx2FileEpIdx(ep.episode);
            if(StringUtils.isBlank(ep.tagline)) ep.tagline = "第"+ep.episode+"集";
        }
        
        return episodes;
    }
    private TvShowEpisode tvshow_episode_loadEpisode(String episodeUrl, TvShowEpisode base) {
        TvShowEpisode episode = SerializationUtils.clone(base);
        
        log.info("load episode {}", episodeUrl);
        String episodeCnt = loadPage(episodeUrl);
        if(log.isTraceEnabled()) log.trace("load episode cnt {}", episodeCnt);
        Document episodeHtml = Jsoup.parse(episodeCnt);
        
        Element contentDiv = episodeHtml.selectFirst("#content");
        if(contentDiv==null) return null;
        
        String nameCn=null,nameOrigin=null;
        
        Elements epInfoLis = contentDiv.select("ul.ep-info li");
        for(Element li : epInfoLis){
            Element nameSpan = li.selectFirst("span");
            if(nameSpan==null) continue;
            String name = nameSpan.text().trim();
            if(StringUtils.isBlank(name)) continue;
            
            Element valueSpan,hideSpan;
            switch(name){
            case "本集中文名:":
                valueSpan = li.selectFirst("span.all");
                if(valueSpan==null) continue;
                nameCn = valueSpan.text().trim();
                break;
            case "本集原名:":
                valueSpan = li.selectFirst("span.all");
                if(valueSpan==null) continue;
                nameOrigin = valueSpan.text().trim();
                break;
            case "播放时间:":
                valueSpan = li.selectFirst("span.all");
                if(valueSpan==null) continue;
                episode.original_available = valueSpan.text().trim();
                break;
            case "剧情简介:":
                valueSpan = li.selectFirst("span.all");
                if(valueSpan==null) continue;
                episode.summary = valueSpan.text().trim();
                hideSpan = li.selectFirst("span.hide");
                if(hideSpan!=null) episode.summary += hideSpan.text().trim();
                break;
            }
        }
        if(StringUtils.isNotBlank(nameOrigin) && StringUtils.equals(nameCn, nameOrigin)){
            episode.tagline = nameOrigin;
        }else{
            if(StringUtils.isNotBlank(nameCn) && StringUtils.isNotBlank(nameOrigin)){
                episode.tagline = nameCn+"　"+nameOrigin;
            }else if(StringUtils.isNotBlank(nameCn) && StringUtils.isBlank(nameOrigin)){
                episode.tagline = nameCn;
            }else if(StringUtils.isBlank(nameCn) && StringUtils.isNotBlank(nameOrigin)){
                episode.tagline = nameOrigin;
            }
        }
        
        if(StringUtils.isBlank(episode.summary)) episode.summary = base.summary;
        
        return episode;
    }
    
    private void loadBackdrops(String mainpicUrl, List<String> backdrops, String publishPrefix) {
        log.info("load backdrop list {}", mainpicUrl);
        String mainpicCnt = loadPage(mainpicUrl);
//        String mainpicCnt = fetchPage(mainpicUrl);
        if(log.isTraceEnabled()) log.trace("load backdrops cnt {}", mainpicCnt);
        
        Document mainpicDoc = Jsoup.parse(mainpicCnt);
        mainpicDoc.setBaseUri(mainpicUrl);
        Element ul = mainpicDoc.selectFirst("#content ul");
        if(ul==null) return;
        
        //统计背景图及其像素量
        List<Triple<String, Long, Long>> coverAndSizes = new ArrayList<>();
        Elements lis = ul.select("li");
        for(Element li : lis){
            Element coverImg = li.selectFirst("div.cover a img");
            if(coverImg==null) continue;
            String coverUrl = coverImg.absUrl("src");
            if(StringUtils.isBlank(coverUrl)) continue;
//            coverUrl = coverUrl.replace("view/photo/m/public", "view/photo/1/public");
//            coverUrl = coverUrl.replace("view/photo/m/public", "view/photo/raw/public");
            coverUrl = staticController.jpgWrap(publishPrefix, coverUrl);
            
            long w=0,h=0;
            Element propDiv = li.selectFirst("div.prop");
            if(propDiv!=null){
                String prop = propDiv.text().trim();
                if(StringUtils.isNotBlank(prop)){
                    String[] wh = prop.split("x");
                    try{
                        w = Long.valueOf(wh[0]);
                        h = Long.valueOf(wh[1]);
                    }catch(Exception ig){}
                }
            }
            coverAndSizes.add(Triple.of(coverUrl, w, h));
        }
        if(coverAndSizes.isEmpty()) return;
        
        List<Triple<String, Long, Long>> landscapes = coverAndSizes.stream().filter(t->t.getMiddle()>t.getRight()).collect(Collectors.toList());
        if(landscapes.size()>0){ //尝试只要横向图
            coverAndSizes = landscapes;
        }
        //按像素量倒排
        coverAndSizes.sort((p1,p2)->-Long.compare(p1.getMiddle()*p1.getRight(), p2.getMiddle()*p2.getRight()));
        //取前5个
        coverAndSizes = new ArrayList<>(coverAndSizes.subList(0, Math.min(coverAndSizes.size(), 5)));
        //顺序随机下
        Collections.shuffle(coverAndSizes);
        backdrops.addAll(coverAndSizes.stream().map(p->p.getLeft()).collect(Collectors.toList()));
    }
    
    @SneakyThrows
    private synchronized String loadPage(String url) {
        String cached = cacher.loadAsStr(url);
        if(cached!=null) return cached;
        
        return drivers.searchOpen(url, (driver, src)->{
            if(!isLogined(src)){
                log.error("未登录，需先登录！");
                throw new RuntimeException("未登录");
            }
            if(url.equals(driver.getCurrentUrl())) cacher.saveAsStr(url, src);
            return src;
        });
    }
    @SneakyThrows
    private synchronized String fetchPage(String url) {
        String cached = cacher.loadAsStr(url);
        if(cached!=null) return cached;
        
        byte[] bs = staticService.down(url);
        cacher.save(url, bs);
        return new String(bs);
    }
    
    private boolean isSubjectUrl(String url) {
        URL u;
        try{
            u = new URL(url);
        }catch(Exception ig){
            return false;
        }
        if(!config.getDoubanMovieDomain().equals(u.getHost())) return false;
        if(!PathMatcher.match(SubjectPattern, u.getPath())) return false;
        return true;
    }
    
    private boolean isEpisodeUrl(String url) {
        URL u;
        try{
            u = new URL(url);
        }catch(Exception ig){
            return false;
        }
        if(!config.getDoubanMovieDomain().equals(u.getHost())) return false;
        if(!PathMatcher.match(EpisodePattern, u.getPath())) return false;
        return true;
    }
    
    /**
     * 用 webDriver4Search 检查登录状态
     * 如已登录，则返回已登录
     * 如果未登录
     *     销毁webDriver4Static
     *     webDriver4Search跳转登录页，切换扫码模式，获取二维码地址
     *     返回二维码地址
     * 
     */
    @SneakyThrows
    public synchronized LoginState login(String publishPrefix) {
        LoginState state = new LoginState();
        
        log.info("redirect login check url : {}", config.getDoubanLoginCheckUrl());
        drivers.searchOpen(config.getDoubanLoginCheckUrl(), (driver, src)->{
            state.logined = isLogined(src);
            return null;
        });
        log.info("isLogined : {}", state.logined);
        if(state.logined) return state;
        
        log.info("staticer destory");
        drivers.staticerDestory();
        
        log.info("redirect login url : {}", config.getDoubanLoginUrl());
        drivers.searchOpen(config.getDoubanLoginUrl(), (driver, src)->{
            log.trace("try find login switch btn and click it");
            driver.findElementByCssSelector(".quick.icon-switch").click();
            
            String qrLoginImgUrl = driver.findElementByCssSelector("div.account-qr-scan img").getAttribute("src");
            log.trace("found qr login img : {}", qrLoginImgUrl);
            state.qrLoginImg = staticController.jpgWrap(publishPrefix, qrLoginImgUrl);
            return null;
        });
        return state;
    }
    
    /**
     * 循环 用 webDriver4Search 检查登录状态
     * 未登录则继续循环，直到超时，或
     * 已登录
     *     提取并记录cookie
     *     返回已登录
     */
    @SneakyThrows
    public synchronized LoginState checkLoginSuccess() {
        if(!drivers.isSearcherInited()) throw new ResponseStatusException(BAD_REQUEST, "请先进行登录");
        
        drivers.searchDo(driver->{
            Utils.waitUntil(()->{
                return isLogined(driver);
            }, config.getDoubanLoginWaitTimeoutMillis());
            
            HashSet<Cookie> cookies = new HashSet<>(driver.manage().getCookies());
            File cookiesFile = new File(Consts.Folder_Cookies, config.getDoubanDomain());
            log.trace("save cookies\n{}\n{}", cookies, cookiesFile.getAbsolutePath());
            FileUtils.writeByteArrayToFile(cookiesFile, SerializationUtils.serialize(cookies));
        });
        LoginState state = new LoginState();
        state.logined = true;
        return state;
    }
    private static boolean isLogined(RemoteWebDriver webDriver) {
        return isLogined(webDriver.getPageSource());
    }
    private static boolean isLogined(String pageSource) {
        return Jsoup.parse(pageSource)
                .selectFirst("div#db-global-nav div.top-nav-info li.nav-user-account")!=null;
    }
    
    public static class LoginState {
        public boolean logined;
        public String qrLoginImg;
    }
    
}