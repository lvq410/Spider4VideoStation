package com.lvt4j.spider4videostation.service;

import static com.lvt4j.spider4videostation.service.FileCacher.cacheFile;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 *
 * @author LV on 2022年7月2日
 */
public class FileCacherTest {

    @Test
    public void cacheFileTest() {
        assertEquals("cache\\movie.douban.com\\subject\\26933210\\.cache", cacheFile("https://movie.douban.com/subject/26933210/").getPath());
        assertEquals("cache\\javdb003.com\\search？f=all&lm=v&q=star-508.cache", cacheFile("https://javdb003.com/search?f=all&lm=v&q=star-508").getPath());
        assertEquals("cache\\javdb003.com\\a\\b\\e.cache", cacheFile("https://javdb003.com/a/b/e").getPath());
        assertEquals("cache\\javdb003.com\\a\\b\\e\\f.cache", cacheFile("https://javdb003.com/a/b/e/f").getPath());
        assertEquals("cache\\javdb003.com\\a\\b\\e\\f？cc.cache", cacheFile("https://javdb003.com/a/b/e/f?cc").getPath());
        assertEquals("cache\\javdb003.com\\a\\b\\c？f=all&lm=v&q=star-508.cache", cacheFile("https://javdb003.com/a/b/c?f=all&lm=v&q=star-508").getPath());
        assertEquals("cache\\javdb003.com\\a\\b\\g.jpg？f=all&lm=v&q=star-508.cache", cacheFile("https://javdb003.com/a/b/g.jpg?f=all&lm=v&q=star-508").getPath());
    }
    
}