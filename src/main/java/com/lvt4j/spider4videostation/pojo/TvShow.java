package com.lvt4j.spider4videostation.pojo;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

/**
 *
 * @author LV on 2023年1月12日
 */
public class TvShow implements Serializable {
    private static final long serialVersionUID = -9163674403976993941L;

    public static TvShow testUse(String pluginId) {
        TvShow testUse = new TvShow(pluginId);
        testUse.title = testUse.original_available = testUse.summary = "this is 4 test use";
        return testUse;
    }
    
    private final String pluginId;
    
    public String title = EMPTY;
    public String original_available = EMPTY;
    public String summary = EMPTY;
    
    public Map<String, Extra> extra;
    
    public TvShow(String pluginId) {
        this.pluginId = pluginId;
        extra = ImmutableMap.of(pluginId, new Extra());
    }
    
    public Extra extra() {
        return extra.get(pluginId);
    }
    
    public class Extra implements Serializable {
        private static final long serialVersionUID = -7379332400058258473L;
        
        public List<String> backdrop = new ArrayList<>();
        public List<String> poster = new ArrayList<>();
    }
    
}