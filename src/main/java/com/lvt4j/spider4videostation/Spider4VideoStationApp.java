package com.lvt4j.spider4videostation;

import javax.swing.SwingUtilities;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.lvt4j.spider4videostation.ui.MainStage;

/**
 *
 * @author LV on 2022年3月11日
 */
@EnableScheduling
@SpringBootApplication
public class Spider4VideoStationApp {

    private static ConfigurableApplicationContext springContext;

    public static void main(String[] args) {
        springContext = new SpringApplicationBuilder(Spider4VideoStationApp.class)
            .web(org.springframework.boot.WebApplicationType.NONE)
            .headless(false)
            .profiles("local")
            .run();

        SwingUtilities.invokeLater(() -> {
            MainStage mainStage = springContext.getBean(MainStage.class);
            mainStage.show();
        });
    }

    public static <T> T getBean(Class<T> clazz) {
        return springContext.getBean(clazz);
    }
}