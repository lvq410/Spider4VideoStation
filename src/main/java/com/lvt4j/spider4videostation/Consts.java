package com.lvt4j.spider4videostation;

import java.util.concurrent.TimeUnit;

/**
 *
 * @author LV on 2022年7月4日
 */
public class Consts {

    public static final String AvIdPattern = "([a-zA-Z0-9]{2,}(?:-[a-zA-Z0-9]{2,})+)";
    
    public static final String PluginTestUseTitle = "special_test_title_4_com.lvt4j.spider4videostation";

    public static final String PluginIdPrefix = "com.lvt4j.Spider4VideoStation";
    
//    public static final long WebDriverHeartbeatGap = TimeUnit.MINUTES.toMillis(5L);
    public static final long WebDriverHeartbeatGap = TimeUnit.SECONDS.toMillis(10L);
    
}