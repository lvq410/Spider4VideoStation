package com.lvt4j.spider4videostation;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

/**
 *
 * @author LV on 2022年7月4日
 */
public class Movie {

    public static Movie testUse(String pluginId) {
        Movie testUse = new Movie(pluginId);
        testUse.title = testUse.tagline = testUse.original_available = testUse.summary = "this is 4 test use";
        testUse.genre.add("fake genre");
        testUse.certificate = "PG";
        testUse.actor.add("fake actor");
        testUse.writer.add("fake writer");
        testUse.director.add("fake director");
        return testUse;
    }
    
    private final String pluginId;
    
    public String title = EMPTY;
    public String tagline = EMPTY;
    public String original_available = EMPTY;
    public String summary = EMPTY;
    public List<String> genre = new ArrayList<>();
    public String certificate = EMPTY;
    public List<String> actor = new ArrayList<>();
    public List<String> writer = new ArrayList<>();
    public List<String> director = new ArrayList<>();
    public Map<String, Extra> extra;
    
    public Movie(String pluginId) {
        this.pluginId = pluginId;
        extra = ImmutableMap.of(pluginId, new Extra());
    }
    
    public Extra extra() {
        return extra.get(pluginId);
    }
    
    class Extra {
        public List<String> backdrop = new ArrayList<>();
        public List<String> poster = new ArrayList<>();
        public Map<String, BigDecimal> rating = new HashMap<>();
        
        public void rating(BigDecimal rating) {
            this.rating.put(pluginId, rating);
        }
    }
    
}