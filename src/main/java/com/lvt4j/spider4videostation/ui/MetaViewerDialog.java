package com.lvt4j.spider4videostation.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Base64;
import java.util.Stack;

import javax.imageio.ImageIO;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import com.lvt4j.spider4videostation.Spider4VideoStationApp;

import lombok.extern.slf4j.Slf4j;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.lvt4j.spider4videostation.metadata.VSmeta;

/**
 * 元数据文件查看器 —— 统一查看 vsmeta / nfo
 *
 * @author LV on 2023年2月9日
 */
@Slf4j
public class MetaViewerDialog extends JDialog {

    private File currentDir;
    private Stack<File> backStack = new Stack<>();
    private Stack<File> forwardStack = new Stack<>();

    private JTextField pathTf;
    private DefaultListModel<String> listModel;
    private JList<String> fileList;
    private java.util.List<File> fileEntries = new java.util.ArrayList<>();
    private JButton backBtn;
    private JButton forwardBtn;

    private JPanel detailPanel;
    private JScrollPane detailScroll;
    private SwingWorker<?, ?> loadWorker;

    private File currentMetaFile;
    private VSmeta currentVSmeta;
    private Document currentNfoDoc;
    private boolean isVSmetaMode;
    private final java.util.Map<String, javax.swing.JComponent> fieldComponents = new java.util.HashMap<>();

    public MetaViewerDialog(Frame owner, File initialDir) {
        super(owner, "元数据查看器", true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        this.currentDir = initialDir.isDirectory() ? initialDir : initialDir.getParentFile();
        if (currentDir == null) currentDir = new File("E:\\Downloads");

        JPanel content = new JPanel(new BorderLayout(5, 5));
        content.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 导航栏
        JPanel navPanel = new JPanel(new BorderLayout(5, 5));
        JPanel navBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        JButton upBtn = new JButton("\u2B06");
        upBtn.addActionListener(e -> navigateUp());
        backBtn = new JButton("\u2B05");
        backBtn.addActionListener(e -> navigateBack());
        forwardBtn = new JButton("\u27A1");
        forwardBtn.addActionListener(e -> navigateForward());
        navBtnPanel.add(upBtn);
        navBtnPanel.add(backBtn);
        navBtnPanel.add(forwardBtn);
        pathTf = new JTextField(currentDir.getAbsolutePath());
        JButton goBtn = new JButton("\u25B6");
        goBtn.addActionListener(e -> navigateTo(pathTf.getText().trim()));
        pathTf.addActionListener(e -> navigateTo(pathTf.getText().trim()));
        navPanel.add(navBtnPanel, BorderLayout.WEST);
        navPanel.add(pathTf, BorderLayout.CENTER);
        navPanel.add(goBtn, BorderLayout.EAST);

        // 左侧文件列表 — .vsmeta + .nfo + 文件夹
        listModel = new DefaultListModel<>();
        fileList = new JList<>(listModel);
        Font cjkFont = Spider4VideoStationApp.getCJKFont();
        if (cjkFont != null) {
            fileList.setFont(cjkFont.deriveFont((float) fileList.getFont().getSize()));
            pathTf.setFont(cjkFont.deriveFont((float) pathTf.getFont().getSize()));
        }
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = fileList.getSelectedIndex();
                if (idx < 0 || idx >= fileEntries.size()) return;
                File f = fileEntries.get(idx);
                if (e.getClickCount() == 2) {
                    if (f.isDirectory()) enterDir(f);
                } else {
                    if (!f.isDirectory()) showDetail(f);
                }
            }
        });
        JScrollPane listScroll = new JScrollPane(fileList);
        listScroll.setPreferredSize(new Dimension(250, 0));

        // 右侧详情面板
        detailPanel = new JPanel(new GridBagLayout());
        detailScroll = new JScrollPane(detailPanel);
        detailScroll.getVerticalScrollBar().setUnitIncrement(16);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, detailScroll);
        splitPane.setResizeWeight(0.35);

        content.add(navPanel, BorderLayout.NORTH);
        content.add(splitPane, BorderLayout.CENTER);

        setContentPane(content);
        refreshList();
        updateNavButtons();
        setSize(900, 600);
        setLocationRelativeTo(owner);
    }

    private void enterDir(File dir) {
        backStack.push(currentDir);
        forwardStack.clear();
        currentDir = dir;
        refreshList();
        updateNavButtons();
    }

    private void navigateUp() {
        File parent = currentDir.getParentFile();
        if (parent != null) enterDir(parent);
    }

    private void navigateBack() {
        if (!backStack.isEmpty()) {
            forwardStack.push(currentDir);
            currentDir = backStack.pop();
            refreshList();
            updateNavButtons();
        }
    }

    private void navigateForward() {
        if (!forwardStack.isEmpty()) {
            backStack.push(currentDir);
            currentDir = forwardStack.pop();
            refreshList();
            updateNavButtons();
        }
    }

    private void navigateTo(String path) {
        if (path.isEmpty()) return;
        File dir = new File(path);
        if (!dir.exists()) return;
        if (!dir.isDirectory()) dir = dir.getParentFile();
        if (dir == null || !dir.exists()) return;
        backStack.push(currentDir);
        forwardStack.clear();
        currentDir = dir;
        refreshList();
        updateNavButtons();
    }

    private void refreshList() {
        pathTf.setText(currentDir.getAbsolutePath());
        listModel.clear();
        fileEntries.clear();
        File[] files = currentDir.listFiles();
        if (files != null) {
            java.util.Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            for (File f : files) {
                if (f.isHidden()) continue;
                if (f.isDirectory()) {
                    if (f.getName().startsWith(".")) continue;
                } else {
                    String name = f.getName().toLowerCase();
                    if (!name.endsWith(".vsmeta") && !name.endsWith(".nfo")) continue;
                }
                fileEntries.add(f);
                listModel.addElement(f.getName());
            }
        }
        detailPanel.removeAll();
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private void updateNavButtons() {
        backBtn.setEnabled(!backStack.isEmpty());
        forwardBtn.setEnabled(!forwardStack.isEmpty());
    }

    private void showDetail(File file) {
        if (loadWorker != null && !loadWorker.isDone()) loadWorker.cancel(true);

        currentMetaFile = file;
        currentVSmeta = null;
        currentNfoDoc = null;
        fieldComponents.clear();

        detailPanel.removeAll();
        detailPanel.add(new JLabel("加载中..."));
        detailPanel.revalidate();
        detailPanel.repaint();

        String name = file.getName().toLowerCase();
        final boolean isVsmeta = name.endsWith(".vsmeta");
        loadWorker = new SwingWorker<JPanel, Void>() {
            @Override
            protected JPanel doInBackground() throws Exception {
                return isVsmeta ? buildVSmetaPanel(file) : buildNfoPanel(file);
            }
            @Override
            protected void done() {
                if (isCancelled()) return;
                try {
                    JPanel built = get();
                    JPanel wrapper = new JPanel(new BorderLayout(0, 5));
                    JButton saveBtn = new JButton("保存");
                    saveBtn.addActionListener(e -> performSave());

                    boolean lockdata = false;
                    if (!isVSmetaMode) {
                        Element lockEl = currentNfoDoc.selectFirst("lockdata");
                        lockdata = lockEl != null && "true".equalsIgnoreCase(lockEl.ownText());
                    } else {
                        lockdata = (currentVSmeta.locked != null && currentVSmeta.locked == 1)
                                || (currentVSmeta.episodeLocked != null && currentVSmeta.episodeLocked == 1);
                    }
                    JCheckBox lockCb = new JCheckBox("锁定元数据");
                    lockCb.setSelected(lockdata);
                    fieldComponents.put("lockdata", lockCb);

                    JPanel topPanel = new JPanel(new BorderLayout());
                    topPanel.add(lockCb, BorderLayout.WEST);
                    JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                    btnPanel.add(saveBtn);
                    topPanel.add(btnPanel, BorderLayout.EAST);
                    wrapper.add(topPanel, BorderLayout.NORTH);
                    wrapper.add(built, BorderLayout.CENTER);

                    detailPanel.removeAll();
                    GridBagConstraints c = new GridBagConstraints();
                    c.gridx = 0; c.gridy = 0;
                    c.weightx = 1.0; c.weighty = 1.0;
                    c.anchor = GridBagConstraints.NORTHWEST;
                    c.fill = GridBagConstraints.HORIZONTAL;
                    detailPanel.add(wrapper, c);
                } catch (Exception e) {
                    detailPanel.removeAll();
                    detailPanel.add(new JLabel("解析失败: " + e.getMessage()));
                }
                detailPanel.revalidate();
                detailPanel.repaint();
            }
        };
        loadWorker.execute();
    }

    // ==================== vsmeta 详情：在后台线程构建完整 JPanel ====================

    private JPanel buildVSmetaPanel(File vsmetaFile) throws Exception {
        VSmeta vsmeta = new VSmeta(vsmetaFile);
        currentVSmeta = vsmeta;
        isVSmetaMode = true;

        java.util.List<java.awt.Component> rows = new java.util.ArrayList<>();
        JComboBox<String> typeCb = new JComboBox<>(new String[]{"电影", "剧集"});
        typeCb.setSelectedItem(vsmeta.type == VSmeta.TypeMovie ? "电影" : "剧集");
        typeCb.setEnabled(false);
        rows.add(new JLabel("类型:"));
        rows.add(typeCb);
        fieldComponents.put("type", typeCb);
        addRow(rows, "showTitle", "标题", vsmeta.showTitle);
        if (!isEmpty(vsmeta.episodeTitle) && !vsmeta.episodeTitle.equals(vsmeta.showTitle))
            addRow(rows, "episodeTitle", "剧集标题", vsmeta.episodeTitle);
        addRow(rows, "year", "年份", vsmeta.year > 0 ? String.valueOf(vsmeta.year) : null);
        addRow(rows, "episodeReleaseDate", "发布日期", vsmeta.episodeReleaseDate);
        addRow(rows, "chapterSummary", "简介", vsmeta.chapterSummary);
        addRow(rows, "classification", "分级", vsmeta.classification);
        if (!java.util.Arrays.equals(vsmeta.rating, VSmeta.RatingRaw)) {
            if (vsmeta.rating.length == 1) addRow(rows, "rating", "评分", String.valueOf(vsmeta.rating[0] & 0xFF));
            else addRow(rows, "rating", "评分", "已设置");
        }
        addRow(rows, "genres", "类型", listStr(vsmeta.genres));
        addRow(rows, "casts", "演员", listStr(vsmeta.casts));
        addRow(rows, "directors", "导演", listStr(vsmeta.directors));
        addRow(rows, "writers", "编剧", listStr(vsmeta.writers));
        if (vsmeta.type == VSmeta.TypeEpisode) {
            addRow(rows, "season", "季", String.valueOf(vsmeta.season));
            addRow(rows, "episode", "集", String.valueOf(vsmeta.episode));
        }
        addImageRow(rows, "海报", decodeBase64Image(vsmeta.posterData));
        addImageRow(rows, "缩略图", decodeBase64Image(vsmeta.episodeThumbData));
        addImageRow(rows, "背景", decodeBase64Image(vsmeta.backdropData));

        return assemblePanel(rows);
    }

    private static Image decodeBase64Image(String base64) {
        if (isEmpty(base64)) return null;
        try {
            byte[] data = Base64.getDecoder().decode(base64.replace("\n", "").replace("\r", ""));
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img == null) return null;
            if (img.getWidth() > 400) {
                int h = img.getHeight() * 400 / img.getWidth();
                return img.getScaledInstance(400, h, Image.SCALE_SMOOTH);
            }
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== nfo 详情：在后台线程构建完整 JPanel ====================

    private JPanel buildNfoPanel(File nfoFile) throws Exception {
        Document doc = Jsoup.parse(nfoFile, "UTF-8");
        currentNfoDoc = doc;
        isVSmetaMode = false;
        File parentDir = nfoFile.getParentFile();

        java.util.List<java.awt.Component> rows = new java.util.ArrayList<>();
        addRow(rows, "title", "标题", ownText(doc, "title"));
        addRow(rows, "tagline", "标语", ownText(doc, "tagline"));
        addRow(rows, "plot", "情节", ownText(doc, "plot"));
        addRow(rows, "premiered", "首映", ownText(doc, "premiered"));
        addRow(rows, "year", "年份", ownText(doc, "year"));
        addRow(rows, "aired", "播出", ownText(doc, "aired"));
        addRow(rows, "season", "季", ownText(doc, "season"));
        addRow(rows, "seasonnumber", "季号", ownText(doc, "seasonnumber"));
        addRow(rows, "episode", "集", ownText(doc, "episode"));
        addRow(rows, "mpaa", "分级", ownText(doc, "mpaa"));
        addRow(rows, "rating", "评分", ownText(doc, "rating"));
        addRow(rows, "genre", "类型", listText(doc, "genre"));
        addRow(rows, "actor", "演员", listText(doc, "actor"));
        addRow(rows, "director", "导演", listText(doc, "director"));
        addRow(rows, "credits", "编剧", listText(doc, "credits"));

        for (Element el : doc.select("thumb"))
            addImageRow(rows, "缩略图", loadImageFile(parentDir, ownText(el)));
        for (Element el : doc.select("fanart")) {
            if (el.parent() != null && "art".equals(el.parent().tagName())) continue;
            addImageRow(rows, "背景", loadImageFile(parentDir, ownText(el)));
        }
        // Jellyfin <art> 扩展
        for (Element el : doc.select("art poster"))
            addImageRow(rows, "海报(art)", loadImageFile(parentDir, ownText(el)));
        for (Element el : doc.select("art fanart"))
            addImageRow(rows, "背景(art)", loadImageFile(parentDir, ownText(el)));

        return assemblePanel(rows);
    }

    private static Image loadImageFile(File parentDir, String imgPath) {
        if (isEmpty(imgPath)) return null;
        try {
            File imgFile = new File(imgPath);
            if (!imgFile.isAbsolute()) imgFile = new File(parentDir, imgPath);
            if (!imgFile.exists()) return null;
            BufferedImage img = ImageIO.read(imgFile);
            if (img == null) return null;
            if (img.getWidth() > 400) {
                int h = img.getHeight() * 400 / img.getWidth();
                return img.getScaledInstance(400, h, Image.SCALE_SMOOTH);
            }
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 组装面板 ====================

    /** 添加可编辑文本行 */
    /** 添加文本行 */
    private void addRow(java.util.List<java.awt.Component> rows, String fieldKey, String label, String value) {
        if (isEmpty(value)) return;
        rows.add(new JLabel(label + ":"));
        JTextArea ta = new JTextArea(value);
        ta.setEditable(true);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        Font cjk = Spider4VideoStationApp.getCJKFont();
        ta.setFont(cjk != null ? cjk.deriveFont((float)new JLabel().getFont().getSize()) : ta.getFont());
        fieldComponents.put(fieldKey, ta);
        rows.add(ta);
    }

    /** 添加图片行 */
    private static void addImageRow(java.util.List<java.awt.Component> rows, String label, Image img) {
        if (img == null) return;
        rows.add(new JLabel(label + ":"));
        rows.add(new JLabel(new ImageIcon(img)));
    }

    /** 将行列表组装为 GridBagLayout JPanel */
    private static JPanel assemblePanel(java.util.List<java.awt.Component> rows) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        int row = 0;
        for (int i = 0; i < rows.size(); i += 2) {
            JLabel labelComp = (JLabel) rows.get(i);
            java.awt.Component valComp = rows.get(i + 1);
            boolean isImage = valComp instanceof JLabel && ((JLabel) valComp).getIcon() != null;

            gbc.gridy = row;
            gbc.gridx = 0;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(labelComp, gbc);
            gbc.gridx = 1;
            if (isImage) {
                gbc.weightx = 0;
                gbc.fill = GridBagConstraints.NONE;
            } else {
                gbc.weightx = 1.0;
                gbc.fill = GridBagConstraints.HORIZONTAL;
            }
            panel.add(valComp, gbc);
            row++;
        }
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JLabel(), gbc);
        return panel;
    }

    // ==================== 工具方法 ====================

    private static String ownText(Document doc, String tag) {
        Element el = doc.selectFirst(tag);
        return el != null ? el.ownText() : null;
    }

    private static String ownText(Element el) {
        return el != null ? el.ownText() : null;
    }

    private static String listText(Document doc, String tag) {
        java.util.List<String> items = new java.util.ArrayList<>();
        for (Element el : doc.select(tag)) {
            String v = el.text();
            if (v != null && !v.isEmpty()) items.add(v);
        }
        return items.isEmpty() ? null : String.join(", ", items);
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private static String listStr(java.util.List<String> list) {
        return list == null || list.isEmpty() ? null : String.join(", ", list);
    }

    // ==================== 保存 ====================

    private void performSave() {
        try {
            if (isVSmetaMode) saveVSmeta();
            else saveNfo();
            JOptionPane.showMessageDialog(this, "保存成功", "保存", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            log.error("保存失败", e);
            JOptionPane.showMessageDialog(this, "保存失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveVSmeta() throws Exception {
        VSmeta m = currentVSmeta;
        String typeVal = getFieldText("type");
        if ("电影".equals(typeVal)) m.type = VSmeta.TypeMovie;
        else if ("剧集".equals(typeVal)) m.type = VSmeta.TypeEpisode;
        else throw new IllegalArgumentException("类型必须为\"电影\"或\"剧集\"");

        m.showTitle = getFieldText("showTitle");
        if (m.showTitle == null || m.showTitle.isEmpty())
            throw new IllegalArgumentException("标题不能为空");
        m.showTitle2 = m.showTitle;

        m.episodeTitle = nullOr(getFieldText("episodeTitle"));
        m.episodeReleaseDate = nullOr(getFieldText("episodeReleaseDate"));
        m.chapterSummary = nullOr(getFieldText("chapterSummary"));
        m.classification = nullOr(getFieldText("classification"));

        String yearStr = getFieldText("year");
        m.year = (yearStr != null && !yearStr.isEmpty()) ? Integer.parseInt(yearStr) : 0;

        m.genres = parseList(getFieldText("genres"));
        m.casts = parseList(getFieldText("casts"));
        m.directors = parseList(getFieldText("directors"));
        m.writers = parseList(getFieldText("writers"));

        String seasonStr = getFieldText("season");
        if (seasonStr != null && !seasonStr.isEmpty()) m.season = Integer.parseInt(seasonStr);
        String episodeStr = getFieldText("episode");
        if (episodeStr != null && !episodeStr.isEmpty()) m.episode = Integer.parseInt(episodeStr);

        boolean lock = "true".equalsIgnoreCase(getFieldText("lockdata"));
        m.locked = lock ? (byte)1 : null;
        m.episodeLocked = lock ? (byte)1 : null;

        m.write(currentMetaFile);
    }

    private void saveNfo() throws Exception {
        Document doc = currentNfoDoc;
        Element root = doc.body().children().first();
        String rootTag = root != null ? root.tagName() : "movie";

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<").append(rootTag).append(">\n");

        appendNfoTag(sb, "lockdata", getFieldText("lockdata"));

        appendNfoTag(sb, "title", getFieldText("title"));
        appendNfoTag(sb, "tagline", getFieldText("tagline"));
        appendNfoTag(sb, "plot", getFieldText("plot"));
        appendNfoTag(sb, "premiered", getFieldText("premiered"));
        appendNfoTag(sb, "year", getFieldText("year"));
        appendNfoTag(sb, "aired", getFieldText("aired"));
        appendNfoTag(sb, "season", getFieldText("season"));
        appendNfoTag(sb, "episode", getFieldText("episode"));
        appendNfoTag(sb, "mpaa", getFieldText("mpaa"));
        if ("movie".equals(rootTag))
            appendNfoTag(sb, "rating", getFieldText("rating"));

        appendNfoListTags(sb, "genre", getFieldText("genre"), null);
        appendNfoListTags(sb, "actor", getFieldText("actor"), "name");
        appendNfoListTags(sb, "director", getFieldText("director"), null);
        appendNfoListTags(sb, "credits", getFieldText("credits"), null);

        for (Element el : doc.select("thumb")) {
            String v = el.ownText();
            if (v != null && !v.isEmpty())
                sb.append("  <thumb>").append(escXml(v)).append("</thumb>\n");
        }
        for (Element el : doc.select("fanart")) {
            String v = el.ownText();
            if (v != null && !v.isEmpty())
                sb.append("  <fanart>").append(escXml(v)).append("</fanart>\n");
        }

        sb.append("</").append(rootTag).append(">\n");

        try (java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(currentMetaFile), java.nio.charset.StandardCharsets.UTF_8)) {
            w.write(sb.toString());
        }
    }

    private String getFieldText(String key) {
        javax.swing.JComponent c = fieldComponents.get(key);
        if (c == null) return null;
        if (c instanceof JTextArea) return ((JTextArea) c).getText().trim();
        if (c instanceof JCheckBox) return ((JCheckBox) c).isSelected() ? "true" : "false";
        if (c instanceof JComboBox) {
            Object sel = ((JComboBox<?>) c).getSelectedItem();
            return sel != null ? sel.toString() : null;
        }
        return null;
    }

    private static String nullOr(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static java.util.List<String> parseList(String s) {
        if (s == null || s.trim().isEmpty()) return new java.util.ArrayList<>();
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String item : s.split("\\s*,\\s*")) {
            String t = item.trim();
            if (!t.isEmpty()) list.add(t);
        }
        return list;
    }

    private static void appendNfoTag(StringBuilder sb, String tag, String val) {
        if (val != null && !val.isEmpty())
            sb.append("  <").append(tag).append(">").append(escXml(val)).append("</").append(tag).append(">\n");
    }

    private static void appendNfoListTags(StringBuilder sb, String tag, String text, String subTag) {
        if (text == null || text.trim().isEmpty()) return;
        for (String val : text.split("\\s*,\\s*")) {
            if (!val.isEmpty()) {
                sb.append("  <").append(tag).append(">");
                if (subTag != null) sb.append("<").append(subTag).append(">").append(escXml(val)).append("</").append(subTag).append(">");
                else sb.append(escXml(val));
                sb.append("</").append(tag).append(">\n");
            }
        }
    }

    private static String escXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
