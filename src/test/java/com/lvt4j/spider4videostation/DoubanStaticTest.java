package com.lvt4j.spider4videostation;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.fluent.Request;
import org.springframework.http.HttpHeaders;

import lombok.Cleanup;

/**
 *
 * @author LV on 2022年7月6日
 */
public class DoubanStaticTest {

    public static void main(String[] args) throws Exception {
        System.out.println(FilenameUtils.getName(new URL("https://img9.doubanio.com/view/photo/raw/public/p2759555526.jpg?a=b").getPath()));
        
        @Cleanup InputStream in = Request.Get("https://img9.doubanio.com/view/photo/raw/public/p2759555526.jpg")
            .setHeader(HttpHeaders.REFERER, "https://movie.douban.com/photos/photo/2759555526/")
            .execute().returnContent().asStream();
        @Cleanup FileOutputStream out = new FileOutputStream("1.jpg");
        IOUtils.copy(in, out);
    }
    
}
