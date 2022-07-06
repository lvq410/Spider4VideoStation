package com.lvt4j.spider4videostation;

import java.net.URL;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 *
 * @author LV on 2022年7月5日
 */
public class DoubanDemo {

    public static void main(String[] args) throws Throwable {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-gpu"
            ,"--disable-extensions"
            ,"--disable-browser-side-navigation"
            ,"--disable-dev-shm-usage"
//            ,"--blink-settings=imagesEnabled=false"
            ,"--no-sandbox"
            ,"--start-maximized"
            ,"--enable-automation");
        
        RemoteWebDriver webDriver = new RemoteWebDriver(new URL("http://127.0.0.1:4444"), options);
        
        System.out.println("打开登录页");
        webDriver.get("https://accounts.douban.com/passport/login");
        
        System.out.println("确认登录成功？");
        System.in.read();
        
        webDriver.manage().getCookies();
        
        System.out.println("开始打开电影首页");
        webDriver.get("https://movie.douban.com");
        
        WebElement loginNavInfo = webDriver.findElement(By.cssSelector("div#db-global-nav div.top-nav-info"));
        boolean logined = false;
        if("登录/注册".equals(loginNavInfo.getText())){
            logined = false;
        }else{
            if(loginNavInfo.findElements(By.cssSelector("li.nav-user-account")).isEmpty()){
                logined=false;
            }else{
                logined=true;
            }
        }
        System.out.println("登录状态:"+logined);
        
        System.out.println("开始执行搜索？");
        System.in.read();
        
        webDriver.findElement(By.id("inp-query")).sendKeys("蜘蛛侠"+Keys.ENTER);
        System.in.read();
        
        Document doc = Jsoup.parse(webDriver.getPageSource());
        doc.setBaseUri("https://movie.douban.com");
        Elements items = doc.select("div.item-root");
        for(Element item : items){
            Element detailA = item.selectFirst("a");
            if(detailA==null) continue;
            Element coverImg = detailA.selectFirst("img");
            String coverUrl = null;
            if(coverImg!=null) coverUrl = coverImg.absUrl("src");
            
            Element detailDiv = item.selectFirst("div.detail");
            if(detailDiv==null) continue;
            Element titleDiv = detailDiv.selectFirst("div.title");
            String title = null;
            if(titleDiv!=null) title = titleDiv.text();
            
            System.out.println(title +" "+ coverUrl);
        }
        
        System.out.println("退出？");
        webDriver.quit();
    }
    
}
