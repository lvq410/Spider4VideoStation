package com.lvt4j.spider4videostation.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.swing.SwingUtilities;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.lvt4j.spider4videostation.Spider4VideoStationApp;
import com.lvt4j.spider4videostation.TargetSite;
import com.lvt4j.spider4videostation.Utils;
import com.lvt4j.spider4videostation.pojo.Args;
import com.lvt4j.spider4videostation.pojo.Rst;
import com.lvt4j.spider4videostation.ffmpeg.FFmpegUtils;
import com.lvt4j.spider4videostation.ffmpeg.MediaInfo;
import com.lvt4j.spider4videostation.metadata.MetadataGenerator;
import com.lvt4j.spider4videostation.metadata.NfoGenerator;
import com.lvt4j.spider4videostation.metadata.VSmeta;
import com.lvt4j.spider4videostation.service.ConfigService;
import com.lvt4j.spider4videostation.service.FileCacher;
import com.lvt4j.spider4videostation.service.SearchOrchestratorService;

/**
 * 主界面 —— Swing实现，替代原 index.html
 *
 * @author LV on 2022年7月4日
 */
@Component
public class MainStage {

    @Autowired
    private SearchOrchestratorService searchOrchestrator;

    @Autowired
    private ConfigService configService;

    @Autowired
    private FileCacher fileCacher;

    @Autowired
    private MetadataGenerator metadataGenerator;

    private JFrame frame;

    private JComboBox<String> targetSiteCb;
    private JComboBox<String> typeCb;
    private JComboBox<String> langCb;
    private JTextField titleTf;
    private JTextField seasonTf;
    private JTextField episodeTf;
    private JTextField limitTf;
    private JPanel resultDetailPanel;
    private JTextArea resultRawTa;
    private DefaultListModel<String> resultTitleListModel;
    private JList<String> resultTitleList;
    private Color resultListBg, resultDetailBg, resultRawBg;
    private List<Object> lastResults = new ArrayList<>();
    private JLabel statusLb;
    private JButton searchBtn;
    private JButton genVsmetaBtn;
    private JButton genNfoBtn;
    private JButton renameBtn;
    private JComboBox<String> targetPathCb;
    private javax.swing.DefaultComboBoxModel<String> targetPathModel;
    private JButton pickerBtn;
    private JButton cleanCacheBtn;
    private JButton doubanLoginBtn;
    private JButton metaViewerBtn;
    private final List<JButton> settingButtons = new ArrayList<>();

    private JTextField webDriverAddrTf;
    private JTextField javdbOriginTf;
    private JTextField fileEpOffsetTf;
    private JTextField siteEpOffsetTf;
    private JTextField originalAvailableTf;
    private JTextField doubanMaxLimitTf;
    private JTextField baikeBaiduMaxLimitTf;

    public static final List<String> SETTING_KEYS = Arrays.asList(
        "webDriverAddr", "javdbOrigin", "fileEpOffset", "siteEpOffset",
        "originalAvailable", "doubanMaxLimit", "baikeBaiduMaxLimit");

    public void show() {
        frame = new JFrame("Spider4VideoStation");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(10, 10));

        JPanel leftPanel = buildLeftPanel();
        JPanel rightPanel = buildRightPanel();

        JPanel centerPanel = new JPanel(new BorderLayout(10, 0));
        centerPanel.add(leftPanel, BorderLayout.CENTER);
        centerPanel.add(rightPanel, BorderLayout.EAST);
        centerPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 5, 10));

        frame.add(centerPanel, BorderLayout.CENTER);

        statusLb = new JLabel("就绪");
        statusLb.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 10, 5, 10));
        frame.add(statusLb, BorderLayout.SOUTH);

        frame.setSize(1150, 750);
        frame.setLocationRelativeTo(null);

        // 给显示刮削/文件数据的控件应用 CJK 字体（韩文兼容），UI 标签按钮保持默认
        applyCJKFontToContent();

        frame.setVisible(true);

        loadSettings();
        loadRecentTargets();
    }

    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JPanel pickerPanel = new JPanel(new BorderLayout(5, 0));
        pickerPanel.add(new JLabel("抓取目标:"), BorderLayout.WEST);
        targetPathModel = new javax.swing.DefaultComboBoxModel<>();
        targetPathCb = new JComboBox<>(targetPathModel);
        targetPathCb.setEditable(true);
        pickerPanel.add(targetPathCb, BorderLayout.CENTER);
        pickerBtn = new JButton("...");
        pickerBtn.addActionListener(e -> openFilePicker());
        pickerPanel.add(pickerBtn, BorderLayout.EAST);

        JPanel contentPanel = new JPanel(new BorderLayout(5, 5));
        JPanel searchPanel = new JPanel(new GridBagLayout());
        searchPanel.setBorder(new TitledBorder("抓取目标网站"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        searchPanel.add(new JLabel("抓取目标网站:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        targetSiteCb = new JComboBox<>();
        for (TargetSite ts : TargetSite.values()) {
            targetSiteCb.addItem(ts.name);
        }
        targetSiteCb.setSelectedItem(TargetSite.Douban.name);
        targetSiteCb.addActionListener(e -> onTargetSiteSelect());
        searchPanel.add(targetSiteCb, gbc);
        gbc.gridwidth = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;

        gbc.gridx = 0; gbc.gridy = 1;
        searchPanel.add(new JLabel("类型:"), gbc);
        gbc.gridx = 1;
        typeCb = new JComboBox<>();
        typeCb.addActionListener(e -> onTypeSelect());
        searchPanel.add(typeCb, gbc);
        gbc.gridx = 2;
        searchPanel.add(new JLabel("语言:"), gbc);
        gbc.gridx = 3; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        langCb = new JComboBox<>();
        searchPanel.add(langCb, gbc);
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;

        gbc.gridx = 0; gbc.gridy = 2;
        searchPanel.add(new JLabel("关键词:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        titleTf = new JTextField(40);
        searchPanel.add(titleTf, gbc);
        gbc.gridwidth = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;

        gbc.gridx = 0; gbc.gridy = 3;
        searchPanel.add(new JLabel("季/集:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 3; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel epPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
        epPanel.add(new JLabel("季:"));
        seasonTf = new JTextField(3);
        epPanel.add(seasonTf);
        epPanel.add(new JLabel("集:"));
        episodeTf = new JTextField(3);
        epPanel.add(episodeTf);
        searchPanel.add(epPanel, gbc);
        gbc.gridwidth = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;

        gbc.gridx = 0; gbc.gridy = 4;
        searchPanel.add(new JLabel("条数:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        limitTf = new JTextField("1", 3);
        searchPanel.add(limitTf, gbc);
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;

        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE; gbc.anchor = GridBagConstraints.WEST;
        searchBtn = new JButton("搜索");
        searchBtn.addActionListener(e -> search());
        searchPanel.add(searchBtn, gbc);

        // Tab 1: 列表视图 — 左侧标题列表，右侧详情
        resultTitleListModel = new DefaultListModel<>();
        resultTitleList = new JList<>(resultTitleListModel);
        resultTitleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultTitleList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onResultTitleSelect();
        });
        JScrollPane titleScroll = new JScrollPane(resultTitleList);
        titleScroll.setPreferredSize(new Dimension(200, 0));

        resultDetailPanel = new JPanel(new GridBagLayout());
        JScrollPane detailScroll = new JScrollPane(resultDetailPanel);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, titleScroll, detailScroll);
        splitPane.setResizeWeight(0.3);

        // Tab 2: 原始 JSON
        resultRawTa = new JTextArea(20, 45);
        resultRawTa.setEditable(false);
        JScrollPane rawScroll = new JScrollPane(resultRawTa);

        resultListBg = resultTitleList.getBackground();
        resultDetailBg = resultDetailPanel.getBackground();
        resultRawBg = resultRawTa.getBackground();

        JTabbedPane resultTabs = new JTabbedPane();
        resultTabs.addTab("列表", splitPane);
        resultTabs.addTab("原始", rawScroll);
        resultTabs.setBorder(new TitledBorder("搜索结果"));

        genVsmetaBtn = new JButton("生成vsmeta");
        genVsmetaBtn.setEnabled(false);
        genVsmetaBtn.addActionListener(e -> generateMetadata(true));
        genNfoBtn = new JButton("生成nfo");
        genNfoBtn.setEnabled(false);
        genNfoBtn.addActionListener(e -> generateMetadata(false));
        JPanel applyPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 5));
        applyPanel.setBorder(new TitledBorder("应用"));
        applyPanel.add(genVsmetaBtn);
        applyPanel.add(genNfoBtn);
        renameBtn = new JButton("标准重命名");
        renameBtn.setEnabled(false);
        renameBtn.addActionListener(e -> renameStandard());
        applyPanel.add(renameBtn);

        JPanel resultPanel = new JPanel(new BorderLayout());
        resultPanel.add(resultTabs, BorderLayout.CENTER);
        resultPanel.add(applyPanel, BorderLayout.SOUTH);

        contentPanel.add(searchPanel, BorderLayout.NORTH);
        contentPanel.add(resultPanel, BorderLayout.CENTER);

        panel.add(pickerPanel, BorderLayout.NORTH);
        panel.add(contentPanel, BorderLayout.CENTER);

        onTargetSiteSelect();
        return panel;
    }

    private JPanel buildRightPanel() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setPreferredSize(new Dimension(450, 0));
        GridBagConstraints wgbc = new GridBagConstraints();
        wgbc.gridx = 0; wgbc.weightx = 1.0; wgbc.fill = GridBagConstraints.HORIZONTAL;
        wgbc.insets = new Insets(0, 0, 5, 0);

        wgbc.gridy = 0; wgbc.weighty = 0;
        wrapper.add(buildSettingsPanel(), wgbc);

        wgbc.gridy = 1; wgbc.weighty = 1.0; wgbc.fill = GridBagConstraints.BOTH;
        wrapper.add(buildToolsPanel(), wgbc);

        return wrapper;
    }

    private JPanel buildSettingsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("系统设置"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        row = addSettingRow(panel, gbc, row, "WebDriver地址", webDriverAddrTf = new JTextField(25));
        row = addSettingRow(panel, gbc, row, "Javdb地址", javdbOriginTf = new JTextField(25));
        row = addSettingRow(panel, gbc, row, "视频集号偏移量", fileEpOffsetTf = new JTextField(10));
        row = addSettingRow(panel, gbc, row, "源站集号偏移量", siteEpOffsetTf = new JTextField(10));
        row = addSettingRow(panel, gbc, row, "强制发布日期", originalAvailableTf = new JTextField(10));
        row = addSettingRow(panel, gbc, row, "豆瓣结果条数", doubanMaxLimitTf = new JTextField(10));
        row = addSettingRow(panel, gbc, row, "百度百科结果条数", baikeBaiduMaxLimitTf = new JTextField(10));

        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.NONE; gbc.anchor = GridBagConstraints.WEST;
        JPanel btnPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 0));
        cleanCacheBtn = new JButton("清空缓存");
        cleanCacheBtn.addActionListener(e -> cleanCache());
        doubanLoginBtn = new JButton("豆瓣登录");
        doubanLoginBtn.addActionListener(e -> openDoubanLogin());
        btnPanel.add(cleanCacheBtn);
        btnPanel.add(doubanLoginBtn);
        panel.add(btnPanel, gbc);

        gbc.gridx = 0; gbc.gridy = row + 1; gbc.gridwidth = 3; gbc.weightx = 0; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.VERTICAL;
        panel.add(new JLabel(), gbc);

        return panel;
    }

    private JPanel buildToolsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("工具"));
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3)) {
            @Override
            public java.awt.Dimension getPreferredSize() {
                int w = getParent() != null ? getParent().getWidth() : 450;
                if (w <= 0) w = 450;
                FlowLayout fl = (FlowLayout) getLayout();
                int hgap = fl.getHgap();
                int vgap = fl.getVgap();
                java.awt.Insets ins = getInsets();
                int innerW = w - ins.left - ins.right;
                int x = 0, rowH = 0;
                java.util.List<Integer> rowHeights = new java.util.ArrayList<>();
                for (int i = 0, n = getComponentCount(); i < n; i++) {
                    java.awt.Dimension d = getComponent(i).getPreferredSize();
                    if (x > 0 && x + d.width + hgap > innerW) {
                        rowHeights.add(rowH);
                        x = 0; rowH = 0;
                    }
                    x += d.width + hgap;
                    rowH = Math.max(rowH, d.height);
                }
                if (x > 0) rowHeights.add(rowH);
                int h = ins.top + ins.bottom;
                for (int rh : rowHeights) h += rh;
                if (rowHeights.size() > 1) h += (rowHeights.size() - 1) * vgap;
                return new java.awt.Dimension(innerW, h + 4);
            }
        };
        metaViewerBtn = new JButton("查看元数据文件");
        metaViewerBtn.addActionListener(e -> openMetaViewer());
        btnPanel.add(metaViewerBtn);
        JButton vsmeta2NfoBtn = new JButton("vsmeta转nfo");
        vsmeta2NfoBtn.addActionListener(e -> vsmetaToNfo());
        btnPanel.add(vsmeta2NfoBtn);
        JButton delNfoBtn = new JButton("递归删除nfo");
        delNfoBtn.addActionListener(e -> deleteNfo());
        btnPanel.add(delNfoBtn);
        JButton fixSeasonBtn = new JButton("vsmeta修season.nfo");
        fixSeasonBtn.addActionListener(e -> fixSeasonNfo());
        btnPanel.add(fixSeasonBtn);
        panel.add(btnPanel, BorderLayout.NORTH);
        return panel;
    }

    private void vsmetaToNfo() {
        String targetPath = getTargetPath();
        if (targetPath.isEmpty()) { statusLb.setText("请先选择抓取目标"); return; }
        recordTargetPath(targetPath);
        File target = new File(targetPath);

        java.util.List<File> vsmetaFiles = new java.util.ArrayList<>();
        if (target.isDirectory()) {
            collectVsmetaFiles(target, vsmetaFiles);
        } else if (target.isFile()) {
            File f = new File(target.getParentFile(), target.getName() + ".vsmeta");
            if (f.exists()) vsmetaFiles.add(f);
        }
        if (vsmetaFiles.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "未找到任何vsmeta文件", "vsmeta转nfo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // 文件夹模式按文件名排序
        if (target.isDirectory()) {
            vsmetaFiles.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        }

        // 二次确认
        int confirm = JOptionPane.showConfirmDialog(frame,
                "找到 " + vsmetaFiles.size() + " 个vsmeta文件，开始转换？",
                "vsmeta转nfo", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) return;

        setInputControlsEnabled(false);

        // 进度弹窗
        JDialog progressDialog = new JDialog(frame, "vsmeta转nfo", false);
        JTextArea logArea = new JTextArea(20, 55);
        logArea.setEditable(false);
        Font cjk = Spider4VideoStationApp.getCJKFont();
        if (cjk != null) logArea.setFont(cjk.deriveFont(12f));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(600, 350));
        JButton closeBtn = new JButton("关闭");
        closeBtn.setEnabled(false);
        closeBtn.addActionListener(e -> progressDialog.dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(closeBtn);
        JPanel content = new JPanel(new BorderLayout(5, 5));
        content.setBorder(new EmptyBorder(10, 10, 10, 10));
        content.add(logScroll, BorderLayout.CENTER);
        content.add(btnPanel, BorderLayout.SOUTH);
        progressDialog.setContentPane(content);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(frame);
        progressDialog.setVisible(true);

        int total = vsmetaFiles.size();
        File tempDir = new File("D:/tmp");
        tempDir.mkdirs();

        new SwingWorker<Void, String>() {
            private int doneCount = 0;
            private int successCount = 0;
            private int skippedCount = 0;
            private java.util.List<String> failedPaths = new java.util.ArrayList<>();
            private java.util.List<String> skippedPaths = new java.util.ArrayList<>();

            @Override
            protected Void doInBackground() throws Exception {
                // 按目录分组剧集vsmeta（用于生成season.nfo）
                java.util.Map<File, File> dirFirstEpVsmeta = new java.util.LinkedHashMap<>();
                if (target.isDirectory()) {
                    for (File vsmetaFile : vsmetaFiles) {
                        File dir = vsmetaFile.getParentFile();
                        if (dirFirstEpVsmeta.containsKey(dir)) continue;
                        try {
                            VSmeta v = new VSmeta(vsmetaFile);
                            if (v.type == VSmeta.TypeEpisode) dirFirstEpVsmeta.put(dir, vsmetaFile);
                        } catch (Exception ignored) {}
                    }
                }

                // 找排序第一条剧集vsmeta，用于在目标根目录生成tvshow.nfo
                File firstEpVsmetaFile = null;
                if (target.isDirectory()) {
                    for (File vsmetaFile : vsmetaFiles) {
                        try {
                            VSmeta v = new VSmeta(vsmetaFile);
                            if (v.type == VSmeta.TypeEpisode) { firstEpVsmetaFile = vsmetaFile; break; }
                        } catch (Exception ignored) {}
                    }
                }

                for (File vsmetaFile : vsmetaFiles) {
                    String vsmetaPath = vsmetaFile.getAbsolutePath();
                    String basePath = vsmetaPath.substring(0, vsmetaPath.length() - ".vsmeta".length());
                    int dot = basePath.lastIndexOf('.');
                    if (dot < 0) { doneCount++; continue; }
                    String nfoPath = basePath.substring(0, dot) + ".nfo";
                    File nfoFile = new File(nfoPath);

                    publish(vsmetaPath);

                    if (nfoFile.exists()) {
                        doneCount++; skippedCount++;
                        skippedPaths.add(nfoPath);
                        publish("[跳过]");
                        continue;
                    }

                    try {
                        VSmeta vsmeta = new VSmeta(vsmetaFile);
                        ObjectNode node = buildJsonFromVSmeta(vsmeta);
                        saveVsmetaImagesToTemp(vsmeta, node, tempDir, new File(basePath));
                        if (vsmeta.type == VSmeta.TypeMovie) {
                            NfoGenerator.generateMovie(nfoFile, node);
                        } else {
                            NfoGenerator.generateEpisode(nfoFile, node, vsmeta.season, vsmeta.episode);
                        }
                        doneCount++; successCount++;
                        publish("[成功]");
                    } catch (Exception e) {
                        doneCount++; failedPaths.add(vsmetaPath + " (" + e.getMessage() + ")");
                        publish("[失败] " + e.getMessage());
                    }
                }

                // 为每个季文件夹生成season.nfo
                for (java.util.Map.Entry<File, File> entry : dirFirstEpVsmeta.entrySet()) {
                    File dir = entry.getKey();
                    if (dir.equals(target)) continue; // 根目录不生成season.nfo
                    File seasonNfo = new File(dir, "season.nfo");
                    String seasonNfoPath = seasonNfo.getAbsolutePath();
                    publish(seasonNfoPath);
                    if (seasonNfo.exists()) {
                        skippedPaths.add(seasonNfoPath);
                        publish("[跳过]");
                    } else {
                        try {
                            VSmeta epVsmeta = new VSmeta(entry.getValue());
                            generateSeasonNfo(seasonNfo, epVsmeta, tempDir);
                            successCount++;
                            publish("[成功]");
                        } catch (Exception e) {
                            failedPaths.add(seasonNfoPath + " (" + e.getMessage() + ")");
                            publish("[失败] " + e.getMessage());
                        }
                    }
                }

                // 在目标根目录生成tvshow.nfo（仅一份，取自排序第一条剧集vsmeta）
                if (firstEpVsmetaFile != null) {
                    File tvshowNfo = new File(target, "tvshow.nfo");
                    String tvshowNfoPath = tvshowNfo.getAbsolutePath();
                    publish(tvshowNfoPath);
                    if (tvshowNfo.exists()) {
                        skippedPaths.add(tvshowNfoPath);
                        publish("[跳过]");
                    } else {
                        try {
                            VSmeta epVsmeta = new VSmeta(firstEpVsmetaFile);
                            generateTvshowNfo(tvshowNfo, epVsmeta, tempDir);
                            successCount++;
                            publish("[成功]");
                        } catch (Exception e) {
                            failedPaths.add(tvshowNfoPath + " (" + e.getMessage() + ")");
                            publish("[失败] " + e.getMessage());
                        }
                    }
                }

                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String msg : chunks) {
                    logArea.append(msg + "\n");
                }
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                try { get(); } catch (Exception ignored) {}
                StringBuilder summary = new StringBuilder();
                summary.append("\n===== 完成 =====\n");
                summary.append("成功: ").append(successCount)
                        .append(", 跳过: ").append(skippedCount)
                        .append(", 失败: ").append(failedPaths.size()).append("\n");
                if (!skippedPaths.isEmpty()) {
                    summary.append("\n— 跳过清单 —\n");
                    for (String p : skippedPaths) summary.append("  ").append(p).append("\n");
                }
                if (!failedPaths.isEmpty()) {
                    summary.append("\n— 失败清单 —\n");
                    for (String p : failedPaths) summary.append("  ").append(p).append("\n");
                }
                logArea.append(summary.toString());
                logArea.setCaretPosition(logArea.getDocument().getLength());
                closeBtn.setEnabled(true);
                statusLb.setText("vsmeta转nfo完成: 成功" + successCount + ", 跳过" + skippedCount + ", 失败" + failedPaths.size());
                setInputControlsEnabled(true);
                onTypeSelect();
                updateApplyButtons();
            }
        }.execute();
    }

    private void deleteNfo() {
        String targetPath = getTargetPath();
        if (targetPath.isEmpty()) { statusLb.setText("请先选择抓取目标"); return; }
        recordTargetPath(targetPath);
        File target = new File(targetPath);
        if (!target.isDirectory()) {
            JOptionPane.showMessageDialog(frame, "请选择文件夹", "递归删除nfo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        java.util.List<File> nfoFiles = new java.util.ArrayList<>();
        collectNfoFiles(target, nfoFiles);
        if (nfoFiles.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "未找到任何nfo文件", "递归删除nfo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(frame,
                "找到 " + nfoFiles.size() + " 个nfo文件，确认删除？",
                "递归删除nfo", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) return;

        int deleted = 0;
        for (File f : nfoFiles) {
            if (f.delete()) deleted++;
        }
        statusLb.setText("已删除 " + deleted + "/" + nfoFiles.size() + " 个nfo文件");
        JOptionPane.showMessageDialog(frame, "已删除 " + deleted + " 个nfo文件", "递归删除nfo", JOptionPane.INFORMATION_MESSAGE);
    }

    private void fixSeasonNfo() {
        String targetPath = getTargetPath();
        if (targetPath.isEmpty()) { statusLb.setText("请先选择抓取目标"); return; }
        File target = new File(targetPath);
        if (!target.isDirectory()) {
            JOptionPane.showMessageDialog(frame, "请选择文件夹", "vsmeta修season.nfo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        recordTargetPath(targetPath);

        // 扫描所有season.nfo
        java.util.List<File> seasonNfoFiles = new java.util.ArrayList<>();
        collectSeasonNfoFiles(target, seasonNfoFiles);
        if (seasonNfoFiles.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "未找到任何season.nfo", "vsmeta修season.nfo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(frame,
                "找到 " + seasonNfoFiles.size() + " 个season.nfo，修正lockdata和title？",
                "vsmeta修season.nfo", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) return;

        setInputControlsEnabled(false);
        statusLb.setText("vsmeta修season.nfo: 处理中...");

        new SwingWorker<String, String>() {
            private int fixed = 0;
            private int failed = 0;

            @Override
            protected String doInBackground() throws Exception {
                for (File seasonNfo : seasonNfoFiles) {
                    File dir = seasonNfo.getParentFile();
                    // 找同目录下第一条剧集vsmeta
                    VSmeta epVsmeta = null;
                    File[] files = dir.listFiles();
                    if (files != null) {
                        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                        for (File f : files) {
                            if (!f.getName().endsWith(".vsmeta")) continue;
                            try { VSmeta v = new VSmeta(f); if (v.type == VSmeta.TypeEpisode) { epVsmeta = v; break; } }
                            catch (Exception ignored) {}
                        }
                    }

                    publish(seasonNfo.getAbsolutePath());

                    try {
                        String xml = new String(java.nio.file.Files.readAllBytes(seasonNfo.toPath()),
                                java.nio.charset.StandardCharsets.UTF_8);
                        Document doc = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser());
                        boolean changed = false;

                        Element lockEl = doc.selectFirst("lockdata");
                        if (lockEl == null) {
                            doc.selectFirst("season").prependElement("lockdata").text("true");
                            changed = true;
                        } else if (!"true".equals(lockEl.text())) {
                            lockEl.text("true");
                            changed = true;
                        }

                        if (epVsmeta != null && notEmpty(epVsmeta.showTitle)) {
                            Element titleEl = doc.selectFirst("title");
                            if (titleEl == null) {
                                doc.selectFirst("season").prependElement("title").text(epVsmeta.showTitle);
                                changed = true;
                            } else if (!epVsmeta.showTitle.equals(titleEl.text())) {
                                titleEl.text(epVsmeta.showTitle);
                                changed = true;
                            }
                        }

                        if (changed) {
                            doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
                            java.io.FileOutputStream fos = new java.io.FileOutputStream(seasonNfo);
                            fos.write(doc.outerHtml().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            fos.close();
                            fixed++;
                            publish("[已修正]");
                        } else {
                            publish("[无变化]");
                        }
                    } catch (Exception e) {
                        failed++;
                        publish("[失败] " + e.getMessage());
                    }
                }
                return "修正" + fixed + "个, 失败" + failed + "个";
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String msg : chunks) statusLb.setText(msg);
            }

            @Override
            protected void done() {
                try {
                    String msg = get();
                    statusLb.setText("vsmeta修season.nfo: " + msg);
                    JOptionPane.showMessageDialog(frame, msg, "vsmeta修season.nfo", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    statusLb.setText("vsmeta修season.nfo失败: " + e.getMessage());
                } finally {
                    setInputControlsEnabled(true);
                }
            }
        }.execute();
    }

    private void collectSeasonNfoFiles(File dir, java.util.List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectSeasonNfoFiles(f, result);
            } else if (f.getName().equals("season.nfo")) {
                result.add(f);
            }
        }
    }

    private void collectNfoFiles(File dir, java.util.List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectNfoFiles(f, result);
            } else if (f.getName().endsWith(".nfo")) {
                result.add(f);
            }
        }
    }

    private void collectVsmetaFiles(File dir, java.util.List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectVsmetaFiles(f, result);
            } else if (f.getName().endsWith(".vsmeta")) {
                result.add(f);
            }
        }
    }

    private static ObjectNode buildJsonFromVSmeta(VSmeta vsmeta) {
        ObjectNode node = Utils.ObjectMapper.createObjectNode();
        if (vsmeta.type == VSmeta.TypeEpisode) {
            node.put("title", orEmpty(vsmeta.episodeTitle));
        } else {
            node.put("title", orEmpty(vsmeta.showTitle));
        }
        node.put("tagline", orEmpty(vsmeta.episodeTitle));
        node.put("summary", orEmpty(vsmeta.chapterSummary));
        node.put("original_available", orEmpty(vsmeta.episodeReleaseDate));
        node.put("certificate", orEmpty(vsmeta.classification));

        ArrayNode genreArr = Utils.ObjectMapper.createArrayNode();
        if (vsmeta.genres != null) for (String g : vsmeta.genres) genreArr.add(g);
        node.set("genre", genreArr);

        ArrayNode actorArr = Utils.ObjectMapper.createArrayNode();
        if (vsmeta.casts != null) for (String a : vsmeta.casts) {
            ObjectNode ao = Utils.ObjectMapper.createObjectNode();
            ao.put("name", a);
            actorArr.add(ao);
        }
        node.set("actor", actorArr);

        ArrayNode dirArr = Utils.ObjectMapper.createArrayNode();
        if (vsmeta.directors != null) for (String d : vsmeta.directors) dirArr.add(d);
        node.set("director", dirArr);

        ArrayNode writerArr = Utils.ObjectMapper.createArrayNode();
        if (vsmeta.writers != null) for (String w : vsmeta.writers) writerArr.add(w);
        node.set("writer", writerArr);

        if (vsmeta.season > 0) node.put("season", vsmeta.season);
        if (vsmeta.episode > 0) node.put("episode", vsmeta.episode);

        return node;
    }

    private static void saveVsmetaImagesToTemp(VSmeta vsmeta,
            ObjectNode node, File tempDir, File videoFile) {
        String posterB64;
        if (vsmeta.type == VSmeta.TypeEpisode) {
            posterB64 = vsmeta.episodeThumbData != null ? vsmeta.episodeThumbData.trim() : null;
            // 没有缩略图时，用 FFmpeg 现场截图
            if ((posterB64 == null || posterB64.isEmpty()) && videoFile.exists()) {
                try {
                    MediaInfo mi = FFmpegUtils.mediaInfo(videoFile);
                    long pos = (long)(mi.format.parseDuration() * 0.618);
                    File snap = new File(tempDir, "v2n_snap_" + System.currentTimeMillis() + ".jpg");
                    FFmpegUtils.snapshot(videoFile, FFmpegUtils.formatDuration(pos), snap);
                    if (snap.exists() && snap.length() > 0) {
                        posterB64 = VSmeta.readImgData(snap);
                        snap.delete();
                    }
                } catch (Exception e) {
                    System.err.println("[vsmeta转nfo] FFmpeg截图失败: " + videoFile.getAbsolutePath() + " — " + e.getMessage());
                }
            }
            // FFmpeg未能生成时，用posterData兜底
            if (posterB64 == null || posterB64.isEmpty()) {
                posterB64 = vsmeta.posterData != null ? vsmeta.posterData.trim() : null;
            }
        } else {
            posterB64 = vsmeta.posterData != null ? vsmeta.posterData.trim() : null;
        }
        String backdropB64 = vsmeta.backdropData != null ? vsmeta.backdropData.trim() : null;

        boolean hasRating = vsmeta.rating != null && vsmeta.rating.length == 1
                && !java.util.Arrays.equals(vsmeta.rating, VSmeta.RatingRaw);
        if (posterB64 == null && backdropB64 == null && !hasRating) return;

        ArrayNode extraArr = Utils.ObjectMapper.createArrayNode();
        ObjectNode extraSite = Utils.ObjectMapper.createObjectNode();

        if (hasRating) {
            double score = (vsmeta.rating[0] & 0xFF) / 10.0;
            ObjectNode ratingObj = Utils.ObjectMapper.createObjectNode();
            ratingObj.put("score", score);
            extraSite.set("rating", ratingObj);
        }
        if (posterB64 != null && !posterB64.isEmpty()) {
            File f = saveBase64Image(posterB64, new File(tempDir, "v2n_poster_" + System.currentTimeMillis() + ".jpg"));
            if (f != null) {
                ArrayNode pa = Utils.ObjectMapper.createArrayNode();
                pa.add(f.getAbsolutePath());
                extraSite.set("poster", pa);
            }
        }
        if (backdropB64 != null && !backdropB64.isEmpty()) {
            File f = saveBase64Image(backdropB64, new File(tempDir, "v2n_backdrop_" + System.currentTimeMillis() + ".jpg"));
            if (f != null) {
                ArrayNode ba = Utils.ObjectMapper.createArrayNode();
                ba.add(f.getAbsolutePath());
                extraSite.set("backdrop", ba);
            }
        }

        extraArr.add(extraSite);
        node.set("extra", extraArr);
    }

    private static void generateTvshowNfo(File nfoFile, VSmeta vsmeta, File tempDir) throws Exception {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<tvshow>\n");
        sb.append("  <lockdata>true</lockdata>\n");
        String title = extractTitleFromFolder(nfoFile.getParentFile());
        if (notEmpty(title)) sb.append("  <title>").append(escXml(title)).append("</title>\n");
        if (notEmpty(vsmeta.tvshowSummary)) sb.append("  <plot>").append(escXml(vsmeta.tvshowSummary)).append("</plot>\n");
        if (vsmeta.tvShowYear > 0) sb.append("  <year>").append(vsmeta.tvShowYear).append("</year>\n");
        if (notEmpty(vsmeta.releaseDateTvShow)) sb.append("  <premiered>").append(escXml(vsmeta.releaseDateTvShow)).append("</premiered>\n");
        if (vsmeta.genres != null) for (String g : vsmeta.genres) sb.append("  <genre>").append(escXml(g)).append("</genre>\n");
        if (vsmeta.casts != null) for (String c : vsmeta.casts) sb.append("  <actor><name>").append(escXml(c)).append("</name></actor>\n");
        if (vsmeta.directors != null) for (String d : vsmeta.directors) sb.append("  <director>").append(escXml(d)).append("</director>\n");
        if (vsmeta.writers != null) for (String w : vsmeta.writers) sb.append("  <credits>").append(escXml(w)).append("</credits>\n");

        // 海报/背景图
        File dir = nfoFile.getParentFile();
        boolean hasArt = false;
        StringBuilder artXml = new StringBuilder();
        String posterB64 = vsmeta.posterData != null ? vsmeta.posterData.trim() : null;
        if (posterB64 != null && !posterB64.isEmpty()) {
            File f = saveBase64Image(posterB64, new File(tempDir, "v2n_tvthumb_" + System.currentTimeMillis() + ".jpg"));
            if (f != null) {
                String dest = copyImageFile(f, "folder", dir);
                if (dest != null) {
                    sb.append("  <thumb>").append(escXml(dest)).append("</thumb>\n");
                    artXml.append("    <poster>").append(escXml(dest)).append("</poster>\n");
                    hasArt = true;
                }
            }
        }
        String backdropB64 = vsmeta.backdropData != null ? vsmeta.backdropData.trim() : null;
        if (backdropB64 != null && !backdropB64.isEmpty()) {
            File f = saveBase64Image(backdropB64, new File(tempDir, "v2n_tvfanart_" + System.currentTimeMillis() + ".jpg"));
            if (f != null) {
                String dest = copyImageFile(f, "backdrop", dir);
                if (dest != null) {
                    sb.append("  <fanart>").append(escXml(dest)).append("</fanart>\n");
                    artXml.append("    <fanart>").append(escXml(dest)).append("</fanart>\n");
                    hasArt = true;
                }
            }
        }
        if (hasArt) sb.append("  <art>\n").append(artXml).append("  </art>\n");

        sb.append("</tvshow>\n");
        java.io.FileOutputStream fos = new java.io.FileOutputStream(nfoFile);
        fos.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        fos.close();
    }

    /** 从文件夹名智能提取节目名：去掉排序前缀字母和年份后缀 */
    private static String extractTitleFromFolder(File dir) {
        String name = dir.getName();
        // 去掉前导单字母前缀（如 "B 冰火魔厨 (2021)" → "冰火魔厨 (2021)"）
        name = name.replaceFirst("^[A-Za-z]\\s+", "");
        // 去掉末尾括号年份（如 "冰火魔厨 (2021)" → "冰火魔厨"）
        name = name.replaceFirst("\\s*\\(\\d{4}\\)\\s*$", "");
        return name.trim();
    }

    private static void generateSeasonNfo(File nfoFile, VSmeta vsmeta, File tempDir) throws Exception {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<season>\n");
        sb.append("  <lockdata>true</lockdata>\n");
        if (notEmpty(vsmeta.showTitle)) sb.append("  <title>").append(escXml(vsmeta.showTitle)).append("</title>\n");
        String seasonPlot = notEmpty(vsmeta.tvshowSummary) ? vsmeta.tvshowSummary : vsmeta.chapterSummary;
        if (notEmpty(seasonPlot)) sb.append("  <plot>").append(escXml(seasonPlot)).append("</plot>\n");
        if (vsmeta.season > 0) sb.append("  <seasonnumber>").append(vsmeta.season).append("</seasonnumber>\n");
        if (notEmpty(vsmeta.episodeReleaseDate)) sb.append("  <premiered>").append(escXml(vsmeta.episodeReleaseDate)).append("</premiered>\n");
        if (vsmeta.year > 0) sb.append("  <year>").append(vsmeta.year).append("</year>\n");

        // 海报
        File dir = nfoFile.getParentFile();
        String posterB64 = vsmeta.posterData != null ? vsmeta.posterData.trim() : null;
        if (posterB64 != null && !posterB64.isEmpty()) {
            File f = saveBase64Image(posterB64, new File(tempDir, "v2n_season_poster_" + System.currentTimeMillis() + ".jpg"));
            if (f != null) {
                String dest = copyImageFile(f, "folder", dir);
                if (dest != null) {
                    sb.append("  <thumb>").append(escXml(dest)).append("</thumb>\n");
                    sb.append("  <art>\n    <poster>").append(escXml(dest)).append("</poster>\n  </art>\n");
                }
            }
        }

        sb.append("</season>\n");
        java.io.FileOutputStream fos = new java.io.FileOutputStream(nfoFile);
        fos.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        fos.close();
    }

    private static String copyImageFile(File src, String baseName, File dir) {
        try {
            String ext = src.getName().substring(src.getName().lastIndexOf('.') + 1);
            File dest = new File(dir, baseName + "." + ext);
            java.nio.file.Files.copy(src.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return dest.getName();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    private static String escXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static File saveBase64Image(String base64, File dest) {
        try {
            byte[] data = Base64.getDecoder().decode(base64.replace("\n", "").replace("\r", ""));
            java.io.FileOutputStream fos = new java.io.FileOutputStream(dest);
            fos.write(data);
            fos.close();
            return dest;
        } catch (Exception e) {
            return null;
        }
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private int addSettingRow(JPanel panel, GridBagConstraints gbc, int row, String label, JTextField tf) {
        gbc.gridx = 0; gbc.gridy = row;
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label + ":"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(tf, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        JButton btn = new JButton("设置");
        btn.addActionListener(e -> setProperty(label, tf));
        settingButtons.add(btn);
        panel.add(btn, gbc);
        return row + 1;
    }

    private void onTargetSiteSelect() {
        TargetSite ts = findTargetSite();
        if (ts == null) return;

        typeCb.removeAllItems();
        for (String t : ts.searchTypes) typeCb.addItem(t);
        typeCb.setSelectedItem(ts.searchTypes.get(0));

        langCb.removeAllItems();
        for (String l : ts.languages) langCb.addItem(l);
        langCb.setSelectedItem(ts.languages.get(0));

        onTypeSelect();
    }

    private void onTypeSelect() {
        String type = (String) typeCb.getSelectedItem();
        boolean isEpisode = "tvshow_episode".equals(type);
        seasonTf.setEnabled(isEpisode);
        episodeTf.setEnabled(isEpisode);
        if (isEpisode) {
            if (seasonTf.getText().isEmpty()) seasonTf.setText("1");
            if (episodeTf.getText().isEmpty()) episodeTf.setText("1");
        } else {
            seasonTf.setText("");
            episodeTf.setText("");
        }
    }

    private void search() {
        String title = titleTf.getText();
        if (title == null || title.trim().isEmpty()) {
            resultRawTa.setText("关键词不能为空");
            return;
        }

        TargetSite ts = findTargetSite();
        if (ts == null) return;

        String targetPath = getTargetPath();
        if (!targetPath.isEmpty()) recordTargetPath(targetPath);

        String type = (String) typeCb.getSelectedItem();
        String lang = (String) langCb.getSelectedItem();
        int limit;
        try { limit = Integer.parseInt(limitTf.getText().trim()); } catch (NumberFormatException e) { limit = 1; }

        Args.Input input = new Args.Input();
        input.title = title.trim();
        if ("tvshow_episode".equals(type)) {
            try { input.season = Integer.parseInt(seasonTf.getText().trim()); } catch (NumberFormatException e) {}
            try { input.episode = Integer.parseInt(episodeTf.getText().trim()); } catch (NumberFormatException e) {}
        }

        String body;
        try {
            body = "--type " + type + " --lang " + lang + " --input " +
                Utils.ObjectMapper.writeValueAsString(input) + " --limit " + limit;
        } catch (Exception e) {
            resultRawTa.setText("序列化失败: " + e.getMessage());
            return;
        }
        Args args;
        try {
            args = Args.parse(body);
        } catch (Exception e) {
            resultRawTa.setText("参数解析失败: " + e.getMessage());
            return;
        }

        resultTitleListModel.clear();
        resultDetailPanel.removeAll();
        resultDetailPanel.revalidate();
        resultDetailPanel.repaint();
        resultRawTa.setText("搜索中...");
        statusLb.setText("搜索中...");
        searchBtn.setEnabled(false);
        setInputControlsEnabled(false);
        Color disabledBg = new Color(230, 230, 230);
        resultTitleList.setBackground(disabledBg);
        resultDetailPanel.setBackground(disabledBg);
        resultRawTa.setBackground(disabledBg);

        new SwingWorker<Rst, Void>() {
            @Override
            protected Rst doInBackground() throws Exception {
                return searchOrchestrator.search(ts, args);
            }

            @Override
            protected void done() {
                try {
                    Rst rst = get();
                    lastResults = new ArrayList<>(rst.result);
                    resultTitleListModel.clear();
                    for (Object item : lastResults) {
                        JsonNode node = Utils.ObjectMapper.valueToTree(item);
                        JsonNode titleNode = node.get("title");
                        resultTitleListModel.addElement(titleNode != null ? titleNode.asText() : "(无标题)");
                    }
                    resultRawTa.setText(Utils.ObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rst));
                    statusLb.setText("搜索完成");
                    if (!lastResults.isEmpty()) resultTitleList.setSelectedIndex(0);
                    updateApplyButtons();
                } catch (Exception e) {
                    resultRawTa.setText("搜索失败: " + e.getMessage());
                    statusLb.setText("搜索失败");
                } finally {
                    searchBtn.setEnabled(true);
                    setInputControlsEnabled(true);
                    onTypeSelect();
                    updateApplyButtons();
                    resultTitleList.setBackground(resultListBg);
                    resultDetailPanel.setBackground(resultDetailBg);
                    resultRawTa.setBackground(resultRawBg);
                }
            }
        }.execute();
    }

    private void onResultTitleSelect() {
        int idx = resultTitleList.getSelectedIndex();
        if (idx < 0 || idx >= lastResults.size()) {
            resultDetailPanel.removeAll();
            resultDetailPanel.revalidate();
            resultDetailPanel.repaint();
            updateApplyButtons();
            return;
        }
        try {
            JsonNode node = Utils.ObjectMapper.valueToTree(lastResults.get(idx));
            java.util.List<java.awt.Component> rows = new java.util.ArrayList<>();
            addDetailRow(rows, "标题", text(node.get("title")));
            addDetailRow(rows, "标语", text(node.get("tagline")));
            String date = text(node.get("original_available"));
            if (!date.isEmpty()) {
                addDetailRow(rows, "发布日期", date);
                if (date.length() >= 4) addDetailRow(rows, "年份", date.substring(0, 4));
            }
            addDetailRow(rows, "简介", text(node.get("summary")));
            addDetailRow(rows, "分级", text(node.get("certificate")));
            addDetailRow(rows, "类型", arr2str(node.get("genre")));
            addDetailRow(rows, "演员", arr2str(node.get("actor")));
            addDetailRow(rows, "导演", arr2str(node.get("director")));
            addDetailRow(rows, "编剧", arr2str(node.get("writer")));
            JsonNode seasonN = node.get("season");
            if (seasonN != null && !seasonN.isNull()) addDetailRow(rows, "季", seasonN.asText());
            JsonNode episodeN = node.get("episode");
            if (episodeN != null && !episodeN.isNull()) addDetailRow(rows, "集", episodeN.asText());

            JsonNode extra = node.get("extra");
            if (extra != null) {
                for (JsonNode siteNode : extra) {
                    JsonNode rating = siteNode.get("rating");
                    if (rating == null) continue;
                    for (JsonNode val : rating) {
                        if (val.isNumber()) {
                            addDetailRow(rows, "评分", val.asText());
                            break;
                        }
                    }
                }
                java.util.List<Image> posters = new java.util.ArrayList<>();
                java.util.List<Image> backdrops = new java.util.ArrayList<>();
                for (String path : collectExtraPaths(extra, "poster")) {
                    Image img = loadImage(new File(path));
                    if (img != null) posters.add(img);
                }
                for (String path : collectExtraPaths(extra, "backdrop")) {
                    Image img = loadImage(new File(path));
                    if (img != null) backdrops.add(img);
                }
                addDetailImageGroup(rows, "海报", posters);
                addDetailImageGroup(rows, "背景", backdrops);
            }

            resultDetailPanel.removeAll();
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0; c.gridy = 0; c.weightx = 1.0; c.weighty = 1.0;
            c.anchor = GridBagConstraints.NORTHWEST; c.fill = GridBagConstraints.HORIZONTAL;
            resultDetailPanel.add(assembleDetailPanel(rows), c);
        } catch (Exception e) {
            resultDetailPanel.removeAll();
            resultDetailPanel.add(new JLabel("详情构建失败: " + e.getMessage()));
        }
        resultDetailPanel.revalidate();
        resultDetailPanel.repaint();
        updateApplyButtons();
    }

    private void cleanCache() {
        statusLb.setText("清理缓存中...");
        new SwingWorker<Long, Void>() {
            @Override
            protected Long doInBackground() throws Exception {
                return fileCacher.clean();
            }

            @Override
            protected void done() {
                try {
                    long size = get();
                    String sz = formatSize(size);
                    JOptionPane.showMessageDialog(frame,
                        "成功清理 " + sz + " 缓存", "清空缓存", JOptionPane.INFORMATION_MESSAGE);
                    statusLb.setText("就绪");
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(frame,
                        "清理失败: " + e.getMessage(), "清空缓存", JOptionPane.ERROR_MESSAGE);
                    statusLb.setText("清理失败");
                }
            }
        }.execute();
    }

    private File resolveInitialDir(String path) {
        if (path.isEmpty()) return new File("E:\\Downloads");
        File dir = new File(path);
        if (dir.exists()) return dir.isDirectory() ? dir : dir.getParentFile();
        while (dir != null && !dir.exists()) dir = dir.getParentFile();
        return dir != null ? dir : new File("E:\\Downloads");
    }

    private void openDoubanLogin() {
        DoubanLoginDialog dialog = new DoubanLoginDialog(frame);
        dialog.setVisible(true);
    }

    private void openMetaViewer() {
        String targetPath = getTargetPath();
        if (!targetPath.isEmpty()) recordTargetPath(targetPath);
        MetaViewerDialog dialog = new MetaViewerDialog(frame, resolveInitialDir(targetPath));
        dialog.setVisible(true);
    }

    private void openFilePicker() {
        File initialDir = resolveInitialDir(getTargetPath());
        FilePickerDialog dialog = new FilePickerDialog(frame, initialDir);
        dialog.setVisible(true);
        FilePickerDialog.DialogResult result = dialog.getResult();
        if (result == null) return;
        setTargetPath(result.path);
        recordTargetPath(result.path);
        titleTf.setText(stripExtension(result.name));
        updateApplyButtons();
    }

    private String getTargetPath() {
        Object sel = targetPathModel.getSelectedItem();
        return sel != null ? sel.toString().trim() : "";
    }

    private void setTargetPath(String path) {
        targetPathCb.setSelectedItem(path);
    }

    private static final int MAX_RECENT_TARGETS = 20;

    private void recordTargetPath(String path) {
        if (path == null || (path = path.trim()).isEmpty()) return;
        // 去重
        for (int i = 0; i < targetPathModel.getSize(); i++) {
            if (path.equals(targetPathModel.getElementAt(i))) {
                targetPathModel.removeElementAt(i);
                break;
            }
        }
        targetPathModel.insertElementAt(path, 0);
        targetPathCb.setSelectedItem(path);
        while (targetPathModel.getSize() > MAX_RECENT_TARGETS)
            targetPathModel.removeElementAt(targetPathModel.getSize() - 1);
        saveRecentTargets();
    }

    private void loadRecentTargets() {
        try {
            String json = configService.gets(java.util.Collections.singletonList("recentTargets")).get("recentTargets");
            if (json != null && !json.isEmpty()) {
                com.fasterxml.jackson.databind.JsonNode arr = Utils.ObjectMapper.readTree(json);
                for (com.fasterxml.jackson.databind.JsonNode item : arr) {
                    targetPathModel.addElement(item.asText());
                }
            }
        } catch (Exception ignored) {}
    }

    private void saveRecentTargets() {
        try {
            com.fasterxml.jackson.databind.node.ArrayNode arr = Utils.ObjectMapper.createArrayNode();
            for (int i = 0; i < targetPathModel.getSize(); i++)
                arr.add(targetPathModel.getElementAt(i));
            configService.set("recentTargets", Utils.ObjectMapper.writeValueAsString(arr));
        } catch (Exception ignored) {}
    }

    private void updateApplyButtons() {
        boolean enable = !getTargetPath().isEmpty()
            && resultTitleList.getSelectedIndex() >= 0;
        genVsmetaBtn.setEnabled(enable);
        genNfoBtn.setEnabled(enable);
        boolean isMovie = "movie".equals(typeCb.getSelectedItem());
        renameBtn.setEnabled(enable && isMovie);
    }

    private void setInputControlsEnabled(boolean enabled) {
        searchBtn.setEnabled(enabled);
        targetPathCb.setEnabled(enabled);
        pickerBtn.setEnabled(enabled);
        targetSiteCb.setEnabled(enabled);
        typeCb.setEnabled(enabled);
        langCb.setEnabled(enabled);
        titleTf.setEnabled(enabled);
        seasonTf.setEnabled(enabled);
        episodeTf.setEnabled(enabled);
        limitTf.setEnabled(enabled);
        resultTitleList.setEnabled(enabled);
        resultDetailPanel.setEnabled(enabled);
        resultRawTa.setEnabled(enabled);
        genVsmetaBtn.setEnabled(enabled);
        genNfoBtn.setEnabled(enabled);
        renameBtn.setEnabled(enabled);
        webDriverAddrTf.setEnabled(enabled);
        javdbOriginTf.setEnabled(enabled);
        fileEpOffsetTf.setEnabled(enabled);
        siteEpOffsetTf.setEnabled(enabled);
        originalAvailableTf.setEnabled(enabled);
        doubanMaxLimitTf.setEnabled(enabled);
        baikeBaiduMaxLimitTf.setEnabled(enabled);
        cleanCacheBtn.setEnabled(enabled);
        doubanLoginBtn.setEnabled(enabled);
        for (JButton btn : settingButtons) btn.setEnabled(enabled);
    }

    // ==================== 搜索结果详情格式化展示 ====================

    private static Font getContentFont() {
        Font cjk = Spider4VideoStationApp.getCJKFont();
        if (cjk == null) return null;
        return cjk.deriveFont((float) new JLabel().getFont().getSize());
    }

    private void applyCJKFontToContent() {
        Font f = getContentFont();
        if (f == null) return;
        resultTitleList.setFont(f);
        resultRawTa.setFont(f);
        targetPathCb.getEditor().getEditorComponent().setFont(f);
        titleTf.setFont(f);
    }

    private static void addDetailRow(java.util.List<java.awt.Component> rows, String label, String value) {
        if (value == null || value.isEmpty()) return;
        rows.add(new JLabel(label + ":"));
        JTextArea ta = new JTextArea(value);
        ta.setEditable(false);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setBackground(null);
        Font cjk = Spider4VideoStationApp.getCJKFont();
        ta.setFont(cjk != null ? cjk.deriveFont((float)new JLabel().getFont().getSize()) : ta.getFont());
        rows.add(ta);
    }

    private static void addDetailImageRow(java.util.List<java.awt.Component> rows, String label, Image img) {
        if (img == null) return;
        rows.add(new JLabel(label + ":"));
        rows.add(new JLabel(new ImageIcon(img)));
    }

    private static void addDetailImageGroup(java.util.List<java.awt.Component> rows, String label, java.util.List<Image> imgs) {
        if (imgs.isEmpty()) return;
        rows.add(new JLabel(label + ":"));
        JPanel flow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0) {
            @Override
            public void layoutContainer(java.awt.Container target) {
                super.layoutContainer(target);
                synchronized (target.getTreeLock()) {
                    for (int i = 0, n = target.getComponentCount(); i < n; i++) {
                        java.awt.Component c = target.getComponent(i);
                        c.setLocation(c.getX(), 0);
                    }
                }
            }
        });
        for (Image img : imgs) flow.add(new JLabel(new ImageIcon(img)));
        rows.add(flow);
    }

    private static JPanel assembleDetailPanel(java.util.List<java.awt.Component> rows) {
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

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText();
    }

    private static String arr2str(JsonNode arr) {
        if (arr == null || !arr.isArray()) return null;
        java.util.List<String> items = new java.util.ArrayList<>();
        for (JsonNode item : arr) items.add(item.asText());
        return items.isEmpty() ? null : String.join(", ", items);
    }

    private static java.util.List<String> collectExtraPaths(JsonNode extra, String field) {
        java.util.List<String> paths = new java.util.ArrayList<>();
        collectArr(extra, field, paths);
        for (JsonNode siteNode : extra) {
            JsonNode tvshow = siteNode.get("tvshow");
            if (tvshow == null) continue;
            JsonNode tvExtra = tvshow.get("extra");
            if (tvExtra != null) collectArr(tvExtra, field, paths);
        }
        return paths;
    }
    private static void collectArr(JsonNode container, String field, java.util.List<String> paths) {
        if (container == null) return;
        for (JsonNode siteNode : container) {
            JsonNode arr = siteNode.get(field);
            if (arr != null && arr.isArray()) for (JsonNode item : arr) paths.add(item.asText());
        }
    }

    private static Image loadImage(File file) {
        try {
            if (!file.exists()) return null;
            BufferedImage img = ImageIO.read(file);
            if (img == null) return null;
            if (img.getWidth() > 200) {
                int h = img.getHeight() * 200 / img.getWidth();
                return img.getScaledInstance(200, h, Image.SCALE_SMOOTH);
            }
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    private void generateMetadata(boolean vsmeta) {
        String targetPath = getTargetPath();
        File target = new File(targetPath);
        int selectedIdx = resultTitleList.getSelectedIndex();
        if (selectedIdx < 0 || selectedIdx >= lastResults.size()) return;

        String type = (String) typeCb.getSelectedItem();
        setInputControlsEnabled(false);
        statusLb.setText(vsmeta ? "生成vsmeta中..." : "生成nfo中...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                if (target.isDirectory() && "tvshow".equals(type)) {
                    String keyword = titleTf.getText().trim();
                    String showTitle;
                    if (Utils.isUrl(keyword)) {
                        showTitle = keyword;
                    } else {
                        JsonNode selNode = Utils.ObjectMapper.valueToTree(lastResults.get(selectedIdx));
                        showTitle = selNode.get("title").asText();
                    }
                    String lang = (String) langCb.getSelectedItem();
                    TargetSite ts = findTargetSite();
                    if (ts == null) return "抓取目标网站未选择";
                    int count = metadataGenerator.generateBatch(target, showTitle, ts, lang, vsmeta, !vsmeta,
                        msg -> SwingUtilities.invokeLater(() -> statusLb.setText(msg)));
                    return "批量生成完成，共 " + count + " 个";
                }
                Object result = lastResults.get(selectedIdx);
                if (vsmeta) metadataGenerator.generateVsmeta(target, result, null, 0, 0);
                else metadataGenerator.generateNfo(target, result);
                return vsmeta ? "vsmeta生成完成" : "nfo生成完成";
            }

            @Override
            protected void done() {
                try {
                    String msg = get();
                    statusLb.setText(msg);
                    if (msg.startsWith("批量生成完成"))
                        JOptionPane.showMessageDialog(frame, msg, "提示", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    statusLb.setText("生成失败: " + e.getMessage());
                } finally {
                    setInputControlsEnabled(true);
                    onTypeSelect();
                    updateApplyButtons();
                }
            }
        }.execute();
    }

    private void loadSettings() {
        try {
            Map<String, String> props = configService.gets(SETTING_KEYS);
            setIfNotNull(webDriverAddrTf, props.get("webDriverAddr"));
            setIfNotNull(javdbOriginTf, props.get("javdbOrigin"));
            setIfNotNull(fileEpOffsetTf, props.get("fileEpOffset"));
            setIfNotNull(siteEpOffsetTf, props.get("siteEpOffset"));
            setIfNotNull(originalAvailableTf, props.get("originalAvailable"));
            setIfNotNull(doubanMaxLimitTf, props.get("doubanMaxLimit"));
            setIfNotNull(baikeBaiduMaxLimitTf, props.get("baikeBaiduMaxLimit"));
        } catch (Exception e) {
            statusLb.setText("加载设置失败: " + e.getMessage());
        }
    }

    private void setProperty(String label, JTextField tf) {
        String key = labelToKey(label);
        String val = tf.getText();
        try {
            configService.set(key, val);
            JOptionPane.showMessageDialog(frame, "设置成功", "设置", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "设置失败: " + e.getMessage(), "设置", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String labelToKey(String label) {
        switch (label) {
        case "WebDriver地址": return "webDriverAddr";
        case "Javdb地址": return "javdbOrigin";
        case "视频集号偏移量": return "fileEpOffset";
        case "源站集号偏移量": return "siteEpOffset";
        case "强制发布日期": return "originalAvailable";
        case "豆瓣结果条数": return "doubanMaxLimit";
        case "百度百科结果条数": return "baikeBaiduMaxLimit";
        default: return "";
        }
    }

    private TargetSite findTargetSite() {
        String name = (String) targetSiteCb.getSelectedItem();
        return TargetSite.find(name);
    }

    private void setIfNotNull(JTextField tf, String val) {
        if (val != null) tf.setText(val);
    }

    private static String formatSize(long size) {
        if (size < 1024) return size + "B";
        double sz = size / 1024.0;
        if (sz < 1024) return String.format("%.2fK", sz);
        sz /= 1024;
        if (sz < 1024) return String.format("%.2fM", sz);
        sz /= 1024;
        return String.format("%.2fG", sz);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot > 0) return name.substring(0, dot);
        return name;
    }

    // ==================== 标准重命名 ====================

    private static String legalName(String name) {
        return name.replaceAll("[\t]", " ").replaceAll("[/]", "／")
            .replaceAll("[\\\\]", "＼").replaceAll("[:꞉]", "：")
            .replaceAll("[\\*]", "＊").replaceAll("[\\?]", "？")
            .replaceAll("[\\\"]", "＂").replaceAll("[<]", "＜")
            .replaceAll("[>]", "＞").replaceAll("[|]", "｜")
            .replaceAll("[!]", "！").replaceAll("[・]", "·")
            .replaceAll("[･]", "·").replaceAll("[｢]", "「")
            .replaceAll("[｣]", "」").replaceAll("[♪]", "")
            .replaceAll("[〜]", "～").replaceAll("[♭]", "b");
    }

    private void renameStandard() {
        int selectedIdx = resultTitleList.getSelectedIndex();
        if (selectedIdx < 0 || selectedIdx >= lastResults.size()) return;
        String targetPath = getTargetPath();
        File target = new File(targetPath);
        if (!target.isFile()) {
            statusLb.setText("请选择视频文件");
            return;
        }

        JsonNode node = Utils.ObjectMapper.valueToTree(lastResults.get(selectedIdx));
        String title = node.get("title").asText();
        String date = node.has("original_available") ? node.get("original_available").asText() : "";
        String year = date.length() >= 4 ? date.substring(0, 4) : "";

        String ext = target.getName().substring(target.getName().lastIndexOf('.'));
        String newName = legalName(title);
        if (!year.isEmpty()) newName += " (" + year + ")";
        newName += ext;
        File newFile = new File(target.getParentFile(), newName);

        if (newFile.exists() && !newFile.equals(target)) {
            statusLb.setText("目标文件已存在: " + newName);
            return;
        }

        // 重命名对应的vsmeta文件
        File oldVsmeta = new File(target.getParentFile(), target.getName() + ".vsmeta");
        File newVsmeta = new File(target.getParentFile(), newFile.getName() + ".vsmeta");
        if (oldVsmeta.exists()) {
            if (newVsmeta.exists() && !newVsmeta.equals(oldVsmeta)) {
                statusLb.setText("目标vsmeta文件已存在: " + newVsmeta.getName());
                return;
            }
            if (!oldVsmeta.renameTo(newVsmeta))
                System.err.println("vsmeta重命名失败: " + oldVsmeta.getAbsolutePath());
        }

        if (target.renameTo(newFile)) {
            setTargetPath(newFile.getAbsolutePath());
            statusLb.setText("已重命名为 " + newFile.getName());
        } else {
            statusLb.setText("重命名失败");
        }
    }
}