package com.lvt4j.spider4videostation;

import java.net.URL;
import java.util.Date;

import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsProperties.Web;

/**
 *
 * @author LV on 2022年7月9日
 */
public class MultiDriverTest {

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
        
        webDriver.get("https://javdb36.com/manifest.json");
        
        WebDriver javDriver = openBack("", webDriver);
        WebDriver doubanDriver = openBack("https://douban.com/robots.txt", webDriver);
        
//        String mainHandle = webDriver.getWindowHandle();
//        webDriver.executeScript("window.open('https://javdb36.com/manifest.json','_blank')");
//        System.out.println(webDriver.getCurrentUrl());
//        webDriver.close();
//        String javHandle = webDriver.getWindowHandle();
//        WebDriver javDriver = webDriver.switchTo().window(javHandle);
//        System.out.println(webDriver.getCurrentUrl());
//        webDriver.switchTo().window(mainHandle);
        
//        webDriver.executeScript("window.open('https://douban.com/robots.txt','_blank')");
//        String doubanHandle = webDriver.getWindowHandle();
//        webDriver.switchTo().window(mainHandle);
        
        Date cookieExpire = new Date(System.currentTimeMillis()+365*24*60*60*1000);
        javDriver.manage().addCookie(new Cookie("list_mode", "v",    null, "/", cookieExpire));
        javDriver.manage().addCookie(new Cookie("over18",    "1",    null, "/", cookieExpire));
        javDriver.manage().addCookie(new Cookie("locale",    "zh",   null, "/", cookieExpire));
        javDriver.manage().addCookie(new Cookie("theme",     "auto", null, "/", cookieExpire));
        
        javDriver.close();
        doubanDriver.close();
        
        webDriver.quit();
        
    }
    public static RemoteWebDriver openBack(String url, RemoteWebDriver mainDriver) {
        String mainHandle = mainDriver.getWindowHandle();
        mainDriver.executeScript("window.open('"+url+"','_blank')");
        for(String handle : mainDriver.getWindowHandles()){
            if(mainHandle.equals(handle)) continue;
            RemoteWebDriver subDriver = (RemoteWebDriver) mainDriver.switchTo().window(handle);
            if(!url.equals(subDriver.getCurrentUrl())) continue;
            return subDriver;
        }
        return null;
    }
}
