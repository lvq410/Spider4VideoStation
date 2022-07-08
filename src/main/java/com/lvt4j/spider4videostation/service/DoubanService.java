package com.lvt4j.spider4videostation.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.lvt4j.spider4videostation.Config;
import com.lvt4j.spider4videostation.Consts;
import com.lvt4j.spider4videostation.Plugin;
import com.lvt4j.spider4videostation.Utils;
import com.lvt4j.spider4videostation.controller.DoubanController;
import com.lvt4j.spider4videostation.controller.StaticController;
import com.lvt4j.spider4videostation.pojo.Args;
import com.lvt4j.spider4videostation.pojo.Movie;
import com.lvt4j.spider4videostation.pojo.Rst;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author LV on 2022年7月6日
 */
@Slf4j
@Service
public class DoubanService implements SpiderService {

    private static File cookiesFile = new File("cookies/douban.com");
    
    @Autowired
    private Config config;
    
    @Autowired
    private StaticService staticService;
    @Autowired
    private FileCacher cacher;
    
    @Autowired@Lazy
    private StaticController staticController;
    @Autowired@Lazy
    private DoubanController doubanController;
    
    private RemoteWebDriver webDriver4Search;
    private long latestWebDriver4SearchTouchTime;
    
    private RemoteWebDriver webDriver4Static;
    private long latestWebDriver4StaticTouchTime;
    
    @PreDestroy
    private void destory() {
        destoryWebDriver4Search();
        destoryWebDriver4Static();
    }
    private void destoryWebDriver4Search() {
        if(webDriver4Search==null) return;
        try{
            webDriver4Search.quit();
        }catch(Exception e){
            log.warn("err on web quit {}", e);
        }finally {
            webDriver4Search = null;
        }
    }
    private void destoryWebDriver4Static() {
        if(webDriver4Static==null) return;
        try{
            webDriver4Static.quit();
        }catch(Exception e){
            log.warn("err on web quit {}", e);
        }finally {
            webDriver4Static = null;
        }
    }
    
    @Override
    public boolean support(Plugin plugin, Args args) {
        if(Plugin.Douban!=plugin) return false;
        if(!plugin.types.contains(args.type)) return false;
        if(!plugin.languages.contains(args.lang)) return false;
        return true;
    }

    @Override
    public synchronized void search(String publishPrefix, Plugin plugin, Args args, Rst rst) {
        switch(args.type){
        case "movie":
            searchMovie(publishPrefix, plugin, args, rst);
            break;
        default: break;
        }
    }
    @SneakyThrows
    private void searchMovie(String publishPrefix, Plugin plugin, Args args, Rst rst) {
        args.limit = Math.min(args.limit, config.getDoubanMaxLimit());
        
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
            if(!detailUrl.startsWith(config.getDoubanMovieItemPatterm())) continue;
            Element detailDiv = item.selectFirst("div.detail");
            if(detailDiv==null) continue;
            
            //提取标题
            String title = null;
            Element titleDiv = detailDiv.selectFirst("div.title");
            if(titleDiv!=null) {
                title = titleDiv.text().trim();
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
                movie = loadItem(publishPrefix, plugin, detailUrl);
            }catch(Exception e){
                log.error("error load detail {}", detailUrl, e);
                continue;
            }
            
            if(movie==null){ //未从详情页提取出信息
                if(StringUtils.isNotBlank(title) && StringUtils.isNotBlank(coverUrl)){ //但列表上有点数据
                    //用列表上的数据
                    movie = new Movie(plugin.id);
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
    private Movie loadItem(String publishPrefix, Plugin plugin, String detailUrl) {
        Movie movie = new Movie(plugin.id);
        
        log.info("load detail {}", detailUrl);
        String detailCnt = loadPage(detailUrl);
        if(log.isTraceEnabled()) log.trace("load detail cnt {}", detailCnt);
        Document detailHtml = Jsoup.parse(detailCnt);
        
        Element contentDiv = detailHtml.selectFirst("#content");
        if(contentDiv==null) return null;
        
        Element titleSpan = contentDiv.selectFirst("h1 span");
        if(titleSpan!=null){
            movie.title = titleSpan.text().trim();
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
                    loadBackdrops(publishPrefix, movie, mainpicUrl);
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
    private void loadBackdrops(String publishPrefix, Movie movie, String mainpicUrl) {
        log.info("load backdrop list {}", mainpicUrl);
        String mainpicCnt = loadPage(mainpicUrl);
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
            coverUrl = coverUrl.replace("view/photo/m/public", "view/photo/1/public");
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
        movie.extra().backdrop.addAll(coverAndSizes.stream().map(p->p.getLeft()).collect(Collectors.toList()));
    }
    
    @SneakyThrows
    private synchronized String loadPage(String url) {
        String cached = cacher.loadAsStr(url);
        if(cached!=null) return cached;
        
        Utils.retry(()->{
            webDriver4Search();
            log.trace("load page : {}", url);
            webDriver4Search.get(url);
        }, (e, n)->{
            log.error("err on web load : {}", url, e);
            destoryWebDriver4Search();
            if(n==1) return true;
            throw new RuntimeException("err on web load", e);
        });
        
        String cnt = webDriver4Search.getPageSource();
        if(!isLogined(cnt)){
            log.error("未登录，需先登录！");
            throw new RuntimeException("未登录");
        }
        
        if(url.equals(webDriver4Search.getCurrentUrl())) cacher.saveAsStr(url, cnt);
        
        return cnt;
    }
    
    @SneakyThrows
    private RemoteWebDriver webDriver4Search() {
        if(webDriver4Search!=null){
            latestWebDriver4SearchTouchTime = System.currentTimeMillis();
            return webDriver4Search;
        }
        
        ChromeOptions options = new ChromeOptions();
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        options.addArguments(config.getWebDriverArgs());
        
        log.info("init webdriver4search {}", config.getWebDriverAddr());
        webDriver4Search = new RemoteWebDriver(new URL(config.getWebDriverAddr()), options);
        
        webDriver4Search.manage().timeouts()
            .implicitlyWait(10, TimeUnit.SECONDS)
            .setScriptTimeout(config.getDoubanTimeoutMillis(), TimeUnit.MILLISECONDS)
            .pageLoadTimeout(config.getDoubanTimeoutMillis(), TimeUnit.MILLISECONDS);
        
        if(log.isTraceEnabled()) log.trace("open douban touchUrl : {}", config.getDoubanTouchUrl());
        webDriver4Search.get(config.getDoubanTouchUrl());
        
        loadCookie(webDriver4Search);
        
        return webDriver4Search;
    }
    private void loadCookie(RemoteWebDriver webDriver) {
        if(!cookiesFile.exists()) return;
        if(log.isTraceEnabled()) log.trace("cookiesFile exist, try init cookies");
        HashSet<Cookie> cookies;
        try{
            cookies = SerializationUtils.deserialize(FileUtils.readFileToByteArray(cookiesFile));
        }catch(Exception ig){
            log.warn("deserialize cookies file err", ig);
            FileUtils.deleteQuietly(cookiesFile);
            return;
        }
        for(Cookie cookie : cookies){
            try{
                webDriver.manage().addCookie(cookie);
                if(log.isTraceEnabled()) log.trace("init cookie : {}", cookie);
            }catch(Exception e){
                if(log.isTraceEnabled()) log.trace("init cookie ignore : {}", cookie, e);
            }
        }
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
        
        Utils.retry(()->{
            webDriver4Search();
            log.trace("redirect login check url : {}", config.getDoubanLoginCheckUrl());
            webDriver4Search.get(config.getDoubanLoginCheckUrl()); //先跳转检查登录状态的地址
        }, (e, n)->{
            log.error("err on web load {}", config.getDoubanLoginCheckUrl(), e);
            destoryWebDriver4Search();
            if(n==1) return true;
            throw new RuntimeException("err on web load", e);
        });
        
        boolean isLogined = isLogined(webDriver4Search);
        log.trace("isLogined : {}", isLogined);
        if(isLogined){
            state.logined = true;
            return state;
        }
        
        destoryWebDriver4Static();
        
        log.trace("redirect login url : {}", config.getDoubanLoginUrl());
        webDriver4Search.get(config.getDoubanLoginUrl());
        
        log.trace("try find login switch btn and click it");
        webDriver4Search.findElementByCssSelector(".quick.icon-switch").click();
        
        String qrLoginImgUrl = webDriver4Search.findElementByCssSelector("div.account-qr-scan img").getAttribute("src");
        log.trace("found qr login img : {}", qrLoginImgUrl);
        state.qrLoginImg = staticController.jpgWrap(publishPrefix, qrLoginImgUrl);
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
        LoginState state = new LoginState();
        
        if(webDriver4Search==null) throw new ResponseStatusException(BAD_REQUEST, "请先进行登录");
        
        Utils.waitUntil(()->{
            return isLogined(webDriver4Search);
        }, config.getDoubanLoginWaitTimeoutMillis());
        
        HashSet<Cookie> cookies = new HashSet<>(webDriver4Search.manage().getCookies());
        FileUtils.writeByteArrayToFile(cookiesFile, SerializationUtils.serialize(cookies));
        
        state.logined = true;
        return state;
    }
    
    @SneakyThrows
    @Deprecated
    public synchronized byte[] coverPre(String preUrl) {
        byte[] bs = cacher.load(preUrl);
        if(bs!=null) return bs;
        
        Utils.retry(()->{
            webDriver4Static();
            if(log.isTraceEnabled()) log.trace("open static preUrl：{}", preUrl);
            webDriver4Static.get(preUrl);
        }, (e, n)->{
            log.error("err on web load {}", preUrl, e);
            destoryWebDriver4Static();
            if(n==1) return true;
            throw new RuntimeException("err on web load", e);
        });
        
        if(!isLogined(webDriver4Static)){
            log.error("未登录，需先登录！");
            throw new RuntimeException("未登录");
        }
        
        if(log.isTraceEnabled()) log.trace("click 查看原图");
        webDriver4Static.findElementByCssSelector(".update.magnifier a").click();
        
        for(String win : webDriver4Static.getWindowHandles()){
            RemoteWebDriver winDriver = (RemoteWebDriver) webDriver4Static.switchTo().window(win);
            if(preUrl.equals(winDriver.getCurrentUrl())) continue;
            bs = staticService.downCurAsBs(winDriver);
            winDriver.close();
        }
        
        for(String win : webDriver4Static.getWindowHandles()){
            RemoteWebDriver winDriver = (RemoteWebDriver) webDriver4Static.switchTo().window(win);
            if(preUrl.equals(winDriver.getCurrentUrl())) break;
        }
        
        cacher.save(preUrl, bs);
        
        return bs;
    }
    
    @SneakyThrows
    private RemoteWebDriver webDriver4Static() {
        if(webDriver4Static!=null){
            latestWebDriver4StaticTouchTime = System.currentTimeMillis();
            return webDriver4Static;
        }
        
        ChromeOptions options = new ChromeOptions();
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        options.addArguments(config.getStaticWebDriverArgs());
        
        log.info("init webdriver4Static {}", config.getWebDriverAddr());
        webDriver4Static = new RemoteWebDriver(new URL(config.getWebDriverAddr()), options);
        
        webDriver4Static.manage().timeouts()
            .setScriptTimeout(config.getDoubanTimeoutMillis(), TimeUnit.MILLISECONDS)
            .pageLoadTimeout(config.getDoubanTimeoutMillis(), TimeUnit.MILLISECONDS);
        
        if(log.isTraceEnabled()) log.trace("open douban touchUrl : {}", config.getDoubanTouchUrl());
        webDriver4Static.get(config.getDoubanTouchUrl());
        
        loadCookie(webDriver4Static);
        
        return webDriver4Static;
    }
    private static boolean isLogined(RemoteWebDriver webDriver) {
        return isLogined(webDriver.getPageSource());
    }
    private static boolean isLogined(String pageSource) {
        return Jsoup.parse(pageSource)
                .selectFirst("div#db-global-nav div.top-nav-info li.nav-user-account")!=null;
    }

    @Scheduled(cron="0/30 * * * * ?")
    public synchronized void driverKeepAlive() {
        driver4SearchKeepAlive();
        driver4StaticKeepAlive();
    }
    private synchronized void driver4SearchKeepAlive() {
        if(webDriver4Search==null) return;
        if(System.currentTimeMillis()-latestWebDriver4SearchTouchTime<Consts.WebDriverHeartbeatGap) return;
        if(log.isTraceEnabled()) log.trace("webdriver4search heartbeat");
        webDriver4Search.executeScript("console.log('heartbeat')");
        latestWebDriver4SearchTouchTime = System.currentTimeMillis();
    }
    private synchronized void driver4StaticKeepAlive() {
        if(webDriver4Static==null) return;
        if(System.currentTimeMillis()-latestWebDriver4StaticTouchTime<Consts.WebDriverHeartbeatGap) return;
        if(log.isTraceEnabled()) log.trace("webdriver4static heartbeat");
        webDriver4Static.executeScript("console.log('heartbeat')");
        latestWebDriver4StaticTouchTime = System.currentTimeMillis();
    }
    
    public static class LoginState {
        public boolean logined;
        public String qrLoginImg;
    }
    
}