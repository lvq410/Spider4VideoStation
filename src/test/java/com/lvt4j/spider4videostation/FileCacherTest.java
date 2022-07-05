package com.lvt4j.spider4videostation;

import static com.lvt4j.spider4videostation.FileCacher.cacheFile;

/**
 *
 * @author LV on 2022年7月2日
 */
public class FileCacherTest {

    public static void main(String[] args) {
        System.out.println(cacheFile("https://javdb003.com/search?f=all&lm=v&q=star-508"));
        System.out.println(cacheFile("https://javdb003.com/a/b/e"));
        System.out.println(cacheFile("https://javdb003.com/a/b/e/f"));
        System.out.println(cacheFile("https://javdb003.com/a/b/e/f?cc"));
        System.out.println(cacheFile("https://javdb003.com/a/b/c?f=all&lm=v&q=star-508"));
        System.out.println(cacheFile("https://javdb003.com/a/b/g.jpg?f=all&lm=v&q=star-508"));
    }
    
}
