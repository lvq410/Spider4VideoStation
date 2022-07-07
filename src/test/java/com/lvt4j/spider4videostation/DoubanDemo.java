package com.lvt4j.spider4videostation;

import java.io.File;
import java.net.URL;
import java.util.HashSet;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.Keys;
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
            ,"--blink-settings=imagesEnabled=false"
            ,"--no-sandbox"
            ,"--start-maximized"
            ,"--enable-automation");
        
        RemoteWebDriver webDriver = new RemoteWebDriver(new URL("http://127.0.0.1:4444"), options);
        
        HashSet<Cookie> cookies;
        File cookiesFile = new File("cookies");
        if(cookiesFile.exists()){
            System.out.println("打开touch页");
            webDriver.get("https://www.douban.com/robots.txt");
            
            System.out.println("填充cookie？"); System.in.read();
            cookies = SerializationUtils.deserialize(FileUtils.readFileToByteArray(cookiesFile));
            for(Cookie cookie : cookies){
                System.out.println("cookie："+cookie);
                try{
                    webDriver.manage().addCookie(cookie);
                }catch(Exception e){
                    System.err.println(e.getMessage());
                }
            }
            
            System.out.println("重新打开电影首页？"); System.in.read();
            webDriver.get("https://movie.douban.com");
        }else{
            System.out.println("打开登录页");
            webDriver.get("https://accounts.douban.com/passport/login");
            
            webDriver.findElementByCssSelector(".quick.icon-switch").click();
            String qrLoginImgUrl = webDriver.findElementByCssSelector("div.account-qr-scan img").getAttribute("src");
            System.out.println("二维码登录连接："+qrLoginImgUrl);
            
            System.out.println("确认登录成功？"); System.in.read();
            
            cookies = new HashSet<>(webDriver.manage().getCookies());
            FileUtils.writeByteArrayToFile(cookiesFile, SerializationUtils.serialize(cookies));
            
            System.out.println("打开电影首页？");
            webDriver.get("https://movie.douban.com");
        }
        
        System.out.println("登录状态:"+isLogined(webDriver));
        
        System.out.println("开始执行搜索？"); System.in.read();
        
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
        
        System.out.println("退出？"); System.in.read();
        webDriver.quit();
    }
    
    public static boolean isLogined(RemoteWebDriver webDriver) {
        Document doc = Jsoup.parse(webDriver.getPageSource());
        Element userAccNav = doc.selectFirst("div#db-global-nav div.top-nav-info li.nav-user-account");
        return userAccNav!=null;
    }
    
}
