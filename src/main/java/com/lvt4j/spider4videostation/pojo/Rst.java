package com.lvt4j.spider4videostation.pojo;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author LV on 2022年7月4日
 */
public class Rst {
    public boolean success;
    public List<Movie> result = Collections.synchronizedList(new LinkedList<>());
}
