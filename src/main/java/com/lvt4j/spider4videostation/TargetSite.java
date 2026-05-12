package com.lvt4j.spider4videostation;

import static java.util.Arrays.asList;

import java.util.List;

import lombok.RequiredArgsConstructor;

/**
 *
 * @author LV on 2022年7月5日
 */
@RequiredArgsConstructor
public enum TargetSite{

    AV_StrictId("AV.StrictId", asList("movie"), asList("movie"), asList("jpn"))
    ,AV_Normal("AV.Normal", asList("movie"), asList("movie"), asList("jpn"))

    ,Douban("Douban", asList("movie","tvshow", "tvshow_episode"), asList("movie","tvshow"), asList("chs"))

    ,BaikeBaidu("BaikeBaidu", asList("movie","tvshow", "tvshow_episode"), asList("movie","tvshow"), asList("chs"))

    ;

    public final String name;
    public final List<String> searchTypes;
    public final List<String> infoTypes;
    public final List<String> languages;

    public static TargetSite find(String type) {
        for(TargetSite target : TargetSite.values()){
            if(target.name.equals(type)) return target;
        }
        return null;
    }
}