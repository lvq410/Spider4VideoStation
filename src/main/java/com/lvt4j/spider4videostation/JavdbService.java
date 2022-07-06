package com.lvt4j.spider4videostation;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author LV on 2022年6月30日
 */
@Slf4j
@Service
public class JavdbService implements SpiderService {

    private static final List<Plugin> SupportPluginIds = Arrays.asList(Plugin.AV_Normal, Plugin.AV_StrictId);
    
    @Autowired
    private Config config;
    
    @Autowired
    private FileCacher cacher;
    
    private RemoteWebDriver webDriver;
    private long latestWebDriverTouchTime;
    
    @PreDestroy
    private void destory() {
        if(webDriver==null) return;
        try{
            webDriver.quit();
        }catch(Exception ig){
            log.warn("err on web quit {}", ig);
        }finally {
            webDriver = null;
        }
    }
    
    @Override
    public boolean support(Plugin plugin, Args args) {
        if(!SupportPluginIds.contains(plugin)) return false;
        if(!plugin.types.contains(args.type)) return false;
        if(!plugin.languages.contains(args.lang)) return false;
        return true;
    }
    
    @Override
    public void search(Plugin plugin, Args args, Rst rst) {
        boolean strictAvId = Plugin.AV_StrictId==plugin;
        
        String title = args.input.title;
        if(strictAvId) title = Utils.extractAvId(title);
        if(StringUtils.isBlank(title)) return;
        
        String listUrl,listCnt;
        try{
            listUrl = config.getJavdbOrigin()+"/search?f=all&lm=v&q="+URLEncoder.encode(title, "utf8");
            log.info("load list {}", listUrl);
        }catch(Exception ig){ return;}
        
        try{
            listCnt = loadPage(listUrl.toString());
            if(log.isTraceEnabled()) log.trace("load list cnt {}", listCnt);
        }catch(Exception e){
            log.error("error load list {}", listUrl, e);
            return;
        }
        
        Document listHtml = Jsoup.parse(listCnt);
        listHtml.setBaseUri(listUrl.toString());
        Elements items = listHtml.select(".container .movie-list .item");
        
        int rstNum = 0;
        
        for(Element item : items){
            Element box = item.selectFirst("a.box");
            if(box==null) continue;
            
            //提取标题备用
            String videoTitle = null;
            Element videoTitleDiv = box.selectFirst(".video-title");
            if(videoTitleDiv!=null) videoTitle = videoTitleDiv.text().trim();
            
            if(StringUtils.isNotBlank(videoTitle) && strictAvId){ //严格番号模式下，标题不包含待搜番号直接跳过
                if(!videoTitle.toLowerCase().contains(title.toLowerCase())) continue;
            }
            
            String detailUrl = box.absUrl("href");
            if(StringUtils.isBlank(detailUrl)) continue;
            
            Movie movie = null;
            try{
                movie = loadItem(plugin, detailUrl);
            }catch(Exception e){
                log.error("error load detail {}", detailUrl, e);
                continue;
            }
            
            //提取竖版图片备用
            String coverUrl = null;
            Element coverImg = box.selectFirst(".cover img");
            if(coverImg!=null){
                coverUrl = coverImg.absUrl("src");
                if(StringUtils.isNotBlank(coverUrl)){
                    coverUrl = config.staticWrap(coverUrl);
                }
            }
            
            if(movie==null){ //未从详情页提取出信息
                if(StringUtils.isNotBlank(videoTitle) && StringUtils.isNotBlank(coverUrl)){ //但列表上有点数据
                    //用列表上的数据
                    movie = new Movie(plugin.id);
                    if(StringUtils.isNotBlank(videoTitle)) movie.title = videoTitle;
                    if(StringUtils.isNotBlank(coverUrl)) {
                        movie.extra().poster.add(0, coverUrl);
                        movie.extra().backdrop.add(0, coverUrl);
                    }
                }else{
                    continue;
                }
            }else{
                if(StringUtils.isNotBlank(coverUrl)){ //用竖版封面
                    movie.extra().poster.add(0, coverUrl);
                }
            }
            
            rst.result.add(movie);
            if(args.reachLimit(++rstNum)) break;
        }
    }
    
    private Movie loadItem(Plugin plugin, String detailUrl) throws Exception {
        Movie movie = new Movie(plugin.id);
        
        log.info("load detail {}", detailUrl);
        String detailCnt = loadPage(detailUrl);
        if(log.isTraceEnabled()) log.trace("load detail cnt {}", detailCnt);
        Document detailHtml = Jsoup.parse(detailCnt);
        
        Element detailDiv = detailHtml.selectFirst(".video-detail");
        if(detailDiv==null) return null;
        
        Element titleDiv = detailDiv.selectFirst("h2.title");
        if(titleDiv!=null){
            movie.title = titleDiv.text().trim();
        }
        
        Element coverImg = detailDiv.selectFirst(".column-video-cover img.video-cover");
        String coverUrl = null;
        if(coverImg!=null) {
            coverUrl = coverImg.absUrl("src");
            if(StringUtils.isNotBlank(coverUrl)){
                coverUrl = config.staticWrap(coverUrl);
                movie.extra().poster.add(coverUrl);
            }
        }
        
        String avId = EMPTY;
        
        Elements panels = detailDiv.select(".movie-panel-info .panel-block");
        for(Element panel : panels){
            Element nameStrong = panel.selectFirst("strong");
            if(nameStrong==null) continue;
            
            Element valueDiv = panel.selectFirst("span.value");
            if(valueDiv==null) continue;
            
            String name = nameStrong.text().trim();
            
            switch(name){
            case "番號:":
                avId = valueDiv.text().trim();
                break;
            case "日期:":
                movie.original_available = valueDiv.text().trim();
                break;
            case "類別:":
                Elements tagAs = valueDiv.select("a");
                for(Element tagA : tagAs){
                    String tag = tagA.text().trim();
                    if(StringUtils.isBlank(tag)) continue;
                    movie.genre.add(tag);
                }
                break;
            case "片商:":
            case "發行:":
                Elements makerAs = valueDiv.select("a");
                for(Element makerA : makerAs){
                    String maker = makerA.text().trim();
                    if(StringUtils.isBlank(maker)) continue;
                    movie.writer.add(maker);
                }
                break;
            case "導演:":
                Elements directorAs = valueDiv.select("a");
                for(Element directorA : directorAs){
                    String director = directorA.text().trim();
                    if(StringUtils.isBlank(director)) continue;
                    movie.director.add(director);
                }
                break;
            case "評分:":
                String rating = valueDiv.text().trim();
                rating = StringUtils.substringBefore(rating, "分");
                if(NumberUtils.isParsable(rating)){
                    BigDecimal rate = new BigDecimal(rating)
                        .multiply(BigDecimal.valueOf(2))
                        .setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
                    movie.extra().rating(rate);
                }
                break;
            case "演員:":
                Elements actorAs = valueDiv.select("a");
                for(Element actorA : actorAs){
                    String actor = actorA.text().trim();
                    if(StringUtils.isBlank(actor)) continue;
                    movie.actor.add(actor);
                }
                break;
            default: break;
            }
        }
        Elements previewAs = detailDiv.select(".preview-images a.tile-item");
        for(Element previewA : previewAs){
            String previewUrl = previewA.absUrl("href");
            if(StringUtils.isBlank(previewUrl)) continue;
            previewUrl = config.staticWrap(previewUrl);
            movie.extra().backdrop.add(previewUrl);
        }
        if(previewAs.isEmpty()){ //无预览图时，用回大封面做背景图
            if(StringUtils.isNotBlank(coverUrl)){
                movie.extra().backdrop.add(coverUrl);
            }
        }
        Collections.shuffle(movie.extra().backdrop);
        
        movie.summary = movie.title;
        movie.summary = movie.summary.replaceAll(avId, EMPTY);
        for(String actor : movie.actor){
            try{
                movie.summary = movie.summary.replaceAll(actor, EMPTY);
            }catch(Exception ig){}
        }
        movie.summary = movie.summary.trim();
        
        movie.certificate = "18R";
        
        return movie;
    }
    
    @SneakyThrows
    private synchronized String loadPage(String url) {
        String cached = cacher.loadAsStr(url);
        if(cached!=null) return cached;
        
        Utils.retry(()->{
            driver().get(url);
        }, (e, n)->{
            log.error("err on web load {}", url, e);
            destory();
            if(n==1) return true;
            throw new RuntimeException("err on web load", e);
        });
        
        try{
            WebElement over18A = webDriver.findElement(By.cssSelector("div.over18-modal a.is-success"));
            if(over18A.isDisplayed()) {
                if(log.isTraceEnabled()) log.trace("over18 is displaying , try click it");
                over18A.click();
            }
        }catch(NoSuchElementException ig){}
        
        String cnt = webDriver.getPageSource();
        
        if(url.equals(webDriver.getCurrentUrl())) cacher.saveAsStr(url, cnt);
        
        return cnt;
    }
    
    @SneakyThrows
    private WebDriver driver() {
        if(webDriver!=null) {
            latestWebDriverTouchTime = System.currentTimeMillis();
            return webDriver;
        }
        
        ChromeOptions options = new ChromeOptions();
        options.addArguments(config.getWebDriverArgs());
        
        log.info("init webdriver {}", config.getWebDriverAddr());
        webDriver = new RemoteWebDriver(new URL(config.getWebDriverAddr()), options);
        
        webDriver.manage().timeouts()
            .pageLoadTimeout(config.getJavdbTimeoutMillis(), TimeUnit.MILLISECONDS)
            .setScriptTimeout(config.getJavdbTimeoutMillis(), TimeUnit.MILLISECONDS);
        
        webDriver.get(config.getJavdbTouchUrl());
        
        Date cookieExpire = new Date(System.currentTimeMillis()+365*24*60*60*1000);
        webDriver.manage().addCookie(new Cookie("list_mode", "v",    null, "/", cookieExpire));
        webDriver.manage().addCookie(new Cookie("over18",    "1",    null, "/", cookieExpire));
        webDriver.manage().addCookie(new Cookie("locale",    "zh",   null, "/", cookieExpire));
        webDriver.manage().addCookie(new Cookie("theme",     "auto", null, "/", cookieExpire));
        
        return webDriver;
    }
    
    @Scheduled(cron="0/5 * * * * ?")
    public synchronized void driverKeepAlive() {
        if(webDriver==null) return;
        if(System.currentTimeMillis()-latestWebDriverTouchTime<Consts.WebDriverHeartbeatGap) return;
        if(log.isTraceEnabled()) log.trace("webdriver heartbeat");
        webDriver.executeScript("console.log('heartbeat')");
        latestWebDriverTouchTime = System.currentTimeMillis();
    }
    
}