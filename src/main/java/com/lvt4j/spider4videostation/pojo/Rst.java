package com.lvt4j.spider4videostation.pojo;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 *
 * @author LV on 2022年7月4日
 */
public class Rst {
    public boolean success;
    public List<Object> result = Collections.synchronizedList(new LinkedList<>());
    /** 搜索过程中遇到的用户可感知的错误（如未登录、WebDriver不可达等），用 Set 自动去重 */
    public Set<String> errors = Collections.synchronizedSet(new LinkedHashSet<>());

    /**
     * 检查异常链，自动识别已知错误并添加用户友好提示
     * @return true 如果识别到了已知错误
     */
    public boolean collectError(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg == null) continue;
            if (msg.contains("未登录")) {
                errors.add("豆瓣未登录，请先在设置中点击「豆瓣登录」");
                return true;
            }
            if (msg.contains("open url err") || msg.contains("exe script err")) {
                errors.add("WebDriver 连接失败，请检查「WebDriver地址」设置");
                return true;
            }
        }
        return false;
    }
}
