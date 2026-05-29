package com.lvt4j.spider4videostation.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.lvt4j.spider4videostation.ui.MainStage;
import lombok.extern.slf4j.Slf4j;

/**
 * 配置读写服务
 * 设置变更持久化到 config/application-local.yml，启动时通过 local profile 自动加载
 *
 * @author LV on 2023年2月7日
 */
@Slf4j
@Service
public class ConfigService {

    private static final File LocalConfigFile = new File("config", "application-local.yml");

    @Autowired
    private Environment env;

    @Autowired
    private ContextRefresher contextRefresher;

    public Map<String, String> gets(List<String> keys) {
        return keys.stream().collect(Collectors.toMap(k -> k, k -> env.getProperty(k),
            (a, b) -> b, LinkedHashMap::new));
    }

    public synchronized void set(String key, String val) {
        System.setProperty(key, val);
        contextRefresher.refresh();
        persist();
    }

    private void persist() {
        try {
            LocalConfigFile.getParentFile().mkdirs();
            StringBuilder sb = new StringBuilder("# Spider4VideoStation local settings\n");
            for (String key : MainStage.SETTING_KEYS) {
                String val = System.getProperty(key);
                if (val == null) val = env.getProperty(key);
                if (val != null) sb.append(key).append(": ").append(val).append('\n');
            }
            // 最近抓取目标
            String recentTargets = System.getProperty("recentTargets");
            if (recentTargets == null) recentTargets = env.getProperty("recentTargets");
            if (recentTargets != null) sb.append("recentTargets: '").append(recentTargets.replace("'", "''")).append("'\n");
            try (OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(LocalConfigFile), StandardCharsets.UTF_8)) {
                w.write(sb.toString());
            }
            log.debug("settings persisted to {}", LocalConfigFile.getAbsolutePath());
        } catch (Exception e) {
            log.warn("persist settings fail: {}", e.getMessage());
        }
    }
}