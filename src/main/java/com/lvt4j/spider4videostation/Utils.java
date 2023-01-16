package com.lvt4j.spider4videostation;

import static com.lvt4j.spider4videostation.Consts.AvIdPattern;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.DatatypeConverter;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author LV on 2022年6月30日
 */
@Slf4j
public class Utils {

    public static ObjectMapper ObjectMapper = new ObjectMapper();
    static {
        ObjectMapper.setSerializationInclusion(Include.NON_NULL);
        ObjectMapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        ObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ObjectMapper.configure(Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        ObjectMapper.configure(Feature.ALLOW_SINGLE_QUOTES, true);
        ObjectMapper.configure(Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        ObjectMapper.configure(Feature.ALLOW_COMMENTS, true);
        ObjectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        ObjectMapper.registerModule(new JavaTimeModule());
    }
    
    public static String extractAvId(String title) {
        title = title.replaceAll(" ", "-");
        Matcher m = Pattern.compile(AvIdPattern).matcher(title);
        if(!m.find()) return null;
        String avId = m.group();
        
        String useLessSuffix2 = "ch|CH|cn|CN|hd|HD";
        String useLessSuffix3 = "fhd|FHD";
        if(avId.matches("^.*[-]{1}("+useLessSuffix2+")$")) avId = avId.substring(0, avId.length()-3);
        if(avId.matches("^.*[0-9]{1}("+useLessSuffix2+")$")) avId = avId.substring(0, avId.length()-2);
        if(avId.matches("^.*[-]{1}("+useLessSuffix3+")$")) avId = avId.substring(0, avId.length()-4);
        if(avId.matches("^.*[0-9]{1}("+useLessSuffix3+")$")) avId = avId.substring(0, avId.length()-3);
        return avId;
    }
    
    /**
     * 代码重试机制，运行retryRunnable(至少一次)，若出现异常在满足retryPredicate条件下重试
     * @param retryRunnable
     * @param retryPredicate
     * @throws 不满足retryPredicate条件时的最后一次异常(或retryPredicate本身抛出的异常)
     */
    public static void retry(ThrowableRunnable retryRunnable, RetryPredicate retryPredicate) throws Throwable {
        int executionCount = 0;
        Throwable e;
        do{
            try{
                retryRunnable.run();
                return;
            }catch(Throwable e2){
                e = e2;
            }
            executionCount += 1;
        }while(retryPredicate.retry(e, executionCount));
        throw e;
    }
    public interface RetryPredicate {
        /**
         * 是否可继续重试
         * @param e 已遇到的异常
         * @param executionCount 已遇到的异常总数
         * @return
         * @throws Exception
         */
        boolean retry(Throwable e, int executionCount) throws Throwable;
    }

    public interface ThrowableRunnable {
        void run() throws Throwable;
    }
    public interface ThrowableConsumer<T> {
        void accept(T t) throws Throwable;
    }
    public interface ThrowableFunction<T, R> {
        R apply(T t) throws Throwable;
    }
    public interface ThrowableBiConsumer<T, U> {
        void accept(T t, U u) throws Throwable;
    }
    public interface ThrowableBiFunction<T, U, R> {
        R apply(T t, U u) throws Throwable;
    }
    
    @SneakyThrows
    public static String res(String classpath) {
        return IOUtils.toString(Utils.class.getResourceAsStream(classpath), Charset.defaultCharset());
    }

    public static boolean waitUntil(Supplier<Boolean> until, long timeout) throws InterruptedException,TimeoutException {
        long beginTime = System.currentTimeMillis(), waitedTime = 0;
        while(!until.get()){
            if(timeout>0 && waitedTime>=timeout) throw new TimeoutException(String.format("wait timeout %s > %s", waitedTime, timeout));
            Thread.sleep(10);
            waitedTime = System.currentTimeMillis()-beginTime;
        }
        return until.get();
    }
    
    public static void waitFileNoChange(File file, long timeout) throws InterruptedException, TimeoutException {
        long beginTime = System.currentTimeMillis(), waitedTime = 0;
        long lastPeekModify, lastPeekLength;
        do{
            if(timeout>0 && waitedTime>=timeout) throw new TimeoutException(String.format("wait timeout %s > %s", waitedTime, timeout));
            lastPeekModify = file.lastModified();
            lastPeekLength = file.length();
            if(log.isTraceEnabled()) log.trace("waiting file stop change {} lastModified：{} length：{}", file.getAbsolutePath(), lastPeekModify, lastPeekLength);
            Thread.sleep(100);
            waitedTime = System.currentTimeMillis()-beginTime;
        }while(lastPeekModify!=file.lastModified() || lastPeekLength!=file.length());
    }
    
    public static class MD5 {

        @SneakyThrows
        public static String encode(String text) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(text.getBytes());
            return DatatypeConverter.printHexBinary(md.digest());
        }

        public static String encode(File file) throws Exception {
            MessageDigest md = MessageDigest.getInstance("MD5");
            FileInputStream is = new FileInputStream(file);
            byte[] buff = new byte[1024];
            int len;
            while ((len = is.read(buff)) > 0)
                md.update(buff, 0, len);
            is.close();
            return DatatypeConverter.printHexBinary(md.digest());
        }

        @SneakyThrows
        public static String encode(byte[] bytes) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(bytes);
            return DatatypeConverter.printHexBinary(md.digest());
        }
        
        /** 计算结束is会被关闭 */
        @SneakyThrows
        public static String encode(InputStream is) {
            @Cleanup InputStream i = is;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[1024];
            int len = 0;
            while ((len=i.read(buf))!=-1) {
                md.update(buf, 0, len);
            }
            return DatatypeConverter.printHexBinary(md.digest());
        }
        
    }
    
}