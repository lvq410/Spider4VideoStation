package com.lvt4j.spider4videostation;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author LV on 2022年7月4日
 */
public class Consts {

    public static final String AvIdPattern = "([a-zA-Z0-9]{2,}(?:-[a-zA-Z0-9]{2,})+)";
    
    public static final String PluginTestUseTitle = "special_test_title_4_com.lvt4j.spider4videostation";

    public static final String PluginIdPrefix = "com.lvt4j.Spider4VideoStation";

    public static final File Folder_Cache = new File("cache");
    public static final File Folder_Cookies = new File("cookies");
    
    static{
        Folder_Cache.mkdirs();
        Folder_Cookies.mkdirs();
    }
    
    public static final long WebDriverHeartbeatGap = TimeUnit.MINUTES.toMillis(1L);
//    public static final long WebDriverHeartbeatGap = TimeUnit.SECONDS.toMillis(10L);
    
}