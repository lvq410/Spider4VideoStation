package com.lvt4j.spider4videostation;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.File;

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

    private static Font cjkFont;

    public static Font getCJKFont() {
        return cjkFont;
    }

    public static void main(String[] args) {
        springContext = new SpringApplicationBuilder(Spider4VideoStationApp.class)
            .web(org.springframework.boot.WebApplicationType.NONE)
            .headless(false)
            .profiles("local")
            .run();

        // 加载 Serif CJK 字体，供需要显示韩文的控件使用
        initCJKFont();

        SwingUtilities.invokeLater(() -> {
            MainStage mainStage = springContext.getBean(MainStage.class);
            mainStage.show();
        });
    }

    public static <T> T getBean(Class<T> clazz) {
        return springContext.getBean(clazz);
    }

    private static void initCJKFont() {
//        File ttcFile = new File("fonts/NotoSerifCJK.ttc");
        File ttcFile = new File("C:/Windows/Fonts/NotoSerifCJK.ttc");
        if (!ttcFile.exists()) {
            System.out.println("[font] fonts/NotoSerifCJK.ttc 未找到");
            return;
        }
        try {
            cjkFont = Font.createFont(Font.TRUETYPE_FONT, ttcFile);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(cjkFont);
            System.out.println("[font] 加载: " + cjkFont.getFamily());
        } catch (Exception e) {
            System.err.println("[font] 加载失败: " + e.getMessage());
        }
    }
}
