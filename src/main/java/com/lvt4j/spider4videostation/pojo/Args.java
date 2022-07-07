package com.lvt4j.spider4videostation.pojo;

import static com.lvt4j.spider4videostation.Utils.ObjectMapper;

import java.util.Scanner;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import lombok.Cleanup;

/**
 *
 * @author LV on 2022年7月4日
 */
public class Args {

    private static String Delimiter = "--type |--lang |--input |--limit |--allowguess ";
    
    public String type;
    public String lang;
    public Input input;
    public int limit = 1;
    public boolean allowguess;
    
    public static class Input {
        public String title;
        public String original_available;
        
        public String year() {
            if(StringUtils.isBlank(original_available)) return null;
            String[] splits = original_available.split("-");
            if(splits.length==0) return null;
            return splits[0];
        }
    }
    
    public static Args parse(String args) {
        @Cleanup Scanner scanner = new Scanner(args);
        scanner.useDelimiter(Delimiter);
        
        Args parsed = new Args();
        
        String name = null,value=null;
        while((name=scanner.findInLine(Delimiter))!=null){
            try{
                value = scanner.next().trim();
                switch(name){
                case "--type ":
                    parsed.type = value;
                    break;
                case "--lang ":
                    parsed.lang = value;
                    break;
                case "--input ":
                    parsed.input = ObjectMapper.readValue(value, Input.class);
                    parsed.input.title = StringUtils.trim(parsed.input.title);
                    parsed.input.original_available = StringUtils.trim(parsed.input.original_available);
                    Validate.notBlank(parsed.input.title, "关键词条件不能为空");
                    break;
                case "--limit ":
                    parsed.limit = Integer.valueOf(value);
                    break;
                case "--allowguess ":
                    parsed.allowguess = BooleanUtils.toBoolean(value);
                    break;
                default: break;
                }
            }catch(Exception e){
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.format("错误的参数%s：%s", name, value), e);
            }
        }
        return parsed;
    }
    
    public boolean reachLimit(int num) {
        if(limit<=0) return false;
        return num>=limit;
    }
    
}