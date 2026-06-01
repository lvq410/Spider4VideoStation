package com.lvt4j.spider4videostation.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.lvt4j.spider4videostation.Spider4VideoStationApp;
import com.lvt4j.spider4videostation.Utils;
import com.lvt4j.spider4videostation.metadata.FUtils;
import com.lvt4j.spider4videostation.metadata.VSmeta;
import com.lvt4j.spider4videostation.service.ConfigService;
import com.lvt4j.spider4videostation.service.DsmApiClient;
import com.lvt4j.spider4videostation.service.DsmApiClient.FileInfo;

/**
 * VS追加剧集meta补全对话框——扫描目标文件夹中缺失或信息不全的vsmeta文件，
 * 从同季第一集的完整vsmeta复制元数据补全，并通过DSM移出移入触发VS刷新
 *
 * @author LV on 2024年6月1日
 */
public class VSmetaCompleterDialog extends JDialog {

    private static final Pattern EpPattern = Pattern.compile(
        "(.+)\\.S(\\d{1,2})\\.E(\\d{1,4})(\\.|$)");

    private final DsmApiClient client;
    private final ConfigService configService;
    private final String targetPath;
    private final File targetDir;

    // Phase 1 控件
    private JTextField targetTf;
    private DefaultComboBoxModel<String> dsmPathModel;
    private JComboBox<String> dsmPathCb;
    private JTextField tempFolderTf;
    private JButton scanBtn;

    // Phase 2 控件
    private JLabel progressLb;
    private JProgressBar progressBar;

    // Phase 3 控件
    private JTable resultTable;
    private IncompleteTableModel tableModel;
    private JButton selectAllBtn;
    private JButton deselectAllBtn;
    private JButton completeBtn;

    // Phase 4 控件
    private JTextArea logArea;
    private JButton closeBtn;

    // 主面板区域
    private JPanel centerPanel;
    private JPanel topPanel;

    // 扫描结果
    private List<IncompleteEpisode> scanResults;

    public VSmetaCompleterDialog(Frame owner, DsmApiClient client,
            ConfigService configService, String targetPath) {
        super(owner, "VS追加剧集meta补全", true);
        this.client = client;
        this.configService = configService;
        this.targetPath = targetPath;
        this.targetDir = new File(targetPath);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(950, 680);
        setLocationRelativeTo(owner);

        JPanel rootPanel = new JPanel(new BorderLayout(5, 5));
        rootPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.add(buildPhase1Panel(), BorderLayout.CENTER);
        rootPanel.add(topPanel, BorderLayout.NORTH);

        centerPanel = new JPanel(new BorderLayout());
        rootPanel.add(centerPanel, BorderLayout.CENTER);

        setContentPane(rootPanel);
    }

    // ==================== Phase 1: 参数确认 ====================

    private JPanel buildPhase1Panel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("参数设置"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 目标文件夹（置灰）
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("目标文件夹:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        targetTf = new JTextField(targetPath);
        targetTf.setEditable(false);
        targetTf.setEnabled(false);
        panel.add(targetTf, gbc);

        // 对应DSM路径
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("对应DSM路径:"), gbc);
        JPanel dsmPathPanel = new JPanel(new BorderLayout(5, 0));
        dsmPathModel = new DefaultComboBoxModel<>();
        loadRecentDsmPaths();
        dsmPathCb = new JComboBox<>(dsmPathModel);
        dsmPathCb.setEditable(true);
        dsmPathPanel.add(dsmPathCb, BorderLayout.CENTER);
        JButton browseBtn = new JButton("...");
        browseBtn.addActionListener(e -> browseDsmFolder());
        dsmPathPanel.add(browseBtn, BorderLayout.EAST);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(dsmPathPanel, gbc);

        // 中转文件夹
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("中转文件夹:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        tempFolderTf = new JTextField();
        String savedTemp = getConfig("vsUnregisteredScanTempFolder");
        if (savedTemp != null) tempFolderTf.setText(savedTemp);
        panel.add(tempFolderTf, gbc);

        // 开始扫描按钮
        gbc.gridx = 1; gbc.gridy = 3;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;
        scanBtn = new JButton("开始扫描");
        scanBtn.addActionListener(e -> startScan());
        panel.add(scanBtn, gbc);

        return panel;
    }

    private void loadRecentDsmPaths() {
        try {
            String json = getConfig("recentDsmPaths");
            if (json == null || json.isEmpty()) return;
            ArrayNode arr = (ArrayNode) Utils.ObjectMapper.readTree(json);
            for (JsonNode item : arr) dsmPathModel.addElement(item.asText());
        } catch (Exception ignored) {}
    }

    private void saveRecentDsmPath(String path) {
        // 去重并插入到最前
        for (int i = 0; i < dsmPathModel.getSize(); i++) {
            if (path.equals(dsmPathModel.getElementAt(i))) {
                dsmPathModel.removeElementAt(i);
                break;
            }
        }
        dsmPathModel.insertElementAt(path, 0);
        dsmPathCb.setSelectedItem(path);
        // 最多保留10条
        while (dsmPathModel.getSize() > 10)
            dsmPathModel.removeElementAt(dsmPathModel.getSize() - 1);
        // 持久化
        try {
            ArrayNode arr = Utils.ObjectMapper.createArrayNode();
            for (int i = 0; i < dsmPathModel.getSize(); i++)
                arr.add(dsmPathModel.getElementAt(i));
            configService.set("recentDsmPaths", Utils.ObjectMapper.writeValueAsString(arr));
        } catch (Exception ignored) {}
    }

    private void browseDsmFolder() {
        DSMFolderPickerDialog picker = new DSMFolderPickerDialog(
            (Frame) getOwner(), client, getDsmPath());
        picker.setVisible(true);
        String path = picker.getSelectedPath();
        if (path != null && !path.isEmpty()) {
            dsmPathCb.setSelectedItem(path);
            saveRecentDsmPath(path);
        }
    }

    // ==================== Phase 2: 扫描 ====================

    private void startScan() {
        String dsmPath = getDsmPath();
        String tempFolder = tempFolderTf.getText().trim();
        if (dsmPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请选择或输入对应DSM路径", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (tempFolder.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入中转文件夹路径", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // 保存中转文件夹和DSM路径
        try { configService.set("vsUnregisteredScanTempFolder", tempFolder); } catch (Exception ignored) {}
        saveRecentDsmPath(dsmPath);

        // 禁用控件
        scanBtn.setEnabled(false);
        dsmPathCb.setEnabled(false);
        tempFolderTf.setEnabled(false);

        // 显示进度区（放在NORTH避免被CENTER拉伸）
        centerPanel.removeAll();
        JPanel scanPanel = new JPanel(new BorderLayout(5, 5));
        scanPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        progressLb = new JLabel("正在扫描...");
        Font cjk = Spider4VideoStationApp.getCJKFont();
        if (cjk != null) progressLb.setFont(cjk.deriveFont(12f));
        scanPanel.add(progressLb, BorderLayout.NORTH);
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        scanPanel.add(progressBar, BorderLayout.SOUTH);
        centerPanel.add(scanPanel, BorderLayout.NORTH);
        centerPanel.revalidate();
        centerPanel.repaint();

        new ScanWorker(dsmPath).execute();
    }

    private class ScanWorker extends SwingWorker<List<IncompleteEpisode>, String> {

        private final String dsmBasePath;

        ScanWorker(String dsmBasePath) {
            this.dsmBasePath = dsmBasePath.endsWith("/")
                ? dsmBasePath.substring(0, dsmBasePath.length() - 1) : dsmBasePath;
        }

        @Override
        protected List<IncompleteEpisode> doInBackground() throws Exception {
            List<IncompleteEpisode> results = new ArrayList<>();
            analyzeDir(dsmBasePath, targetDir, results);
            return results;
        }

        /** 深度优先遍历DSM目录，叶子目录分析vsmeta */
        private void analyzeDir(String dsmPath, File localDir, List<IncompleteEpisode> results) throws Exception {
            publish("正在分析 " + dsmPath);

            List<FileInfo> entries = client.listFolder(dsmPath);
            List<FileInfo> subDirs = new ArrayList<>();
            List<File> videoFiles = new ArrayList<>();

            for (FileInfo fi : entries) {
                if (fi.isdir) {
                    subDirs.add(fi);
                } else if (FUtils.isVideoFile(new File(fi.name))) {
                    videoFiles.add(new File(localDir, fi.name));
                }
            }

            // 子目录按名称排序，深度优先递归
            subDirs.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
            for (FileInfo sub : subDirs) {
                analyzeDir(dsmPath + "/" + sub.name,
                    new File(localDir, sub.name), results);
            }

            // 分析本目录的vsmeta
            if (!videoFiles.isEmpty()) {
                analyzeVideos(dsmPath, localDir, videoFiles, results);
            }
        }

        /** 分析一个目录下的视频文件，找出待补全的 */
        private void analyzeVideos(String dsmPath, File localDir, List<File> videoFiles,
                List<IncompleteEpisode> results) throws Exception {
            // 找本目录基准vsmeta（最低episode的可解析vsmeta）
            VSmeta ref = null;
            int minEp = Integer.MAX_VALUE;
            for (File vf : videoFiles) {
                File vsmetaFile = new File(localDir, vf.getName() + ".vsmeta");
                if (!vsmetaFile.exists()) continue;
                try {
                    new VSmeta(vsmetaFile); // 验证可解析
                    Matcher m = EpPattern.matcher(vf.getName());
                    if (m.find()) {
                        int ep = Integer.parseInt(m.group(3));
                        if (ep < minEp) {
                            minEp = ep;
                            ref = new VSmeta(vsmetaFile);
                        }
                    }
                } catch (Exception ignored) {}
            }

            int total = videoFiles.size();
            int checked = 0;
            for (File vf : videoFiles) {
                checked++;
                publish("正在分析 " + dsmPath + " (" + checked + "/" + total + ")");
                File vsmetaFile = new File(localDir, vf.getName() + ".vsmeta");

                if (vsmetaFile.exists()) {
                    try {
                        VSmeta meta = new VSmeta(vsmetaFile);
                        if (ref != null && isIncomplete(meta, ref)) {
                            IncompleteEpisode ie = new IncompleteEpisode();
                            ie.videoFile = vf;
                            ie.vsmetaFile = vsmetaFile;
                            ie.hasVsmeta = true;
                            parseSeasonEpisode(vf.getName(), ie);
                            results.add(ie);
                        }
                    } catch (Exception e) {
                        IncompleteEpisode ie = new IncompleteEpisode();
                        ie.videoFile = vf;
                        ie.vsmetaFile = vsmetaFile;
                        ie.hasVsmeta = true;
                        parseSeasonEpisode(vf.getName(), ie);
                        results.add(ie);
                    }
                } else if (ref != null) {
                    IncompleteEpisode ie = new IncompleteEpisode();
                    ie.videoFile = vf;
                    ie.vsmetaFile = null;
                    ie.hasVsmeta = false;
                    parseSeasonEpisode(vf.getName(), ie);
                    results.add(ie);
                }
            }
        }

        @Override
        protected void process(List<String> chunks) {
            String msg = chunks.get(chunks.size() - 1);
            progressLb.setText(msg);
        }

        @Override
        protected void done() {
            try {
                scanResults = get();
                scanResults.sort((a, b) -> a.videoFile.getAbsolutePath().compareToIgnoreCase(b.videoFile.getAbsolutePath()));
                if (scanResults.isEmpty()) {
                    centerPanel.removeAll();
                    centerPanel.add(new JLabel("未发现需要补全的剧集，所有vsmeta信息完整", JLabel.CENTER));
                    centerPanel.revalidate();
                    centerPanel.repaint();
                    scanBtn.setEnabled(true);
                    dsmPathCb.setEnabled(true);
                    tempFolderTf.setEnabled(true);
                } else {
                    showResultTable();
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(VSmetaCompleterDialog.this,
                    "扫描失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                scanBtn.setEnabled(true);
                dsmPathCb.setEnabled(true);
                tempFolderTf.setEnabled(true);
            }
        }
    }

    /** 递归收集目标目录下所有视频文件 */
    private void collectVideoFiles(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectVideoFiles(f, out);
            } else if (FUtils.isVideoFile(f)) {
                out.add(f);
            }
        }
    }

    /** 判断vsmeta是否"信息不全"：与同目录基准vsmeta对比，基准有的字段目标没有才算缺失 */
    private static boolean isIncomplete(VSmeta meta, VSmeta ref) {
        // 剧集标题没有就一定不完整（与集数相关，不看基准）
        if (meta.episodeTitle == null || meta.episodeTitle.isEmpty()) return true;
        // 统计基准有但目标没有的字段数
        int missing = 0;
        if (ref.year != 0 && meta.year == 0) missing++;
        if (isNotEmpty(ref.casts) && isEmpty(meta.casts)) missing++;
        if (isNotEmpty(ref.directors) && isEmpty(meta.directors)) missing++;
        if (isNotEmpty(ref.genres) && isEmpty(meta.genres)) missing++;
        if (isNotEmpty(ref.episodeReleaseDate) && isEmpty(meta.episodeReleaseDate)) missing++;
        if (isNotEmpty(ref.chapterSummary) && isEmpty(meta.chapterSummary)) missing++;
        return missing >= 2;
    }

    private static boolean isNotEmpty(String s) { return s != null && !s.isEmpty(); }
    private static boolean isEmpty(String s) { return s == null || s.isEmpty(); }
    private static boolean isNotEmpty(List<?> l) { return l != null && !l.isEmpty(); }
    private static boolean isEmpty(List<?> l) { return l == null || l.isEmpty(); }

    /** 从文件名解析 season 和 episode */
    private static void parseSeasonEpisode(String fileName, IncompleteEpisode ie) {
        Matcher m = EpPattern.matcher(fileName);
        if (m.find()) {
            ie.season = Integer.parseInt(m.group(2));
            ie.episode = Integer.parseInt(m.group(3));
        }
    }

    // ==================== Phase 3: 结果表格 ====================

    private void showResultTable() {
        centerPanel.removeAll();

        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JLabel infoLb = new JLabel("发现 " + scanResults.size() + " 个待补全的剧集:");
        Font cjk = Spider4VideoStationApp.getCJKFont();
        if (cjk != null) infoLb.setFont(cjk.deriveFont(12f));
        panel.add(infoLb, BorderLayout.NORTH);

        tableModel = new IncompleteTableModel(scanResults);
        resultTable = new JTable(tableModel);
        resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(600);
        resultTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        if (cjk != null) resultTable.setFont(cjk.deriveFont(12f));
        resultTable.setRowHeight(22);
        JScrollPane tableScroll = new JScrollPane(resultTable);
        panel.add(tableScroll, BorderLayout.CENTER);

        // 按钮栏
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
        selectAllBtn = new JButton("全选");
        selectAllBtn.addActionListener(e -> setAllSelected(true));
        btnPanel.add(selectAllBtn);
        deselectAllBtn = new JButton("取消全选");
        deselectAllBtn.addActionListener(e -> setAllSelected(false));
        btnPanel.add(deselectAllBtn);

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 3));
        completeBtn = new JButton("确认补全");
        completeBtn.addActionListener(e -> startComplete());
        rightBtns.add(completeBtn);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(btnPanel, BorderLayout.WEST);
        bottomPanel.add(rightBtns, BorderLayout.EAST);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        centerPanel.add(panel, BorderLayout.CENTER);

        // 重新扫描按钮
        JPanel rescanPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton rescanBtn = new JButton("重新扫描");
        rescanBtn.addActionListener(e -> {
            // 恢复 Phase 1 状态
            centerPanel.removeAll();
            centerPanel.revalidate();
            centerPanel.repaint();
            scanBtn.setEnabled(true);
            dsmPathCb.setEnabled(true);
            tempFolderTf.setEnabled(true);
        });
        rescanPanel.add(rescanBtn);
        topPanel.add(rescanPanel, BorderLayout.SOUTH);
        topPanel.revalidate();
        topPanel.repaint();

        centerPanel.revalidate();
        centerPanel.repaint();
    }

    private void setAllSelected(boolean selected) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt(selected, i, 0);
        }
    }

    // ==================== Phase 4: 执行补全 ====================

    private void startComplete() {
        // 收集勾选的项目
        List<IncompleteEpisode> selected = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (Boolean.TRUE.equals(tableModel.getValueAt(i, 0))) {
                selected.add(scanResults.get(i));
            }
        }
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请至少勾选一项", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String dsmPath = getDsmPath();
        String tempFolder = tempFolderTf.getText().trim();

        // 替换为日志区
        centerPanel.removeAll();
        topPanel.removeAll();
        topPanel.add(buildPhase1Panel(), BorderLayout.CENTER);
        topPanel.revalidate();
        topPanel.repaint();
        scanBtn.setEnabled(false);
        dsmPathCb.setEnabled(false);
        tempFolderTf.setEnabled(false);

        logArea = new JTextArea();
        logArea.setEditable(false);
        Font cjk = Spider4VideoStationApp.getCJKFont();
        if (cjk != null) logArea.setFont(cjk.deriveFont(12f));
        JScrollPane logScroll = new JScrollPane(logArea);

        closeBtn = new JButton("关闭");
        closeBtn.setEnabled(false);
        closeBtn.addActionListener(e -> dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(closeBtn);

        JPanel logPanel = new JPanel(new BorderLayout(5, 5));
        logPanel.setBorder(new TitledBorder("补全进度"));
        logPanel.add(logScroll, BorderLayout.CENTER);
        logPanel.add(btnPanel, BorderLayout.SOUTH);
        centerPanel.add(logPanel, BorderLayout.CENTER);
        centerPanel.revalidate();
        centerPanel.repaint();

        new CompleteWorker(selected, dsmPath, tempFolder).execute();
    }

    private class CompleteWorker extends SwingWorker<Void, String> {

        private final List<IncompleteEpisode> items;
        private final String dsmTargetPath;
        private final String tempFolder;
        private int successCount;
        private int failCount;

        CompleteWorker(List<IncompleteEpisode> items, String dsmTargetPath, String tempFolder) {
            this.items = items;
            this.dsmTargetPath = dsmTargetPath.endsWith("/")
                ? dsmTargetPath.substring(0, dsmTargetPath.length() - 1) : dsmTargetPath;
            this.tempFolder = tempFolder;
        }

        @Override
        protected Void doInBackground() throws Exception {
            // 预处理：找每目录模板（key=父目录路径）
            Map<String, File> dirTemplates = findSeasonTemplates();
            if (dirTemplates.isEmpty()) {
                publish("警告: 目标文件夹中未找到任何完整的vsmeta模板，无法补全");
                return null;
            }

            int total = items.size();
            for (int i = 0; i < total; i++) {
                IncompleteEpisode ie = items.get(i);
                String label = "S" + String.format("%02d", ie.season)
                    + "E" + String.format("%02d", ie.episode);
                publish("[" + (i + 1) + "/" + total + "] " + label
                    + (ie.hasVsmeta ? " 信息不全" : " 缺失vsmeta"));

                String dirKey = ie.videoFile.getParentFile().getAbsolutePath();
                File templateFile = dirTemplates.get(dirKey);
                if (templateFile == null) {
                    publish("  跳过: 未找到目录 " + dirKey + " 的完整模板");
                    failCount++;
                    continue;
                }
                publish("  使用模板: " + templateFile.getAbsolutePath());

                try {
                    VSmeta template = new VSmeta(templateFile);

                    // 补全/生成 vsmeta
                    VSmeta meta;
                    File vsmetaFile;
                    if (ie.hasVsmeta) {
                        meta = new VSmeta(ie.vsmetaFile);
                        vsmetaFile = ie.vsmetaFile;
                    } else {
                        meta = new VSmeta();
                        meta.type = VSmeta.TypeEpisode;
                        vsmetaFile = new File(ie.videoFile.getParentFile(),
                            ie.videoFile.getName() + ".vsmeta");
                    }
                    copyFromTemplate(meta, template, ie);
                    meta.write(vsmetaFile);
                    publish("  写入 vsmeta 成功: " + vsmetaFile.getAbsolutePath());

                    // 触发VS刷新：移出 → 等待10s → 移回 → 等待10s
                    String videoDsmPath = dsmTargetPath + "/" + ie.videoFile.getName();
                    publish("  触发VS刷新: " + videoDsmPath);
                    client.moveFiles(Collections.singletonList(videoDsmPath), tempFolder);
                    publish("  移出成功，等待10秒...");
                    Thread.sleep(10000);

                    String movedPath = tempFolder + "/" + ie.videoFile.getName();
                    String originalDir = videoDsmPath.substring(0, videoDsmPath.lastIndexOf('/'));
                    client.moveFiles(Collections.singletonList(movedPath), originalDir);
                    publish("  移回成功，等待10秒...");
                    Thread.sleep(10000);

                    successCount++;
                    publish("  完成");
                } catch (Exception e) {
                    failCount++;
                    publish("  失败: " + e.getMessage());
                }
            }
            return null;
        }

        @Override
        protected void process(List<String> chunks) {
            for (String msg : chunks) {
                logArea.append(msg + "\n");
            }
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }

        @Override
        protected void done() {
            try { get(); } catch (Exception ignored) {}
            logArea.append("\n===== 完成 =====\n");
            logArea.append("成功: " + successCount + ", 失败: " + failCount + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
            closeBtn.setEnabled(true);
        }
    }

    /** 在目标文件夹中，按父目录分组找每目录第一集（episode最小）的可解析vsmeta作为模板 */
    private Map<String, File> findSeasonTemplates() throws Exception {
        Map<String, Integer> dirMinEp = new HashMap<>();
        Map<String, File> dirTemplateFile = new HashMap<>();

        List<File> videoFiles = new ArrayList<>();
        collectVideoFiles(targetDir, videoFiles);

        for (File vf : videoFiles) {
            String name = vf.getName();
            Matcher m = EpPattern.matcher(name);
            if (!m.find()) continue;
            int episode = Integer.parseInt(m.group(3));
            File vsmetaFile = new File(vf.getParentFile(), name + ".vsmeta");
            if (!vsmetaFile.exists()) continue;
            try {
                new VSmeta(vsmetaFile); // 仅验证可解析
            } catch (Exception e) {
                continue;
            }
            String dirKey = vf.getParentFile().getAbsolutePath();
            Integer curMin = dirMinEp.get(dirKey);
            if (curMin == null || episode < curMin) {
                dirMinEp.put(dirKey, episode);
                dirTemplateFile.put(dirKey, vsmetaFile);
            }
        }

        return dirTemplateFile;
    }

    /** 从模板复制元数据到目标vsmeta，目标已有值的字段不覆盖 */
    private static void copyFromTemplate(VSmeta target, VSmeta template, IncompleteEpisode ie) {
        if (target.showTitle == null || target.showTitle.isEmpty())
            target.showTitle = template.showTitle;
        if (target.showTitle2 == null || target.showTitle2.isEmpty())
            target.showTitle2 = template.showTitle2;
        if (target.year == 0)
            target.year = template.year;
        if (target.tvShowYear == 0)
            target.tvShowYear = template.tvShowYear;
        // episodeTitle不从模板复制，每集用自己的集号生成
        if (target.episodeReleaseDate == null || target.episodeReleaseDate.isEmpty())
            target.episodeReleaseDate = template.episodeReleaseDate;
        if (target.releaseDateTvShow == null || target.releaseDateTvShow.isEmpty())
            target.releaseDateTvShow = template.releaseDateTvShow;
        if (target.chapterSummary == null || target.chapterSummary.isEmpty())
            target.chapterSummary = template.chapterSummary;
        if (target.tvshowSummary == null || target.tvshowSummary.isEmpty())
            target.tvshowSummary = template.tvshowSummary;
        if (target.classification == null || target.classification.isEmpty())
            target.classification = template.classification;
        if (target.episodeMetaJson == null || target.episodeMetaJson.isEmpty())
            target.episodeMetaJson = template.episodeMetaJson;
        if (target.tvshowMetaJson == null || target.tvshowMetaJson.isEmpty())
            target.tvshowMetaJson = template.tvshowMetaJson;
        if (target.casts == null || target.casts.isEmpty())
            target.casts = new ArrayList<>(template.casts);
        if (target.directors == null || target.directors.isEmpty())
            target.directors = new ArrayList<>(template.directors);
        if (target.genres == null || target.genres.isEmpty())
            target.genres = new ArrayList<>(template.genres);
        if (target.writers == null || target.writers.isEmpty())
            target.writers = new ArrayList<>(template.writers);
        target.rating = template.rating;
        if (target.posterData == null || target.posterData.isEmpty())
            target.posterData = template.posterData;
        if (target.posterMd5 == null || target.posterMd5.isEmpty())
            target.posterMd5 = template.posterMd5;
        if (target.backdropData == null || target.backdropData.isEmpty())
            target.backdropData = template.backdropData;
        if (target.backdropMd5 == null || target.backdropMd5.isEmpty())
            target.backdropMd5 = template.backdropMd5;
        if (target.episodeThumbData == null || target.episodeThumbData.isEmpty())
            target.episodeThumbData = template.episodeThumbData;
        if (target.episodeThumbMd5 == null || target.episodeThumbMd5.isEmpty())
            target.episodeThumbMd5 = template.episodeThumbMd5;
        if (target.timestamp == null)
            target.timestamp = template.timestamp;
        // 保留目标的 season/episode
        target.season = ie.season;
        target.episode = ie.episode;
        // 剧集标题兜底
        if (target.episodeTitle == null || target.episodeTitle.isEmpty()) {
            target.episodeTitle = ie.episode == 0 ? "SP" : "第" + ie.episode + "集";
        }
    }

    // ==================== 工具方法 ====================

    private String getDsmPath() {
        Object sel = dsmPathCb.getSelectedItem();
        return sel != null ? sel.toString().trim() : "";
    }

    private String getConfig(String key) {
        try {
            return configService.gets(Collections.singletonList(key)).get(key);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 数据模型 ====================

    static class IncompleteEpisode {
        File videoFile;
        File vsmetaFile;
        boolean hasVsmeta;
        int season;
        int episode;
    }

    @SuppressWarnings("serial")
    static class IncompleteTableModel extends AbstractTableModel {

        private final String[] columns = {"", "文件路径", "状态"};
        private final List<IncompleteEpisode> data;
        private final List<Boolean> selected;

        IncompleteTableModel(List<IncompleteEpisode> data) {
            this.data = data;
            this.selected = new ArrayList<>(data.size());
            for (int i = 0; i < data.size(); i++) selected.add(true);
        }

        @Override
        public int getRowCount() { return data.size(); }

        @Override
        public int getColumnCount() { return columns.length; }

        @Override
        public String getColumnName(int col) { return columns[col]; }

        @Override
        public Class<?> getColumnClass(int col) {
            return col == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int col) { return col == 0; }

        @Override
        public Object getValueAt(int row, int col) {
            IncompleteEpisode ie = data.get(row);
            switch (col) {
                case 0: return selected.get(row);
                case 1:
                    if (ie.hasVsmeta && ie.vsmetaFile != null)
                        return ie.vsmetaFile.getAbsolutePath();
                    return ie.videoFile.getAbsolutePath();
                case 2: return ie.hasVsmeta ? "信息不全" : "缺失vsmeta";
                default: return null;
            }
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col == 0) {
                selected.set(row, (Boolean) value);
                fireTableCellUpdated(row, col);
            }
        }
    }
}
