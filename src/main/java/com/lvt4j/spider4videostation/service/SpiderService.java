package com.lvt4j.spider4videostation.service;

import com.lvt4j.spider4videostation.PluginType;
import com.lvt4j.spider4videostation.pojo.Args;
import com.lvt4j.spider4videostation.pojo.Rst;

/**
 *
 * @author LV on 2022年7月4日
 */
public interface SpiderService {

    public boolean support(PluginType plugin, Args args);
    
    /**
     * 实现时注意：
     * 加载完一个数据后 执行rst.result.add(xxx) 进行填充
     */
    public void search(String pluginId, String publishPrefix, PluginType pluginType,
        Args args, Rst rst);
    
}