package com.lvt4j.spider4videostation;

import java.io.File;
import java.net.URL;
import java.util.Base64;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 *
 * @author LV on 2022年7月10日
 */
public class DownTest {

    public static void main(String[] args) throws Throwable {
        String EpisodePattern = "/subject/{subjectId}/episode/{epIdx}/";
        Map<String, String> vars = Consts.PathMatcher.extractUriTemplateVariables(EpisodePattern, "/subject/35524406/episode/1/");
        System.out.println(vars);
        
//        byte[] obs = FileUtils.readFileToByteArray(new File("C:\\Users\\chanceylee\\Desktop\\0eJb7.jpg"));
//        System.out.println(Base64.getEncoder().encodeToString(obs));
        
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
        
        webDriver.get("https://www.douban.com/robots.txt");
        webDriver.get("about:blank");
        
//        webDriver.executeScript(FileUtils.readFileToString(new File("func.js"), "utf8"));
        
        String tpl_script = FileUtils.readFileToString(new File("test.js"), "utf8");
        
        {
            String url = "https://img9.doubanio.com/view/photo/s_ratio_poster/public/p2875702766.webp";
            String rstFileName = "douban.jpg";
            String script = tpl_script.replace("@@Url@@", url);
            Object rst = webDriver.executeScript(script);
            System.out.println(rst);
            byte[] bs = Base64.getDecoder().decode(rst.toString());
            FileUtils.writeByteArrayToFile(new File(rstFileName), bs);
        }
        {
            String url = "https://c0.jdbstatic.com/thumbs/0e/0eJb7.jpg";
            String rstFileName = "javdb.jpg";
            String script = tpl_script.replace("@@Url@@", url);
            Object rst = webDriver.executeScript(script);
            System.out.println(rst);
            byte[] bs = Base64.getDecoder().decode(rst.toString());
            FileUtils.writeByteArrayToFile(new File(rstFileName), bs);
        }
        
        webDriver.quit();
    }
    
}
