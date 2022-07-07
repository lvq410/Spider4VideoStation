package com.lvt4j.spider4videostation;

import static com.lvt4j.spider4videostation.Consts.PluginIdPrefix;
import static java.util.Arrays.asList;

import java.util.List;

import lombok.RequiredArgsConstructor;

/**
 *
 * @author LV on 2022年7月5日
 */
@RequiredArgsConstructor
public enum Plugin{
    
    AV_StrictId(PluginIdPrefix+".AV.StrictId", asList("movie"), asList("jpn"))
    ,AV_Normal(PluginIdPrefix+".AV.Normal", asList("movie"), asList("jpn"))
    
    ,Douban(PluginIdPrefix+".Douban", asList("movie"), asList("chs"))
    ;
    
    public final String id;
    public final List<String> types;
    public final List<String> languages;
    
    public static Plugin find(String id) {
        for(Plugin plugin : Plugin.values()){
            if(plugin.id.equals(id)) return plugin;
        }
        return null;
    }
}