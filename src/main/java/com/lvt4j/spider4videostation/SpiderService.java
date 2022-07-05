package com.lvt4j.spider4videostation;

/**
 *
 * @author LV on 2022年7月4日
 */
public interface SpiderService {

    public boolean support(Plugin plugin, Args args);
    
    /**
     * 实现时注意：
     * 加载完一个数据后 执行rst.result.add(xxx) 进行填充
     * @param request
     * @param args
     * @param rst
     */
    public void search(Plugin plugin,
        Args args, Rst rst);
    
}