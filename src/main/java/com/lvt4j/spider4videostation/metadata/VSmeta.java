package com.lvt4j.spider4videostation.metadata;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.lang.Nullable;

import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@NoArgsConstructor
public class VSmeta{

    public static final int MagicNumber0 = 0x08;

    public static final int TypeMovie = 1;
    public static final int TypeEpisode = 2;
    /** 类型 */
    public int type;

    /** 节目标题 */
    public static final int ShowTitle = 0x12;
    /** 节目标题 */
    public String showTitle;

    /** 节目标题2 */
    public static final int ShowTitle2 = 0x1a;
    /** 节目标题2 */
    public String showTitle2;

    /** 剧集标题 */
    public static final int EpisodeTitle = 0x22;
    /** 剧集标题（电影时是标语） */
    public String episodeTitle;

    /** 剧集年 */
    public static final int Year = 0x28;
    /** 剧集年 */
    public int year;

    /** 剧集发布日期 */
    public static final int EpisodeReleaseDate = 0x32;
    /**
     * 电影时是电影 发布日期 (yyyy-MM-dd)
     * 剧集时是 单集的 发布日期 (yyyy-MM-dd)
     */
    public String episodeReleaseDate;

    /** 剧集信息锁定 */
    public static final int EpisodeLocked = 0x38;
    /** 剧集信息锁定 */
    @Nullable
    public Byte episodeLocked;

    /** 章节小结 */
    public static final int ChapterSummary = 0x42;
    /** 章节小结 */
    public String chapterSummary;

    /** 剧集信息 */
    public static final int EpisodeMetaJson = 0x4a;
    /** 剧集信息 */
    @Nullable
    public String episodeMetaJson;

    /** 级别 */
    public static final int Classification = 0x5a;
    /** 级别 */
    public String classification;

    /** 评分 */
    public static final int Rating = 0x60;
    public static final byte[] RatingRaw = new byte[]{(byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, 0x01};
    /** 评分 */
    public byte[] rating = RatingRaw;

    /** 缩略图 */
    public static final int EpisodeThumbData = 0x8a;
    /**
     * 缩略图
     * 剧集时是对应剧集的截图
     */
    public String episodeThumbData;
    /** 缩略图MD5 */
    public static final int EpisodeThumbMd5 = 0x92;
    /** 缩略图MD5 */
    public String episodeThumbMd5;

    //第一组
    /** 第一组 */
    public static final int Group1 = 0x52;

    /** 演员 */
    public static final int Tag1Cast = 0x0a;
    /** 演员 */
    public List<String> casts = new ArrayList<>();
    /** 导演 */
    public static final int Tag1Director = 0x12;
    /** 导演 */
    public List<String> directors = new ArrayList<>();
    /** 类型 */
    public static final int Tag1Genre = 0x1a;
    /** 类型 */
    public List<String> genres = new ArrayList<>();
    /** 编剧 */
    public static final int Tag1Writer = 0x22;
    /** 编剧 */
    public List<String> writers = new ArrayList<>();

    //第二组
    /** 第二组 */
    public static final int Group2 = 0x9a;
    /** 季 */
    public static final int Tag2Season = 0x08;
    /** 季 */
    public int season;
    /** 集 */
    public static final int Tag2Episode = 0x10;
    /** 集 */
    public int episode;
    /** 季发布年 */
    public static final int Tag2TvShowYear = 0x18;
    /** 季发布年 */
    public int tvShowYear;
    /** 季发布日期 */
    public static final int Tag2ReleaseDateTvShow = 0x22;
    /** 剧集时是 电视节目发布日期 */
    public String releaseDateTvShow;
    /** 季信息锁定 */
    public static final int Tag2Locked = 0x28;
    /** 季信息锁定 */
    public Byte locked;
    /** 季信息小结 */
    public static final int Tag2TvshowSummary = 0x32;
    /** 季信息小结 */
    public String tvshowSummary;
    /** 季海报数据 */
    public static final int Tag2PosterData = 0x3a;
    /**
     * 季海报数据
     * 剧集时是 剧集的总海报
     */
    public String posterData;
    /** 季海报MD5 */
    public static final int Tag2PosterMd5 = 0x42;
    /** 季海报MD5 */
    public String posterMd5;
    /** 季信息 */
    public static final int Tag2TvshowMetaJson = 0x4a;
    /** 季信息 */
    public String tvshowMetaJson;

    //第三组
    /** 第三组（剧集） */
    public static final int Tag2Group3 = 0x52;
    /** 第三组（电影） */
    public static final int Group3 = 0xaa;
    /** 背景数据 */
    public static final int Tag3BackdropData = 0x0a;
    /**
     * 背景图片
     * 图片base64格式，76长度一换行
     */
    public String backdropData;
    /** 背景MD5 */
    public static final int Tag3BackdropMd5 = 0x12;
    /** 背景MD5 */
    public String backdropMd5;
    /** 时间戳 */
    public static final int Tag3Timestamp = 0x18;
    /** 时间戳(s) */
    public Integer timestamp;

    public VSmeta(File vsmetaFile) throws Exception {
        try (FileInputStream fis = new FileInputStream(vsmetaFile)) {
            parse(fis);
        }
    }

    public void parse(InputStream is) throws Exception {
        is = new AddrsInputStream(is);
        AddrsInputStream ais = (AddrsInputStream)is;

        Validate.isTrue(is.read() == MagicNumber0);
        type = is.read();

        int tag;
        @SuppressWarnings("unused")
        int count;
        while((tag = is.read()) != -1) {
            //System.out.println("tag: " + Integer.toHexString(tag)+" at "+ais.printAddr());
            switch (tag) {
                case ShowTitle: showTitle = readString(is); break;
                case ShowTitle2: showTitle2 = readString(is); break;
                case EpisodeTitle: episodeTitle = readString(is); break;
                case Year: year = readInt(is); break;
                case EpisodeReleaseDate: episodeReleaseDate = readString(is); break;
                case EpisodeLocked: episodeLocked = (byte)is.read(); break;
                case ChapterSummary: chapterSummary = readString(is); break;
                case EpisodeMetaJson: episodeMetaJson = readString(is); break;
                case Classification: classification = readString(is); break;
                case Rating:
                    int rate0 = is.read();
                    if(rate0==0xff) {
                        rating = new byte[RatingRaw.length];
                        rating[0] = (byte)rate0;
                        for(int i = 1; i < RatingRaw.length; i++) rating[i] = (byte)is.read();
                    }else{
                        rating = new byte[1];
                        rating[0] = (byte)rate0;
                    }
                    break;
                case EpisodeThumbData:
                    count = readInt(is);
                    episodeThumbData = readString(is); break;
                case EpisodeThumbMd5:
                    count = readInt(is);
                    episodeThumbMd5 = readString(is); break;
                case Group1:
                    parseGroup1(new ByteArrayInputStream(readBuf(is)));
                    break;
                case Group2:
                    count = readInt(is);
                    parseGroup2(new ByteArrayInputStream(readBuf(is)));
                    break;
                case Group3:
                    count = readInt(is);
                    parseGroup3(new ByteArrayInputStream(readBuf(is)));
                    break;
                default:
                    throw new RuntimeException("Unknown tag: " + Integer.toHexString(tag)+" at "+ais.printAddr());
            }
        }
    }
    private void parseGroup1(InputStream is) throws Exception{
        int tag;
        while((tag = is.read()) != -1) {
            //System.out.println("tag1: " + Integer.toHexString(tag));
            switch (tag) {
            case Tag1Cast: casts.add(readString(is)); break;
            case Tag1Director: directors.add(readString(is)); break;
            case Tag1Genre: genres.add(readString(is)); break;
            case Tag1Writer: writers.add(readString(is)); break;
            default:
                throw new RuntimeException("Unknown tag in group1: " + Integer.toHexString(tag));
            }
        }
    }
    private void parseGroup2(InputStream is) throws Exception{
        int tag;
        while((tag = is.read()) != -1) {
            //System.out.println("tag2: " + Integer.toHexString(tag));
            switch (tag) {
            case Tag2Season: season=readInt(is); break;
            case Tag2Episode: episode=readInt(is); break;
            case Tag2TvShowYear: tvShowYear=readInt(is); break;
            case Tag2ReleaseDateTvShow: releaseDateTvShow=readString(is); break;
            case Tag2Locked: locked=(byte)is.read(); break;
            case Tag2TvshowSummary: tvshowSummary=readString(is); break;
            case Tag2PosterData: posterData=readString(is); break;
            case Tag2PosterMd5: posterMd5=readString(is); break;
            case Tag2TvshowMetaJson: tvshowMetaJson=readString(is); break;
            case Tag2Group3:
                parseGroup3(new ByteArrayInputStream(readBuf(is)));
                break;
            default:
                throw new RuntimeException("Unknown tag in group2: " + Integer.toHexString(tag));
            }
        }
    }
    private void parseGroup3(InputStream is) throws Exception {
        int tag;
        while((tag = is.read()) != -1){
            //System.out.println("tag3: " + Integer.toHexString(tag));
            switch (tag){
            case Tag3BackdropData: backdropData = readString(is); break;
            case Tag3BackdropMd5: backdropMd5 = readString(is); break;
            case Tag3Timestamp: timestamp = readInt(is); break;
            default:
                throw new RuntimeException("Unknown tag in group3: " + Integer.toHexString(tag));
            }
        }
    }

    private static int readInt(InputStream is) throws Exception {
        int result = 0;
        int shift = 0;
        int b;
        do {
            b = is.read();
            if (b == -1) {
                throw new EOFException("Unexpected end of input");
            }
            result |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return result;
    }
    private static byte[] readBuf(InputStream is) throws Exception {
        int length = readInt(is);
        byte[] buff = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = is.read(buff, offset, length - offset);
            if (read == -1) throw new EOFException("Unexpected end of input");
            offset += read;
        }
        return buff;
    }
    private static String readString(InputStream is) throws Exception {
        return new String(readBuf(is),"utf8");
    }

    public void write(File file) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(MagicNumber0);
            fos.write(type);
            switch (type){
            case 1:
                writeMovie(fos);
                break;
            case 2:
                writeEpisode(fos);
                break;
            default:
                throw new RuntimeException("Unknown type: " + type);
            }
        }
    }
    private void writeMovie(OutputStream out) throws Exception {
        out.write(ShowTitle); writeString(out, showTitle);
        if(showTitle2!=null) {out.write(ShowTitle2); writeString(out, showTitle2);}
        if(episodeTitle!=null) {out.write(EpisodeTitle); writeString(out, episodeTitle);}
        out.write(Year); writeInt(out, year);
        if(episodeReleaseDate!=null) {out.write(EpisodeReleaseDate); writeString(out, episodeReleaseDate);}
        if(episodeLocked!=null) {out.write(EpisodeLocked); out.write(episodeLocked);}
        if(chapterSummary!=null) {out.write(ChapterSummary); writeString(out, chapterSummary);}
        if(episodeMetaJson!=null) {out.write(EpisodeMetaJson); writeString(out, episodeMetaJson);}

        ByteArrayOutputStream tag1 = new ByteArrayOutputStream();
        if(!casts.isEmpty()){
            for(String cast: casts){
                tag1.write(Tag1Cast);
                writeString(tag1, cast);
            }
        }
        if(!directors.isEmpty()){
            for(String director: directors){
                tag1.write(Tag1Director);
                writeString(tag1, director);
            }
        }
        if(!genres.isEmpty()){
            for(String genre: genres){
                tag1.write(Tag1Genre);
                writeString(tag1, genre);
            }
        }
        if(!writers.isEmpty()){
            for(String writer: writers){
                tag1.write(Tag1Writer);
                writeString(tag1, writer);
            }
        }
        if(tag1.size() > 0){ out.write(Group1); writeBuf(out, tag1.toByteArray());}

        if(classification!=null) { out.write(Classification); writeString(out, classification); }
        out.write(Rating); out.write(rating);

        if(episodeThumbData!=null) {out.write(EpisodeThumbData); writeInt(out, 1); writeString(out, episodeThumbData);}
        if(episodeThumbMd5!=null) {out.write(EpisodeThumbMd5); writeInt(out, 1); writeString(out, episodeThumbMd5);}

        ByteArrayOutputStream tag3 = new ByteArrayOutputStream();
        if(backdropData!=null) {tag3.write(Tag3BackdropData); writeString(tag3, backdropData);}
        if(backdropMd5!=null) {tag3.write(Tag3BackdropMd5); writeString(tag3, backdropMd5);}
        if(timestamp!=null) {tag3.write(Tag3Timestamp); writeInt(tag3, timestamp);}
        if(tag3.size() > 0){ out.write(Group3); writeInt(out, 1); writeBuf(out, tag3.toByteArray()); }
    }
    private void writeEpisode(OutputStream out) throws Exception {
        out.write(ShowTitle); writeString(out, showTitle);
        if(showTitle2!=null) {out.write(ShowTitle2); writeString(out, showTitle2);}
        if(episodeTitle!=null) {out.write(EpisodeTitle); writeString(out, episodeTitle);}
        out.write(Year); writeInt(out, year);
        if(episodeReleaseDate!=null) {out.write(EpisodeReleaseDate); writeString(out, episodeReleaseDate);}
        if(episodeLocked!=null) {out.write(EpisodeLocked); out.write(episodeLocked);}
        if(chapterSummary!=null) {out.write(ChapterSummary); writeString(out, chapterSummary);}
        if(episodeMetaJson!=null) {out.write(EpisodeMetaJson); writeString(out, episodeMetaJson);}

        ByteArrayOutputStream tag1 = new ByteArrayOutputStream();
        if(!casts.isEmpty()){
            for(String cast: casts){
                tag1.write(Tag1Cast);
                writeString(tag1, cast);
            }
        }
        if(!directors.isEmpty()){
            for(String director: directors){
                tag1.write(Tag1Director);
                writeString(tag1, director);
            }
        }
        if(!genres.isEmpty()){
            for(String genre: genres){
                tag1.write(Tag1Genre);
                writeString(tag1, genre);
            }
        }
        if(!writers.isEmpty()){
            for(String writer: writers){
                tag1.write(Tag1Writer);
                writeString(tag1, writer);
            }
        }
        if(tag1.size() > 0){
            out.write(Group1);
            writeBuf(out, tag1.toByteArray());
        }

        if(classification!=null) { out.write(Classification); writeString(out, classification); }
        out.write(Rating); out.write(rating);

        if(episodeThumbData!=null) {out.write(EpisodeThumbData); writeInt(out, 1); writeString(out, episodeThumbData);}
        if(episodeThumbMd5!=null) {out.write(EpisodeThumbMd5); writeInt(out, 1); writeString(out, episodeThumbMd5);}

        ByteArrayOutputStream tag2 = new ByteArrayOutputStream();
        tag2.write(Tag2Season); writeInt(tag2, season);
        tag2.write(Tag2Episode); writeInt(tag2, episode);
        tag2.write(Tag2TvShowYear); writeInt(tag2, tvShowYear);
        if(releaseDateTvShow!=null) {tag2.write(Tag2ReleaseDateTvShow); writeString(tag2, releaseDateTvShow);}
        if(locked!=null) {tag2.write(Tag2Locked); tag2.write(locked);}
        if(tvshowSummary!=null) {tag2.write(Tag2TvshowSummary); writeString(tag2, tvshowSummary);}
        if(posterData!=null) {tag2.write(Tag2PosterData); writeString(tag2, posterData);}
        if(posterMd5!=null) {tag2.write(Tag2PosterMd5); writeString(tag2, posterMd5);}
        if(tvshowMetaJson!=null) {tag2.write(Tag2TvshowMetaJson); writeString(tag2, tvshowMetaJson);}

        ByteArrayOutputStream tag3 = new ByteArrayOutputStream();
        if(backdropData!=null) {tag3.write(Tag3BackdropData); writeString(tag3, backdropData);}
        if(backdropMd5!=null) {tag3.write(Tag3BackdropMd5); writeString(tag3, backdropMd5);}
        if(timestamp!=null) {tag3.write(Tag3Timestamp); writeInt(tag3, timestamp);}
        if(tag3.size() > 0){
            tag2.write(Tag2Group3);
            writeBuf(tag2, tag3.toByteArray());
        }
        out.write(Group2); out.write(1); writeBuf(out, tag2.toByteArray());
    }

    private static void writeInt(OutputStream out, int value) throws IOException {
        int v = (int)(value & 0xFFFFFFFFL);
        do {
            byte data = (byte) (v & 0x7F);
            v >>= 7;
            if (v != 0) {
                data |= 0x80;
            }
            out.write(data);
        } while (v != 0);
    }
    private static void writeBuf(OutputStream out, byte[] buf) throws IOException{
        writeInt(out, buf.length);
        out.write(buf);
    }
    private static void writeString(OutputStream out, String str) throws IOException{
        writeBuf(out, str.getBytes());
    }


    public static String readImgData(File imgFile) throws IOException {
        byte[] data = FileUtils.readFileToByteArray(imgFile);
        String str = Base64.getEncoder().encodeToString(data);
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            output.append(str.charAt(i));
            if ((i + 1) % 76 == 0) {
                output.append("\n");
            }
        }
        return output.toString();
    }


    @RequiredArgsConstructor
    static class AddrsInputStream extends InputStream {

        private final InputStream wrap;
        int addr;

        @Override
        public int read() throws IOException{
            try{
                return wrap.read();
            }finally {
                addr++;
            }
        }
        @Override
        public int read(byte[] b) throws IOException{
            int length = wrap.read(b);
            addr += length;
            return length;
        }
        @Override
        public int read(byte[] b, int off, int len) throws IOException{
            int length = wrap.read(b, off, len);
            addr += length;
            return length;
        }

        public String printAddr(){
            return Integer.toHexString(addr-1);
        }

    }
}