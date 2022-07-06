package com.lvt4j.spider4videostation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 *
 * @author LV on 2022年3月11日
 */
@EnableScheduling
@SpringBootApplication
public class Spider4VideoStationApp implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
            .addResourceLocations("classpath:/static/");
    }
    
    public static void main(String[] args) throws Throwable {
        SpringApplication app = new SpringApplication(Spider4VideoStationApp.class);
        app.addListeners(new ApplicationPidFileWriter());
        app.run(args);
    }
    
}