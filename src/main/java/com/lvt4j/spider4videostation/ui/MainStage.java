package com.lvt4j.spider4videostation.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
import javax.swing.border.TitledBorder;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.lvt4j.spider4videostation.TargetSite;
import com.lvt4j.spider4videostation.Utils;
import com.lvt4j.spider4videostation.pojo.Args;
import com.lvt4j.spider4videostation.pojo.Rst;
import com.lvt4j.spider4videostation.metadata.MetadataGenerator;
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
    private JTextArea resultDetailTa;
    private JTextArea resultRawTa;
    private DefaultListModel<String> resultTitleListModel;
    private JList<String> resultTitleList;
    private List<Object> lastResults = new ArrayList<>();
    private JLabel statusLb;
    private JButton searchBtn;
    private JButton genVsmetaBtn;
    private JButton genNfoBtn;
    private JTextField targetPathTf;

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
        frame.setVisible(true);

        loadSettings();
    }

    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JPanel pickerPanel = new JPanel(new BorderLayout(5, 0));
        pickerPanel.add(new JLabel("抓取目标:"), BorderLayout.WEST);
        targetPathTf = new JTextField();
        pickerPanel.add(targetPathTf, BorderLayout.CENTER);
        JButton pickerBtn = new JButton("...");
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

        resultDetailTa = new JTextArea(20, 35);
        resultDetailTa.setEditable(false);
        JScrollPane detailScroll = new JScrollPane(resultDetailTa);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, titleScroll, detailScroll);
        splitPane.setResizeWeight(0.3);

        // Tab 2: 原始 JSON
        resultRawTa = new JTextArea(20, 45);
        resultRawTa.setEditable(false);
        JScrollPane rawScroll = new JScrollPane(resultRawTa);

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
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("系统设置"));
        panel.setPreferredSize(new Dimension(450, 0));

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
        JButton cleanCacheBtn = new JButton("清空缓存");
        cleanCacheBtn.addActionListener(e -> cleanCache());
        JButton doubanLoginBtn = new JButton("豆瓣登录");
        doubanLoginBtn.addActionListener(e -> openDoubanLogin());
        btnPanel.add(cleanCacheBtn);
        btnPanel.add(doubanLoginBtn);
        JButton metaViewerBtn = new JButton("查看元数据文件");
        metaViewerBtn.addActionListener(e -> openMetaViewer());
        btnPanel.add(metaViewerBtn);
        panel.add(btnPanel, gbc);

        gbc.gridx = 0; gbc.gridy = row + 1; gbc.gridwidth = 3; gbc.weightx = 0; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.VERTICAL;
        panel.add(new JLabel(), gbc);

        return panel;
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
        resultDetailTa.setText("");
        resultRawTa.setText("搜索中...");
        statusLb.setText("搜索中...");
        searchBtn.setEnabled(false);

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
                }
            }
        }.execute();
    }

    private void onResultTitleSelect() {
        int idx = resultTitleList.getSelectedIndex();
        if (idx < 0 || idx >= lastResults.size()) {
            resultDetailTa.setText("");
            updateApplyButtons();
            return;
        }
        try {
            String json = Utils.ObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(lastResults.get(idx));
            resultDetailTa.setText(json);
        } catch (Exception e) {
            resultDetailTa.setText("序列化失败: " + e.getMessage());
        }
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

    private void openDoubanLogin() {
        DoubanLoginDialog dialog = new DoubanLoginDialog(frame);
        dialog.setVisible(true);
    }

    private void openMetaViewer() {
        String current = targetPathTf.getText().trim();
        File initialDir;
        if (current.isEmpty()) {
            initialDir = new File("E:\\Downloads");
        } else {
            initialDir = new File(current);
        }
        if (!initialDir.exists()) initialDir = new File("E:\\Downloads");

        MetaViewerDialog dialog = new MetaViewerDialog(frame, initialDir);
        dialog.setVisible(true);
    }

    private void openFilePicker() {
        String current = targetPathTf.getText().trim();
        File initialDir;
        if (current.isEmpty()) {
            initialDir = new File("E:\\Downloads");
        } else {
            initialDir = new File(current);
        }
        if (!initialDir.exists()) initialDir = new File("E:\\Downloads");

        FilePickerDialog dialog = new FilePickerDialog(frame, initialDir);
        dialog.setVisible(true);
        FilePickerDialog.DialogResult result = dialog.getResult();
        if (result == null) return;
        targetPathTf.setText(result.path);
        titleTf.setText(stripExtension(result.name));
        updateApplyButtons();
    }

    private void updateApplyButtons() {
        boolean enable = !targetPathTf.getText().trim().isEmpty()
            && resultTitleList.getSelectedIndex() >= 0;
        genVsmetaBtn.setEnabled(enable);
        genNfoBtn.setEnabled(enable);
    }

    private void generateMetadata(boolean vsmeta) {
        String targetPath = targetPathTf.getText().trim();
        File target = new File(targetPath);
        int selectedIdx = resultTitleList.getSelectedIndex();
        if (selectedIdx < 0 || selectedIdx >= lastResults.size()) return;

        String type = (String) typeCb.getSelectedItem();
        genVsmetaBtn.setEnabled(false);
        genNfoBtn.setEnabled(false);
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
                    int count = metadataGenerator.generateBatch(target, showTitle, ts, lang, vsmeta, !vsmeta);
                    return "批量生成完成，共 " + count + " 个";
                }
                Object result = lastResults.get(selectedIdx);
                if (vsmeta) metadataGenerator.generateVsmeta(target, result);
                else metadataGenerator.generateNfo(target, result);
                return vsmeta ? "vsmeta生成完成" : "nfo生成完成";
            }

            @Override
            protected void done() {
                try {
                    String msg = get();
                    statusLb.setText(msg);
                } catch (Exception e) {
                    statusLb.setText("生成失败: " + e.getMessage());
                } finally {
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
}