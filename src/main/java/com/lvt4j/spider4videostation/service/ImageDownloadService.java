package com.lvt4j.spider4videostation.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * 图片下载服务 —— 替代原 StaticController 的图片代理功能
 * 图片直接存入 FileCacher 的 cache/ 目录，统一管理生命周期
 *
 * @author LV on 2022年7月10日
 */
@Slf4j
@Service
public class ImageDownloadService {

    private static final int MaxImgSize = 4 * 1024 * 1024;

    @Autowired
    private FileCacher cacher;

    @Autowired
    private StaticService staticService;

    @SneakyThrows
    public String download(String imageUrl, String serviceName) {
        byte[] data = cacher.load(imageUrl);
        if (data == null) {
            data = staticService.down(imageUrl, serviceName);
            if (data == null) return null;
            cacher.save(imageUrl, data);
        }

        if (data.length >= MaxImgSize) data = shrink(data, imageUrl);

        return FileCacher.cacheFile(imageUrl).getAbsolutePath();
    }

    private byte[] shrink(byte[] data, String url) {
        while (data.length >= MaxImgSize) {
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
                double rate = Math.sqrt((double) MaxImgSize / data.length);

                int w = (int) (image.getWidth() * rate);
                int h = (int) (image.getHeight() * rate);
                BufferedImage dest = new BufferedImage(w, h, 1);
                dest.getGraphics().drawImage(image, 0, 0, w, h, null);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(dest, "jpg", baos);
                data = baos.toByteArray();
            } catch (Exception e) {
                log.warn("shrink img fail : {}", url, e);
                return data;
            }
        }
        return data;
    }
}