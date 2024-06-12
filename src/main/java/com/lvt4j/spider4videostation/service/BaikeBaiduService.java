package com.lvt4j.spider4videostation.service;

import static com.lvt4j.spider4videostation.Consts.PathMatcher;
import static com.lvt4j.spider4videostation.Consts.Tpl_Orig_Html;
import static com.lvt4j.spider4videostation.Utils.ObjectMapper;
import static com.lvt4j.spider4videostation.Utils.isUrl;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.PageLoadStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.lvt4j.spider4videostation.Config;
import com.lvt4j.spider4videostation.PluginType;
import com.lvt4j.spider4videostation.controller.StaticController;
import com.lvt4j.spider4videostation.pojo.Args;
import com.lvt4j.spider4videostation.pojo.Movie;
import com.lvt4j.spider4videostation.pojo.Rst;
import com.lvt4j.spider4videostation.pojo.TvShow;
import com.lvt4j.spider4videostation.pojo.TvShowEpisode;
import com.lvt4j.spider4videostation.service.Drivers.DriverMeta;
import com.lvt4j.spider4videostation.service.Drivers.Type;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author LV on 2023年1月17日
 */
@Slf4j
@Service
public class BaikeBaiduService implements SpiderService {

    public static final String Name = "baidu";
    
    private static final String ItemPattern = "/item/{itemName}/{itemId}";
    private static final String ItemNameOnlyPattern = "/item/{itemName}";

    @Autowired
    private Config config;
    
    @Autowired
    private FileCacher cacher;
    @Autowired
    private Drivers drivers;
    
    @Autowired@Lazy
    private StaticController staticController;
    
    private DriverMeta driver;
    
    @PostConstruct
    private void init(){
        driver = drivers.driver(Name, Type.Searcher);
        driver.optionsCustom(opts->{
            opts.setPageLoadStrategy(PageLoadStrategy.NORMAL);
            opts.addExtensions(new File("ChromeExtensions/XHRInterceptorExtension.crx"));
        });
    }
    
    @Override
    public boolean support(PluginType plugin, Args args) {
        if(PluginType.BaikeBaidu!=plugin) return false;
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
        default:
            break;
        }
    }
    
    private void movie_search(String pluginId, String publishPrefix, Args args, Rst rst) {
        args.limit = Math.min(args.limit, config.getBaikeBaiduMaxLimit());
        
        if(isUrl(args.input.title)){
            if(isItemUrl(args.input.title)){
                String detailUrl = args.input.title;
                
                Movie movie = null;
                try{
                    movie = movie_loadItem(pluginId, publishPrefix, detailUrl);
                }catch(Throwable e){
                    log.error("error load detail {}", detailUrl, e);
                    return;
                }
                if(movie==null) return;
                
                rst.result.add(movie);
                return;
            }
            return;
        }
        
        String searchUrl = UriComponentsBuilder.fromHttpUrl(config.getBaikeBaiduSearchUrl())
            .queryParam("word", args.input.title).toUriString();
        log.info("open search {}", searchUrl);
        String searchCnt;
        try{
            searchCnt = loadSearchPage(searchUrl);
            if(log.isTraceEnabled()) log.trace("search rst {}", searchCnt);
        }catch(Exception e){
            log.error("error on search {}", searchUrl, e);
            return;
        }
        
        Document doc = Jsoup.parse(searchCnt);
        doc.setBaseUri(searchUrl);
        
        int rstNum = 0;
        
        Elements itemAs = doc.select("div#body_wrapper dl.search-list.J-search-list dd.search-list-item.J-search-list-item a.result-title.J-result-title");
        for(Element itemA : itemAs){
            String detailUrl;
            try{
                detailUrl = itemA.absUrl("href");
                if(StringUtils.isBlank(detailUrl)) continue;
                if(!isItemUrl(detailUrl)) continue;
                URI url = new URI(detailUrl);
                detailUrl = UriComponentsBuilder.fromUri(url)
                    .scheme("https").replacePath(url.getPath()).toUriString();
            }catch(Exception ig){
                continue;
            }
            
            Movie movie = null;
            try{
                movie = movie_loadItem(pluginId, publishPrefix, detailUrl);
            }catch(Throwable e){
                log.error("error load detail {}", detailUrl, e);
                continue;
            }
            
            if(movie==null) continue;
            
            rst.result.add(movie);
            if(args.reachLimit(++rstNum)) break;
        }
    }
    private Movie movie_loadItem(String pluginId, String publishPrefix, String detailUrl) throws Throwable {
        Movie movie = new Movie(pluginId);
        
        log.info("load detail {}", detailUrl);
        Triple<Document, JsonNode, JsonNode> itemPage = loadItemPage(detailUrl, false);
        if(itemPage==null) return null;
        
        Document detailHtml = itemPage.getLeft();
        JsonNode pageDataJson = itemPage.getMiddle();
        
        Map<String, List<String>> cardInfos = cardInfos(pageDataJson);
        
        if(!cardInfos.containsKey("导演")) return null;
        if(cardInfos.containsKey("集数")) return null;
        
        cardInfos.getOrDefault("导演", emptyList()).forEach(movie.director::add);
        cardInfos.getOrDefault("编剧", emptyList()).forEach(movie.writer::add);
        cardInfos.getOrDefault("主演", emptyList()).forEach(movie.actor::add);
        cardInfos.getOrDefault("类型", emptyList()).forEach(movie.genre::add);
        movie.certificate = cardInfos.getOrDefault("适合年龄", emptyList()).stream().findFirst().orElse(StringUtils.EMPTY);
        movie.title = title(cardInfos);
        movie.original_available = original_available(cardInfos);
        movie.summary = summary(detailHtml);
        
        loadPosterAndBackdrops(detailUrl, pageDataJson, movie.extra().poster, movie.extra().backdrop, publishPrefix);
        
        return movie;
    }
    
    @SneakyThrows
    private void tvshow_search(String pluginId, String publishPrefix, Args args, Rst rst) {
        args.limit = Math.min(args.limit, config.getBaikeBaiduMaxLimit());
        
        if(isUrl(args.input.title)){
            if(isItemUrl(args.input.title)){
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
        
        String searchUrl = UriComponentsBuilder.fromHttpUrl(config.getBaikeBaiduSearchUrl())
            .queryParam("word", args.input.title).toUriString();
        log.info("open search {}", searchUrl);
        String searchCnt;
        try{
            searchCnt = loadSearchPage(searchUrl);
            if(log.isTraceEnabled()) log.trace("search rst {}", searchCnt);
        }catch(Exception e){
            log.error("error on search {}", searchUrl, e);
            return;
        }
        
        Document doc = Jsoup.parse(searchCnt);
        doc.setBaseUri(searchUrl);
        
        int rstNum = 0;
        
        Elements itemAs = doc.select("div#body_wrapper dl.search-list.J-search-list dd.search-list-item.J-search-list-item a.result-title.J-result-title");
        for(Element itemA : itemAs){
            String detailUrl;
            try{
                detailUrl = itemA.absUrl("href");
                if(StringUtils.isBlank(detailUrl)) continue;
                if(!isItemUrl(detailUrl)) continue;
                URI url = new URI(detailUrl);
                detailUrl = UriComponentsBuilder.fromUri(url)
                    .scheme("https").replacePath(url.getPath()).toUriString();
            }catch(Exception ig){
                continue;
            }
            
            TvShow tvShow = null;
            try{
                tvShow = tvshow_loadItem(pluginId, publishPrefix, detailUrl);
            }catch(Exception e){
                log.error("error load detail {}", detailUrl, e);
                continue;
            }
            
            if(tvShow==null) continue;
            
            rst.result.add(tvShow);
            if(args.reachLimit(++rstNum)) break;
        }
    }
    private TvShow tvshow_loadItem(String pluginId, String publishPrefix, String detailUrl) throws Throwable {
        TvShow tvShow = new TvShow(pluginId);
        
        log.info("load detail {}", detailUrl);
        Triple<Document, JsonNode, JsonNode> itemPage = loadItemPage(detailUrl, false);
        if(itemPage==null) return null;
        
        Document detailHtml = itemPage.getLeft();
        JsonNode pageDataJson = itemPage.getMiddle();
        
        Map<String, List<String>> cardInfos = cardInfos(pageDataJson);
        
        if(!isTvShow(detailHtml, cardInfos)) return null;
        
        tvShow.title = title(cardInfos);
        tvShow.original_available = original_available(cardInfos);
        tvShow.summary = summary(detailHtml);
        
        loadPosterAndBackdrops(detailUrl, pageDataJson, tvShow.extra().poster, tvShow.extra().backdrop, publishPrefix);
        
        return tvShow;
    }
    
    @SneakyThrows
    private void tvshow_episode_search(String pluginId, String publishPrefix, Args args, Rst rst) {
        args.limit = Math.min(args.limit, config.getBaikeBaiduMaxLimit());
        
        if(isUrl(args.input.title)){
            if(isItemUrl(args.input.title)){
                String detailUrl = args.input.title;
                List<TvShowEpisode> episodes = null;
                try{
                    episodes = tvshow_episode_loadItem(pluginId, publishPrefix, detailUrl, false, args.input.season, args.input.episode);
                    episodes.forEach(ep->ep.detailModeChange(detailUrl));
                }catch(Exception e){
                    log.error("error load detail {}", detailUrl, e);
                    return;
                }
                rst.result.addAll(episodes);
                return;
            }
            return;
        }
        
        String searchUrl = UriComponentsBuilder.fromHttpUrl(config.getBaikeBaiduSearchUrl())
            .queryParam("word", args.input.title).toUriString();
        log.info("open search {}", searchUrl);
        String searchCnt;
        try{
            searchCnt = loadSearchPage(searchUrl);
            if(log.isTraceEnabled()) log.trace("search rst {}", searchCnt);
        }catch(Exception e){
            log.error("error on search {}", searchUrl, e);
            return;
        }
        
        Document doc = Jsoup.parse(searchCnt);
        doc.setBaseUri(searchUrl);
        
        int rstNum = 0;
        
        Elements itemAs = doc.select("div#body_wrapper dl.search-list.J-search-list dd.search-list-item.J-search-list-item a.result-title.J-result-title");
        for(Element itemA : itemAs){
            String detailUrl;
            try{
                detailUrl = itemA.absUrl("href");
                if(StringUtils.isBlank(detailUrl)) continue;
                if(!isItemUrl(detailUrl)) continue;
                URI url = new URI(detailUrl);
                detailUrl = UriComponentsBuilder.fromUri(url)
                    .scheme("https").replacePath(url.getPath()).toUriString();
            }catch(Exception ig){
                continue;
            }
            
            List<TvShowEpisode> episodes = null;
            try{
                episodes = tvshow_episode_loadItem(pluginId, publishPrefix, detailUrl, true, args.input.season, args.input.episode);
            }catch(Exception e){
                log.error("error load detail {}", detailUrl, e);
                continue;
            }
            
            rst.result.addAll(episodes);
            if(args.reachLimit(++rstNum)) break;
        }
    }
    private List<TvShowEpisode> tvshow_episode_loadItem(String pluginId, String publishPrefix, String detailUrl, boolean strictMode, Integer season, Integer epIdx) throws Throwable {
        List<TvShowEpisode> episodes = new LinkedList<>();
        TvShowEpisode base = new TvShowEpisode(pluginId);
        if(season!=null) base.season = season;
        TvShow tvShow = new TvShow(pluginId);
        
        log.info("load detail {}", detailUrl);
        Triple<Document, JsonNode, JsonNode> itemPage = loadItemPage(detailUrl, true);
        if(itemPage==null) return null;
        
        Document detailHtml = itemPage.getLeft();
        JsonNode pageDataJson = itemPage.getMiddle();
        JsonNode dataModuleValueJson = itemPage.getRight();
        
        Map<String, List<String>> cardInfos = cardInfos(pageDataJson);
        if(strictMode && !isTvShow(detailHtml, cardInfos)) return null;
        
        cardInfos.getOrDefault("导演", emptyList()).forEach(base.director::add);
        cardInfos.getOrDefault("编剧", emptyList()).forEach(base.writer::add);
        cardInfos.getOrDefault("主演", emptyList()).forEach(base.actor::add);
        cardInfos.getOrDefault("类型", emptyList()).forEach(base.genre::add);
        base.certificate = cardInfos.getOrDefault("适合年龄", emptyList()).stream().findFirst().orElse(StringUtils.EMPTY);
        
        tvShow.title = base.title = title(cardInfos);
        tvShow.original_available = base.original_available = original_available(cardInfos);
        tvShow.summary = summary(detailHtml);
        
        loadPosterAndBackdrops(detailUrl, pageDataJson, tvShow.extra().poster, tvShow.extra().backdrop, publishPrefix);
        
        base.extra().tvshow = tvShow;
        
        if(dataModuleValueJson==null) return episodes;
        
        Map<Integer, TvShowEpisode> episodeMap = new TreeMap<>();
        for(int i = 0; i < dataModuleValueJson.size(); i++){
            JsonNode plotJson = dataModuleValueJson.get(i);
            
            TvShowEpisode episode = SerializationUtils.clone(base);
            episode.episode = i+1;
            episode.tagline = plotJson.at("/plotTitle/text/0/text").asText();
            List<String> paragraphs = new LinkedList<>();
            plotJson.at("/plotDetail/text").forEach(p->{
                StringBuilder paragraph = new StringBuilder();
                p.at("/content").forEach(c->{
                    String text = c.path("text").asText();
                    if(StringUtils.isBlank(text)) return;
                    paragraph.append(text);
                });
                paragraphs.add(paragraph.toString());
            });
            if(paragraphs.size()==1) {
                episode.summary = paragraphs.get(0);
            }else{
                episode.summary = paragraphs.stream().map(p->"　　"+p).collect(joining("\n"));
            }
            plotJson.at("/plotImg/text").forEach(t->{
                String imgUrl = t.path("text").asText();
                if(StringUtils.isNotBlank(imgUrl)) episode.extra().poster.add(0, imgUrl);
            });
            episodeMap.put(episode.episode, episode);
        }
        
        Integer siteEpIdx = config.fileEpIdx2SiteEpIdx(epIdx);
        if(siteEpIdx==null){
            episodes.addAll(episodeMap.values());
        }else{
            if(episodeMap.isEmpty()){
                TvShowEpisode episode = SerializationUtils.clone(base);
                episode.episode = epIdx;
                episode.tagline = "第"+siteEpIdx+"集";
                episode.summary = tvShow.summary;
                episodes.add(episode);
            }else{
                TvShowEpisode episode = episodeMap.get(siteEpIdx);
                if(episode!=null) episodes.add(episode);
            }
        }
        
        for(TvShowEpisode ep : episodes){
            ep.episode = config.siteEpIdx2FileEpIdx(ep.episode);
        }
        
        return episodes;
    }
    
    private String summary(Document doc) {
        //顺序提取标题和内容
        Element jLemmaCntDiv = doc.selectFirst(".J-lemma-content");
        if(jLemmaCntDiv==null) return StringUtils.EMPTY;
        
        List<Pair<String, List<String>>> paras = new LinkedList<>();
        String curParaTitle = null;
        List<String> curParaCnts = null;
        for(Element child: jLemmaCntDiv.children()){
            if(child.is("div[class^=paraTitle_][data-level=1]")) {
                if(CollectionUtils.isNotEmpty(curParaCnts)){ //整理上一个已提取的内容
                    paras.add(Pair.of(curParaTitle, curParaCnts));
                    curParaTitle = null; curParaCnts = null;
                }
                Element titleH2 = child.selectFirst("h2");
                if(titleH2!=null) {
                    curParaTitle = titleH2.text().trim();
                    curParaCnts = new LinkedList<>();
                }
            }else if(child.is("div[class^=para_]")){
                if(curParaTitle!=null) {
                    child.select("[class^=albumWrapper_]").remove();
                    child.select(".lemma-album").remove();
                    child.select("[class^=lemmaAlbum_]").remove();
                    child.select("[class^=lemmaPicture_]").remove();
                    child.select(".lemma-picture").remove();
                    child.select("sup").remove();
                    child.select(".sup-anchor").remove();
                    child.select("[class^=supAnchor_]").remove();
                    child.select(".lemma-album-marquee").remove();
                    child.select("[class^=lemmaAlbumMarquee_]").remove();
                    child.select("[data-module-type=album]").remove();
                    
                    String para = child.text().trim();
                    if(StringUtils.isNotBlank(para)) {
                        curParaCnts.add("　　"+para);
                    }
                }
            }
        }
        if(CollectionUtils.isNotEmpty(curParaCnts)){ //整理最后一个已提取的内容
            paras.add(Pair.of(curParaTitle, curParaCnts));
            curParaTitle = null; curParaCnts = null;
        }
        
        final Set<String> summaryTitles = ImmutableSet.of("剧情简介", "故事简介", "故事概要", "故事梗概");
        
        paras = paras.stream().filter(p->summaryTitles.contains(p.getLeft())).collect(Collectors.toList());
        
        if(paras.isEmpty()) return StringUtils.EMPTY;
        if(paras.size()==1) return paras.get(0).getRight().stream().collect(Collectors.joining("\n"));
        return paras.stream().map(p->{
            String title = p.getLeft();
            List<String> cnts = p.getRight();
            return title + "\n" + cnts.stream().collect(Collectors.joining("\n"));
        }).collect(Collectors.joining("\n\n"));
    }
//    @SneakyThrows
//    private void loadPosterAndBackdrops(String detailUrl, Document detailHtml, List<String> posters, List<String> backdrops, String publishPrefix) {
//        //点击图册链接
//        Element albumA = detailHtml.selectFirst("a[class^=albumLink_]");
//        if(albumA==null) return;
//        String picUrl = albumA.absUrl("href");
//        String className = albumA.className();
//        String clickScript = "var picA = document.getElementsByClassName('"+className+"')[0];picA.removeAttribute('target');picA.click()";
//        driver.exec(clickScript, (driver, rst)->null);
//        //图册页面监控图册接口数据
//        String getInterceptedAlbumsResponseScript = Tpl_LatestXHr_Script.replace("@@UrlPrefix@@", "https://baike.baidu.com/lemma/api/image/albums");
//        String getInterceptedAlbumsResponse = driver.exec(getInterceptedAlbumsResponseScript, (driver, rst)->rst);
//        log.info("从图片地址获取图片数据: {}\n{}", picUrl, getInterceptedAlbumsResponse);
//        if(StringUtils.isBlank(getInterceptedAlbumsResponse)) return;
//        JsonNode responseJson = Utils.ObjectMapper.readTree(getInterceptedAlbumsResponse);
//        int status = responseJson.path("status").asInt();
//        if(status!=200) throw new RuntimeException("从图片地址获取图片数据失败");
//        
//        JsonNode albumJsons = Utils.ObjectMapper.readTree(responseJson.path("responseText").asText()).at("/data/list");
//        if(albumJsons.size()==0) return;
//        
//        Set<String> allPicUrls = new HashSet<>();
//        List<Triple<String, Integer, Integer>> allPicInfos = new LinkedList<>(); //图片链接、宽、高
//        Map<String, List<Triple<String, Integer, Integer>>> albumImgs = new HashMap<>();
//        
//        String imgUrlPrefix = "https://bkimg.cdn.bcebos.com/pic/";
//        albumJsons.forEach(albumJson->{
//            List<Triple<String, Integer, Integer>> albumImgData = new LinkedList<>();
//            String coverPicSrc = albumJson.at("/coverPic/src").asText();
//            if(StringUtils.isNotBlank(coverPicSrc)) {
//                String imgUrl = imgUrlPrefix+coverPicSrc;
//                if(!allPicUrls.contains(imgUrl)) {
//                    int width = albumJson.at("/coverPic/width").asInt();
//                    int height = albumJson.at("/coverPic/height").asInt();
//                    allPicInfos.add(Triple.of(imgUrl, width, height));
//                    albumImgData.add(Triple.of(imgUrl, width, height));
//                }
//            }
//            albumJson.path("content").forEach(picJson->{
//                String src = picJson.path("src").asText();
//                if(StringUtils.isBlank(src)) return;
//                String imgUrl = imgUrlPrefix+src;
//                if(allPicUrls.contains(imgUrl)) return;
//                int width = picJson.path("width").asInt();
//                int height = picJson.path("height").asInt();
//                allPicInfos.add(Triple.of(imgUrl, width, height));
//                albumImgData.add(Triple.of(imgUrl, width, height));
//            });
//            albumImgs.put(albumJson.path("desc").asText(), albumImgData);
//        });
//        
//        List<Triple<String, Integer, Integer>> posterInfos = Stream.of("概述图册","高清海报").map(albumImgs::get).filter(Objects::nonNull)
//            .flatMap(imgs->imgs.stream()).collect(Collectors.toList());
//        if(posterInfos.isEmpty()) posterInfos = allPicInfos;
//        List<Triple<String, Integer, Integer>> portraits = posterInfos.stream().filter(t->t.getMiddle()<t.getRight()).collect(Collectors.toList());
//        if(portraits.size()>0) posterInfos = portraits; //海报尝试只要纵向图
//        
//        List<Triple<String, Integer, Integer>> backdropInfos = Stream.of("概述图册", "高清海报", "高清剧照", "剧照").map(albumImgs::get).filter(Objects::nonNull)
//            .flatMap(imgs->imgs.stream()).collect(Collectors.toList());
//        if(backdropInfos.isEmpty()) backdropInfos = allPicInfos;
//        List<Triple<String, Integer, Integer>> landscapes = backdropInfos.stream().filter(t->t.getMiddle()>t.getRight()).collect(Collectors.toList());
//        if(landscapes.size()>0) backdropInfos = landscapes; //背景图尝试只要横向图
//        
//        Function<List<Triple<String, Integer, Integer>>, List<String>> tryBest = new Function<List<Triple<String,Integer,Integer>>, List<String>>() {
//            @Override
//            public List<String> apply(List<Triple<String, Integer, Integer>> infos) {
//                //按像素量倒排
//                infos.sort((p1,p2)->-Double.compare(p1.getMiddle()*p1.getRight(), p2.getMiddle()*p2.getRight()));
//                //取前5个
//                infos = new ArrayList<>(infos.subList(0, Math.min(infos.size(), 5)));
//                //顺序随机下
//                Collections.shuffle(infos);
//                return infos.stream().map(p->p.getLeft()).collect(Collectors.toList());
//            }
//        };
//        
//        posters.addAll(tryBest.apply(posterInfos).stream().map(url->staticController.jpgWrap(publishPrefix, url, Name)).collect(Collectors.toList()));
//        backdrops.addAll(tryBest.apply(backdropInfos).stream().map(url->staticController.jpgWrap(publishPrefix, url, Name)).collect(Collectors.toList()));
//    }
//    
//    private String extractImgUrl(Element imgEle, String publishPrefix) {
//        if(imgEle==null) return null;
//        Function<String, String> imgUrlTidy = new Function<String, String>() {
//            @Override
//            public String apply(String url) {
//                if(StringUtils.isBlank(url)) return null;
//                try{
//                    URL u = new URL(url);
//                    if(!PathMatcher.match(PicPattern, u.getPath())) return null;
//                    String rawUrl = UriComponentsBuilder.fromHttpUrl(url).replaceQuery(null).toUriString();
//                    return staticController.jpgWrap(publishPrefix, rawUrl, Name);
//                }catch(Exception ig){
//                    return null;
//                }
//            }
//        };
//        String imgUrl = imgUrlTidy.apply(imgEle.absUrl("src"));
//        if(StringUtils.isBlank(imgUrl)) imgUrl = imgUrlTidy.apply(imgEle.absUrl("data-src"));
//        if(StringUtils.isBlank(imgUrl)) return null;
//        return imgUrl;
//    }
    
    private boolean isItemUrl(String url) {
        URL u;
        try{
            u = new URL(url);
        }catch(Exception ig){
            return false;
        }
        if(!config.getBaikeBaiduDomain().equals(u.getHost())) return false;
        if(!PathMatcher.match(ItemPattern, u.getPath()) && !PathMatcher.match(ItemNameOnlyPattern, u.getPath())) return false;
        return true;
    }
    
    private boolean isTvShow(Document detailHtml, Map<String, List<String>> cardInfos) {
        if(detailHtml.selectFirst("#dramaSeries")!=null) return true;
        if(!cardInfos.containsKey("集数") && !cardInfos.containsKey("剧集数量") && !cardInfos.containsKey("每集时长")) return false;
        return true;
    }
    
    private Map<String, List<String>> cardInfos(JsonNode pageData) {
        Map<String, List<String>> cards = new LinkedHashMap<>();
        JsonNode card = pageData.path("card");
        Consumer<JsonNode> itemHanlder = item->{
            String title = item.path("title").asText();
            if(StringUtils.isBlank(title)) return;
            
            List<String> values = new LinkedList<>();
            item.path("data").forEach(d->{
                String dataType = d.path("dataType").asText();
                if(StringUtils.isBlank(dataType)) return;
                switch(dataType){
                case "text": case "lemma": case "dateTime": case "currency": case "number":
                    StringBuilder text = new StringBuilder();
                    d.path("text").forEach(txt->{
                        String t = txt.path("text").asText();
                        if(StringUtils.isNotBlank(t)) text.append(t);
                    });
                    if(text.length()>0) values.add(text.toString());
                    break;
                }
            });
            if(values.isEmpty()) return;
            cards.put(title, values);
        };
        card.path("left").forEach(itemHanlder);
        card.path("right").forEach(itemHanlder);
        return cards;
    }
    private String title(Map<String, List<String>> cardInfos) {
        String cnName = cardInfos.getOrDefault("中文名", emptyList()).stream().findFirst().orElse(null);
        String alia = cardInfos.getOrDefault("别名", emptyList()).stream().findFirst().orElse(null);
        String originName = cardInfos.getOrDefault("原版名称", emptyList()).stream().findFirst().orElse(null);
        String foreignName = cardInfos.getOrDefault("外文名", emptyList()).stream().findFirst().orElse(null);
        
        String title = StringUtils.firstNonBlank(cnName, originName, alia);
        if(StringUtils.isNotBlank(foreignName)){
            if(StringUtils.isNotBlank(title)) title = title+"　"+foreignName;
            else title = foreignName;
        }
        
        return title;
    }
    private String original_available(Map<String, List<String>> cardInfos) {
        if(StringUtils.isNotBlank(config.getOriginalAvailable())) return config.getOriginalAvailable();
        
        Function<String, Date> parseDate = new Function<String, Date>() {
            Map<String, String> patterns = ImmutableMap.of(
                "\\d{4}年\\d{1,2}月\\d{1,2}日", "yyyy年M月d日"
                ,"\\d{4}年\\d{1,2}月", "yyyy年M月"
                ,"\\d{4}年", "yyyy年"
                ,"\\d{4}", "yyyy"
            );
            
            @Override
            public Date apply(String str) {
                if(StringUtils.isBlank(str)) return null;
                str = str.replaceAll(" ", "");
                for(String regex : patterns.keySet()){
                    Matcher m = Pattern.compile(regex).matcher(str);
                    if(!m.find()) continue;
                    String date = m.group(0);
                    try{
                        return DateUtils.parseDateStrictly(date, patterns.get(regex));
                    }catch(Exception ig){}
                }
                return null;
            }
        };
        
        Date makeDate = parseDate.apply(cardInfos.getOrDefault("出品时间", Collections.emptyList()).stream().findFirst().orElse(null));
        Date beOnDate = parseDate.apply(cardInfos.getOrDefault("上映时间", Collections.emptyList()).stream().findFirst().orElse(null));
        Date playBeginDate = parseDate.apply(cardInfos.getOrDefault("播放期间", Collections.emptyList()).stream().findFirst().orElse(null));
        Date releaseDate = parseDate.apply(cardInfos.getOrDefault("发行日期", Collections.emptyList()).stream().findFirst().orElse(null));
        Date releaseTime = parseDate.apply(cardInfos.getOrDefault("发行时间", Collections.emptyList()).stream().findFirst().orElse(null));
        Date firstPlayTime = parseDate.apply(cardInfos.getOrDefault("首播时间", Collections.emptyList()).stream().findFirst().orElse(null));
        Date hkPlayDate = parseDate.apply(cardInfos.getOrDefault("香港首播", Collections.emptyList()).stream().findFirst().orElse(null));
        
        Date original_available = ObjectUtils.firstNonNull(beOnDate, releaseTime, playBeginDate, releaseDate, makeDate, firstPlayTime, hkPlayDate);
        if(original_available!=null) return DateFormatUtils.format(original_available, "yyyy-MM-dd");
        
        //尝试扫所有基础信息的值，进行时间提取
        original_available = cardInfos.values().stream().flatMap(List::stream).map(parseDate).filter(Objects::nonNull).findFirst().orElse(null);
        if(original_available!=null) return DateFormatUtils.format(original_available, "yyyy-MM-dd");
        
        return StringUtils.EMPTY;
    }
    @SneakyThrows
    private void loadPosterAndBackdrops(String detailUrl, JsonNode pageData, List<String> posters, List<String> backdrops, String publishPrefix) {
        
        Set<String> allPicUrls = new HashSet<>();
        List<Triple<String, Integer, Integer>> allPicInfos = new LinkedList<>(); //图片链接、宽、高
        Map<String, List<Triple<String, Integer, Integer>>> albumImgs = new HashMap<>();
        
        String imgUrlPrefix = "https://bkimg.cdn.bcebos.com/pic/";
        pageData.path("albums").forEach(albumJson->{
            List<Triple<String, Integer, Integer>> albumImgData = new LinkedList<>();
            String coverPicSrc = albumJson.at("/coverPic/src").asText();
            if(StringUtils.isNotBlank(coverPicSrc)) {
                String imgUrl = imgUrlPrefix+coverPicSrc;
                if(!allPicUrls.contains(imgUrl)) {
                    int width = albumJson.at("/coverPic/width").asInt();
                    int height = albumJson.at("/coverPic/height").asInt();
                    allPicInfos.add(Triple.of(imgUrl, width, height));
                    albumImgData.add(Triple.of(imgUrl, width, height));
                }
            }
            albumJson.path("content").forEach(picJson->{
                String src = picJson.path("src").asText();
                if(StringUtils.isBlank(src)) return;
                String imgUrl = imgUrlPrefix+src;
                if(allPicUrls.contains(imgUrl)) return;
                int width = picJson.path("width").asInt();
                int height = picJson.path("height").asInt();
                allPicInfos.add(Triple.of(imgUrl, width, height));
                albumImgData.add(Triple.of(imgUrl, width, height));
            });
            albumImgs.put(albumJson.path("desc").asText(), albumImgData);
        });
        
        List<Triple<String, Integer, Integer>> posterInfos = Stream.of("概述图册","高清海报").map(albumImgs::get).filter(Objects::nonNull)
            .flatMap(imgs->imgs.stream()).collect(Collectors.toList());
        if(posterInfos.isEmpty()) posterInfos = allPicInfos;
        List<Triple<String, Integer, Integer>> portraits = posterInfos.stream().filter(t->t.getMiddle()<t.getRight()).collect(Collectors.toList());
        if(portraits.size()>0) posterInfos = portraits; //海报尝试只要纵向图
        
        List<Triple<String, Integer, Integer>> backdropInfos = Stream.of("概述图册", "高清海报", "高清剧照", "剧照").map(albumImgs::get).filter(Objects::nonNull)
            .flatMap(imgs->imgs.stream()).collect(Collectors.toList());
        if(backdropInfos.isEmpty()) backdropInfos = allPicInfos;
        List<Triple<String, Integer, Integer>> landscapes = backdropInfos.stream().filter(t->t.getMiddle()>t.getRight()).collect(Collectors.toList());
        if(landscapes.size()>0) backdropInfos = landscapes; //背景图尝试只要横向图
        
        Function<List<Triple<String, Integer, Integer>>, List<String>> tryBest = new Function<List<Triple<String,Integer,Integer>>, List<String>>() {
            @Override
            public List<String> apply(List<Triple<String, Integer, Integer>> infos) {
                //按像素量倒排
                infos.sort((p1,p2)->-Double.compare(p1.getMiddle()*p1.getRight(), p2.getMiddle()*p2.getRight()));
                //取前5个
                infos = new ArrayList<>(infos.subList(0, Math.min(infos.size(), 5)));
                //顺序随机下
                Collections.shuffle(infos);
                return infos.stream().map(p->p.getLeft()).collect(Collectors.toList());
            }
        };
        
        posters.addAll(tryBest.apply(posterInfos).stream().map(url->staticController.jpgWrap(publishPrefix, url, Name)).collect(Collectors.toList()));
        backdrops.addAll(tryBest.apply(backdropInfos).stream().map(url->staticController.jpgWrap(publishPrefix, url, Name)).collect(Collectors.toList()));
    }
    
    @SneakyThrows
    private synchronized String loadSearchPage(String url) {
        String cached = cacher.loadAsStr(url);
        if(cached!=null) return cached;
        
        return driver.open(url, (driver, src)->{
            if(url.equals(driver.getCurrentUrl())) cacher.saveAsStr(url, src);
            return src;
        });
    }
    /**
     * 
     * @param url
     * @param needDataModuleValue
     * @return
     */
    @SneakyThrows
    private synchronized Triple<Document, JsonNode, JsonNode> loadItemPage(String url, boolean needDataModuleValue) {
        String pageDatatUrl = UriComponentsBuilder.fromUri(new URI(url)).queryParam("pageData", "1").build().toUriString();
        String dataModuleValueUrl = UriComponentsBuilder.fromUri(new URI(url)).queryParam("dataModuleValue", "1").build().toUriString();
        
        String detailHtml = cacher.loadAsStr(url);
        String pageData = cacher.loadAsStr(pageDatatUrl);
        String dataModuleValue = needDataModuleValue?cacher.loadAsStr(dataModuleValueUrl):null;
        if(detailHtml!=null && pageData!=null && (!needDataModuleValue || (needDataModuleValue && dataModuleValue!=null) ))
            return Triple.of(Jsoup.parse(detailHtml), ObjectMapper.readTree(pageData), dataModuleValue==null?null:ObjectMapper.readTree(dataModuleValue));
        
        detailHtml = driver.open(url, (driver, src)->url.equals(driver.getCurrentUrl())?src:null);
        if(detailHtml==null) return null;
        cacher.saveAsStr(url, detailHtml);
        
        pageData = driver.exec("return JSON.stringify(PAGE_DATA);", (driver, rst)->(String)rst);
        if(pageData==null) return null;
        cacher.saveAsStr(pageDatatUrl, pageData);
        
        if(needDataModuleValue){
            String origHtml = driver.exec(Tpl_Orig_Html, (driver, rst)->(String)rst);
            Document origDoc = Jsoup.parse(origHtml);
            dataModuleValue = Optional.ofNullable(origDoc.selectFirst("div.value-plot[data-module-value]")).map(e->e.attr("data-module-value")).orElse(null);
            if(dataModuleValue!=null) cacher.saveAsStr(dataModuleValueUrl, dataModuleValue);
        }
        
        return Triple.of(Jsoup.parse(detailHtml), ObjectMapper.readTree(pageData), dataModuleValue==null?null:ObjectMapper.readTree(dataModuleValue));
    }
    
}