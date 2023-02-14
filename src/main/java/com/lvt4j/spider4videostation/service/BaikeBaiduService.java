package com.lvt4j.spider4videostation.service;

import static com.lvt4j.spider4videostation.Consts.PathMatcher;
import static com.lvt4j.spider4videostation.Utils.isUrl;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.lvt4j.spider4videostation.Config;
import com.lvt4j.spider4videostation.PluginType;
import com.lvt4j.spider4videostation.Utils;
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
 * @author LV on 2023年1月17日
 */
@Slf4j
@Service
public class BaikeBaiduService implements SpiderService {

    private static final String ItemPattern = "/item/{itemName}/{itemId}";
    private static final String ItemNameOnlyPattern = "/item/{itemName}";
    private static final String PicPattern = "/pic/{picId}";

    private static final String BasicInfoValuesSpilter = "[、，,]";
    
    @Autowired
    private Config config;
    
    @Autowired
    private FileCacher cacher;
    @Autowired
    private Drivers drivers;
//    @Autowired
//    private StaticService staticService;
    
    @Autowired@Lazy
    private StaticController staticController;
    
    @Override
    public boolean support(PluginType plugin, Args args) {
        if(PluginType.BaikeBaidu!=plugin) return false;
        if(!plugin.searchTypes.contains(args.type)) return false;
        if(!plugin.languages.contains(args.lang)) return false;
        return true;
    }

    @Override
    public void search(String pluginId, String publishPrefix, PluginType pluginType, Args args, Rst rst) {
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
        
        String searchUrl = UriComponentsBuilder.fromHttpUrl(config.getBaikeBaiduSearchUrl())
            .queryParam("word", args.input.title).toUriString();
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
            }catch(Exception e){
                log.error("error load detail {}", detailUrl, e);
                continue;
            }
            
            if(movie==null) continue;
            
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
        
        Map<String, String> basicInfos = basicInfos(detailHtml.select("dt.basicInfo-item.name"));
        if(!basicInfos.containsKey("导 演")) return null;
        if(basicInfos.containsKey("集 数")) return null;
        
        Optional.of("导 演").map(basicInfos::get).map(v->v.split(BasicInfoValuesSpilter)).map(Stream::of).orElse(Stream.empty()).forEach(movie.director::add);
        Optional.of("编 剧").map(basicInfos::get).map(v->v.split(BasicInfoValuesSpilter)).map(Stream::of).orElse(Stream.empty()).forEach(movie.writer::add);
        Optional.of("主 演").map(basicInfos::get).map(v->v.split(BasicInfoValuesSpilter)).map(Stream::of).orElse(Stream.empty()).forEach(movie.actor::add);
        Optional.of("类 型").map(basicInfos::get).map(v->v.split(BasicInfoValuesSpilter)).map(Stream::of).orElse(Stream.empty()).forEach(movie.genre::add);
        movie.certificate = Optional.of("适合年龄").orElse(StringUtils.EMPTY);
        movie.title = title(basicInfos);
        movie.original_available = original_available(basicInfos);
        movie.summary = summary(detailHtml);
        
        loadPosterAndBackdrops(detailUrl, detailHtml, movie.extra().poster, movie.extra().backdrop, publishPrefix);
        
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
            searchCnt = loadPage(searchUrl);
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
    private TvShow tvshow_loadItem(String pluginId, String publishPrefix, String detailUrl) {
        TvShow tvShow = new TvShow(pluginId);
        
        log.info("load detail {}", detailUrl);
        String detailCnt = loadPage(detailUrl);
        if(log.isTraceEnabled()) log.trace("load detail cnt {}", detailCnt);
        Document detailHtml = Jsoup.parse(detailCnt);
        
        Map<String, String> basicInfos = basicInfos(detailHtml.select("dt.basicInfo-item.name"));
        if(!isTvShow(detailHtml, basicInfos)) return null;
        
        tvShow.title = title(basicInfos);
        tvShow.original_available = original_available(basicInfos);
        tvShow.summary = summary(detailHtml);
        
        loadPosterAndBackdrops(detailUrl, detailHtml, tvShow.extra().poster, tvShow.extra().backdrop, publishPrefix);
        
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
            searchCnt = loadPage(searchUrl);
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
    private List<TvShowEpisode> tvshow_episode_loadItem(String pluginId, String publishPrefix, String detailUrl, boolean strictMode, Integer season, Integer epIdx) {
        List<TvShowEpisode> episodes = new LinkedList<>();
        TvShowEpisode base = new TvShowEpisode(pluginId);
        if(season!=null) base.season = season;
        TvShow tvShow = new TvShow(pluginId);
        
        log.info("load detail {}", detailUrl);
        String detailCnt = loadPage(detailUrl);
        if(log.isTraceEnabled()) log.trace("load detail cnt {}", detailCnt);
        Document detailHtml = Jsoup.parse(detailCnt); detailHtml.setBaseUri(detailUrl);
        
        Map<String, String> basicInfos = basicInfos(detailHtml.select("dt.basicInfo-item.name"));
        if(strictMode && !isTvShow(detailHtml, basicInfos)) return null;
        
        Optional.of("导 演").map(basicInfos::get).map(v->v.split(BasicInfoValuesSpilter)).map(Stream::of).orElse(Stream.empty()).forEach(base.director::add);
        Optional.of("编 剧").map(basicInfos::get).map(v->v.split(BasicInfoValuesSpilter)).map(Stream::of).orElse(Stream.empty()).forEach(base.writer::add);
        Optional.of("主 演").map(basicInfos::get).map(v->v.split(BasicInfoValuesSpilter)).map(Stream::of).orElse(Stream.empty()).forEach(base.actor::add);
        Optional.of("类 型").map(basicInfos::get).map(v->v.split(BasicInfoValuesSpilter)).map(Stream::of).orElse(Stream.empty()).forEach(base.genre::add);
        base.certificate = Optional.of("适合年龄").map(basicInfos::get).orElse(StringUtils.EMPTY);
        tvShow.title = base.title = title(basicInfos);
        tvShow.original_available = base.original_available = original_available(basicInfos);
        tvShow.summary = summary(detailHtml);
        
        loadPosterAndBackdrops(detailUrl, detailHtml, tvShow.extra().poster, tvShow.extra().backdrop, publishPrefix);
        
        base.extra().tvshow = tvShow;
        
        Map<Integer, TvShowEpisode> episodeMap = new TreeMap<>();
        Elements dts = detailHtml.select("div#dramaSeries ul#dramaSerialList dt");
        for(int i=0; i<dts.size(); i++){
            Element dt = dts.get(i);
            Element dd = dt.nextElementSibling();
            if(dd==null) continue;
            
            TvShowEpisode episode = SerializationUtils.clone(base);
            episode.episode = i+1;
            episode.tagline = dt.text().trim();
            
            Element picEle = dd.selectFirst("div.lemma-picture");
            if(picEle!=null){
                Element img = picEle.selectFirst("img");
                String imgUrl = extractImgUrl(img, publishPrefix);
                if(StringUtils.isNotBlank(imgUrl)) episode.extra().poster.add(0, imgUrl);
                picEle.remove();
            }
            
            episode.summary = dd.text().trim();
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
    
    private String title(Map<String, String> basicInfos) {
        String cnName = basicInfos.get("中文名");
        String alia = basicInfos.get("别 名");
        String originName = basicInfos.get("原版名称");
        String foreignName = basicInfos.get("外文名");
        
        String title = StringUtils.firstNonBlank(cnName, originName, alia);
        if(StringUtils.isNotBlank(foreignName)){
            if(StringUtils.isNotBlank(title)) title = title+"　"+foreignName;
            else title = foreignName;
        }
        
        return title;
    }
    private String original_available(Map<String, String> basicInfos) {
        Function<String, Date> parseDate = new Function<String, Date>() {
            @Override
            public Date apply(String str) {
                if(StringUtils.isBlank(str)) return null;
                str = str.replaceAll(" ", "").split("[-—－]")[0];
                try{
                    return DateUtils.parseDateStrictly(str
                        ,"yyyy年M月d日"
                        ,"yyyy年"
                    );
                }catch(Exception ig){}
                return null;
            }
        };
        
        Date makeDate = parseDate.apply(basicInfos.get("出品时间"));
        Date beOnDate = parseDate.apply(basicInfos.get("上映时间"));
        Date releaseDate = parseDate.apply(basicInfos.get("发行日期"));
        Date releaseTime = parseDate.apply(basicInfos.get("发行时间"));
        Date firstPlayTime = parseDate.apply(basicInfos.get("首播时间"));
        Date playBeginDate = parseDate.apply(basicInfos.get("播放期间"));
        
        Date original_available = ObjectUtils.firstNonNull(beOnDate, releaseTime, releaseDate, makeDate, firstPlayTime, playBeginDate);
        if(original_available==null) return StringUtils.EMPTY;
        return DateFormatUtils.format(original_available, "yyyy-MM-dd");
    }
    
    private Map<String, String> basicInfos(Elements basicNameEles) {
        Map<String, String> basicInfos = new HashMap<>();
        for(Element basicNameEle : basicNameEles){
            String name = basicNameEle.text().trim();
            if(StringUtils.isBlank(name)) continue;
            Element valEle = basicNameEle.nextElementSibling();
            if(valEle==null) continue;
            valEle.select("a.sup-anchor").remove();
            valEle.select("sup").remove();
            String value = valEle.text().trim();
            if(StringUtils.isBlank(value)) continue;
            
            basicInfos.put(name, value);
        }
        return basicInfos;
    }
    
    private String summary(Document doc) {
        Element plotAnchor = doc.selectFirst("div.anchor-list a[name=剧情简介].lemma-anchor");
        Element storyAnchor = doc.selectFirst("div.anchor-list a[name=故事简介].lemma-anchor");
        Element summaryAnchor = ObjectUtils.defaultIfNull(plotAnchor, storyAnchor);
        if(summaryAnchor!=null){
            Element paraEle = summaryAnchor.parent().nextElementSibling();
            if(paraEle!=null) {
                paraEle = paraEle.nextElementSibling();
                if(paraEle!=null){
                    List<String> paras = new LinkedList<>();
                    while(paraEle.is(".para")){
                        paraEle.select("div.lemma-picture").remove();
                        paraEle.select("a.lemma-album").remove();
                        paraEle.select("sup").remove();
                        paraEle.select("a.sup-anchor").remove();
                        String para = paraEle.text().trim();
                        if(StringUtils.isNotBlank(para)){
                            para = "　　"+para;
                            paras.add(para);
                        }
                        paraEle = paraEle.nextElementSibling();
                    }
                    return StringUtils.join(paras, "\n");
                }
            }
        }
        return StringUtils.EMPTY;
    }
    @SneakyThrows
    private void loadPosterAndBackdrops(String detailUrl, Document detailHtml, List<String> posters, List<String> backdrops, String publishPrefix) {
        String picUrl;
        String detailPath = new URL(detailUrl).getPath();
        if(PathMatcher.match(ItemPattern, detailPath)){
            Map<String, String> itemVars = PathMatcher.extractUriTemplateVariables(ItemPattern, detailPath);
            picUrl = config.getBaikeBaiduOrigin()+"/pic/"+itemVars.get("itemName")+"/"+itemVars.get("itemId")+"?fr=lemma";
        }else if(PathMatcher.match(ItemNameOnlyPattern, detailPath)){
            Map<String, String> itemVars = PathMatcher.extractUriTemplateVariables(ItemNameOnlyPattern, detailPath);
            String itemId = Optional.of(detailHtml).map(d->d.select("div[data-lemmaid]")).orElse(new Elements())
                .stream().map(d->d.attr("data-lemmaid")).filter(StringUtils::isNotBlank).findFirst().orElse(null);
            if(StringUtils.isBlank(itemId)) return;
            picUrl = config.getBaikeBaiduOrigin()+"/pic/"+itemVars.get("itemName")+"/"+itemId+"?fr=lemma";
        }else{
            return;
        }
        
        log.info("load picUrl {}", picUrl);
        String picCnt = loadPage(picUrl);
        if(log.isTraceEnabled()) log.trace("load picUrl cnt {}", picCnt);
        
        Document picDoc = Jsoup.parse(picCnt);
        picDoc.setBaseUri(picUrl);
        
        Elements items = picDoc.select("#album-list div.album-item");
        if(CollectionUtils.isEmpty(items)) return;
        
        Map<String, Element> itemMap = new HashMap<>();
        for(Element item : items){
            Element itemTitleEle = item.selectFirst("div.album-title");
            if(itemTitleEle==null) continue;
            itemMap.put(itemTitleEle.text().trim(), item);
        }
        
        Function<Elements, List<Triple<String, Double, Double>>> picAsInfoer = new Function<Elements, List<Triple<String,Double,Double>>>() {
            @Override
            public List<Triple<String, Double, Double>> apply(Elements picAs) {
                List<Triple<String, Double, Double>> coverAndSizes = new ArrayList<>();
                
                for(Element picA : picAs){
                    Element imgEle = picA.selectFirst("img");
                    if(imgEle==null) continue;
                    String imgUrl = extractImgUrl(imgEle, publishPrefix);
                    
                    Map<String, String> styles = Utils.styleVars(picA.attr("style"));
                    double w = Optional.ofNullable(styles.get("width")).map(v->v.replace("px", ""))
                        .filter(NumberUtils::isParsable).map(Double::valueOf).orElse(0D);
                    double h = Optional.ofNullable(styles.get("height")).map(v->v.replace("px", ""))
                        .filter(NumberUtils::isParsable).map(Double::valueOf).orElse(0D);
                    coverAndSizes.add(Triple.of(imgUrl, w, h));
                }
                return coverAndSizes;
            }
            
        };
        
        Elements allPicAs = picDoc.select("#album-list a.pic");
        List<Triple<String, Double, Double>> allPicInfos = picAsInfoer.apply(allPicAs);
        
        Elements posterPriorityItems = new Elements(Stream.of("概述图册","高清海报")
            .map(itemMap::get).filter(Objects::nonNull).collect(Collectors.toList()));
        List<Triple<String, Double, Double>> posterInfos = picAsInfoer.apply(posterPriorityItems.select("a.pic"));
        if(posterInfos.isEmpty()) posterInfos = allPicInfos;
        List<Triple<String, Double, Double>> portraits = posterInfos.stream().filter(t->t.getMiddle()<t.getRight()).collect(Collectors.toList());
        if(portraits.size()>0) posterInfos = portraits; //海报尝试只要纵向图
        
        Elements backdropPriorityItems = new Elements(Stream.of("概述图册","高清海报","高清剧照","剧照")
            .map(itemMap::get).filter(Objects::nonNull).collect(Collectors.toList()));
        List<Triple<String, Double, Double>> backdropInfos = picAsInfoer.apply(backdropPriorityItems.select("a.pic"));
        if(backdropInfos.isEmpty()) backdropInfos = allPicInfos;
        List<Triple<String, Double, Double>> landscapes = backdropInfos.stream().filter(t->t.getMiddle()>t.getRight()).collect(Collectors.toList());
        if(landscapes.size()>0) backdropInfos = landscapes; //背景图尝试只要横向图
        
        Function<List<Triple<String, Double, Double>>, List<String>> tryBest = new Function<List<Triple<String,Double,Double>>, List<String>>() {
            @Override
            public List<String> apply(List<Triple<String, Double, Double>> infos) {
                //按像素量倒排
                infos.sort((p1,p2)->-Double.compare(p1.getMiddle()*p1.getRight(), p2.getMiddle()*p2.getRight()));
                //取前5个
                infos = new ArrayList<>(infos.subList(0, Math.min(infos.size(), 5)));
                //顺序随机下
                Collections.shuffle(infos);
                return infos.stream().map(p->p.getLeft()).collect(Collectors.toList());
            }
        };
        
        posters.addAll(tryBest.apply(posterInfos));
        backdrops.addAll(tryBest.apply(backdropInfos));
    }
    
    private String extractImgUrl(Element imgEle, String publishPrefix) {
        if(imgEle==null) return null;
        Function<String, String> imgUrlTidy = new Function<String, String>() {
            @Override
            public String apply(String url) {
                if(StringUtils.isBlank(url)) return null;
                try{
                    URL u = new URL(url);
                    if(!PathMatcher.match(PicPattern, u.getPath())) return null;
                    String rawUrl = UriComponentsBuilder.fromHttpUrl(url).replaceQuery(null).toUriString();
                    return staticController.jpgWrap(publishPrefix, rawUrl);
                }catch(Exception ig){
                    return null;
                }
            }
        };
        String imgUrl = imgUrlTidy.apply(imgEle.absUrl("src"));
        if(StringUtils.isBlank(imgUrl)) imgUrl = imgUrlTidy.apply(imgEle.absUrl("data-src"));
        if(StringUtils.isBlank(imgUrl)) return null;
        return imgUrl;
    }
    
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
    
    private boolean isTvShow(Document detailHtml, Map<String, String> basicInfos) {
        if(detailHtml.selectFirst("#dramaSeries")!=null) return true;
        if(!basicInfos.containsKey("集 数") && !basicInfos.containsKey("剧集数量")) return false;
        return true;
    }
    
    @SneakyThrows
    private synchronized String loadPage(String url) {
        String cached = cacher.loadAsStr(url);
        if(cached!=null) return cached;
        
        return drivers.searchOpen(url, (driver, src)->{
            if(url.equals(driver.getCurrentUrl())) cacher.saveAsStr(url, src);
            return src;
        });
    }
}
