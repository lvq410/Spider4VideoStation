package com.lvt4j.spider4videostation.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.lvt4j.spider4videostation.ffmpeg.FFmpegUtils;
import com.lvt4j.spider4videostation.ffmpeg.MediaInfo;
import com.lvt4j.spider4videostation.metadata.FUtils;
import com.lvt4j.spider4videostation.metadata.VSmeta;
import com.lvt4j.spider4videostation.service.ConfigService;

/**
 * VS剧集缩略图重刷对话框——扫描目标文件夹下剧集vsmeta，用ffmpeg重新生成缩略图覆盖
 *
 * @author LV on 2024年6月1日
 */
public class VSThumbRefreshDialog extends JDialog {

    private static final String[] MODES = {"仅无缩略图", "重复缩略图", "无+重复", "全部重刷"};

    private final ConfigService configService;
    private File targetDir;

    // Phase 1
    private DefaultComboBoxModel<String> targetPathModel;
    private JComboBox<String> targetPathCb;
    private DefaultComboBoxModel<String> tempPathModel;
    private JComboBox<String> tempPathCb;
    private JComboBox<String> modeCb;
    private JButton scanBtn;

    // Phase 2
    private JLabel progressLb;
    private JProgressBar progressBar;

    // Phase 3
    private JTable resultTable;
    private ThumbTableModel tableModel;

    // Phase 4
    private JTextArea logArea;
    private JButton closeBtn;

    private JPanel centerPanel;
    private JPanel topPanel;

    private List<VSmetaFileItem> scanResults;
    /** vsmeta文件缓存，key=vsmetaFile，扫描时填充避免重复读取 */
    private final Map<File, VSmeta> metaCache = new HashMap<>();

    public VSThumbRefreshDialog(Frame owner, ConfigService configService, String initialTargetPath) {
        super(owner, "VS剧集缩略图重刷", true);
        this.configService = configService;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(950, 680);
        setLocationRelativeTo(owner);

        JPanel rootPanel = new JPanel(new BorderLayout(5, 5));
        rootPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.add(buildPhase1Panel(initialTargetPath), BorderLayout.CENTER);
        rootPanel.add(topPanel, BorderLayout.NORTH);

        centerPanel = new JPanel(new BorderLayout());
        rootPanel.add(centerPanel, BorderLayout.CENTER);

        setContentPane(rootPanel);
    }

    // ==================== Phase 1 ====================

    private JPanel buildPhase1Panel(String initialTargetPath) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("参数设置"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 目标文件夹
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("目标文件夹:"), gbc);
        JPanel targetPanel = new JPanel(new BorderLayout(5, 0));
        targetPathModel = new DefaultComboBoxModel<>();
        loadRecentPaths("recentThumbTargets", targetPathModel);
        if (initialTargetPath != null && !initialTargetPath.isEmpty()) {
            for (int i = 0; i < targetPathModel.getSize(); i++) {
                if (initialTargetPath.equals(targetPathModel.getElementAt(i))) {
                    targetPathModel.removeElementAt(i); break;
                }
            }
            targetPathModel.insertElementAt(initialTargetPath, 0);
        }
        targetPathCb = new JComboBox<>(targetPathModel);
        targetPathCb.setEditable(true);
        targetPathCb.setSelectedItem(initialTargetPath);
        targetPanel.add(targetPathCb, BorderLayout.CENTER);
        JButton targetBrowseBtn = new JButton("...");
        targetBrowseBtn.addActionListener(e -> browseFolder("recentThumbTargets", targetPathModel, targetPathCb));
        targetPanel.add(targetBrowseBtn, BorderLayout.EAST);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(targetPanel, gbc);

        // 中转文件夹
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("中转文件夹:"), gbc);
        JPanel tempPanel = new JPanel(new BorderLayout(5, 0));
        tempPathModel = new DefaultComboBoxModel<>();
        loadRecentPaths("recentThumbTempFolders", tempPathModel);
        tempPathCb = new JComboBox<>(tempPathModel);
        tempPathCb.setEditable(true);
        tempPanel.add(tempPathCb, BorderLayout.CENTER);
        JButton tempBrowseBtn = new JButton("...");
        tempBrowseBtn.addActionListener(e -> browseFolder("recentThumbTempFolders", tempPathModel, tempPathCb));
        tempPanel.add(tempBrowseBtn, BorderLayout.EAST);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(tempPanel, gbc);

        // 扫描模式
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("扫描模式:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        modeCb = new JComboBox<>(MODES);
        modeCb.setSelectedIndex(0);
        panel.add(modeCb, gbc);

        // 开始扫描
        gbc.gridx = 1; gbc.gridy = 3;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;
        scanBtn = new JButton("开始扫描");
        scanBtn.addActionListener(e -> startScan());
        panel.add(scanBtn, gbc);

        return panel;
    }

    private void loadRecentPaths(String configKey, DefaultComboBoxModel<String> model) {
        try {
            String json = getConfig(configKey);
            if (json == null || json.isEmpty()) return;
            ArrayNode arr = (ArrayNode) Utils.ObjectMapper.readTree(json);
            Set<String> seen = new HashSet<>();
            for (JsonNode item : arr) {
                String path = item.asText();
                if (seen.add(path)) model.addElement(path);
            }
        } catch (Exception ignored) {}
    }

    private void saveRecentPath(String configKey, DefaultComboBoxModel<String> model,
            JComboBox<String> cb, String path) {
        for (int i = 0; i < model.getSize(); i++) {
            if (path.equals(model.getElementAt(i))) { model.removeElementAt(i); break; }
        }
        model.insertElementAt(path, 0);
        cb.setSelectedItem(path);
        while (model.getSize() > 10) model.removeElementAt(model.getSize() - 1);
        try {
            ArrayNode arr = Utils.ObjectMapper.createArrayNode();
            for (int i = 0; i < model.getSize(); i++) arr.add(model.getElementAt(i));
            configService.set(configKey, Utils.ObjectMapper.writeValueAsString(arr));
        } catch (Exception ignored) {}
    }

    private void browseFolder(String configKey, DefaultComboBoxModel<String> model,
            JComboBox<String> cb) {
        String initialPath = cb.getSelectedItem() != null ? cb.getSelectedItem().toString().trim() : "";
        File initialDir = !initialPath.isEmpty() ? new File(initialPath) : new File("N:\\");
        FilePickerDialog picker = new FilePickerDialog((Frame) getOwner(), initialDir, true);
        picker.setVisible(true);
        FilePickerDialog.DialogResult result = picker.getResult();
        if (result != null && result.path != null && !result.path.isEmpty()) {
            saveRecentPath(configKey, model, cb, result.path);
        }
    }

    // ==================== Phase 2: 扫描 ====================

    private void startScan() {
        String targetPath = getTargetPath();
        String tempPath = getTempPath();
        if (targetPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请选择目标文件夹", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (tempPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请选择中转文件夹", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        targetDir = new File(targetPath);
        if (!targetDir.isDirectory()) {
            JOptionPane.showMessageDialog(this, "目标文件夹不存在: " + targetPath, "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        File tempDir = new File(tempPath);
        Path p1 = targetDir.toPath().toAbsolutePath().normalize();
        Path p2 = tempDir.toPath().toAbsolutePath().normalize();
        if (p1.getNameCount() < 1 || p2.getNameCount() < 1
                || !p1.getName(0).equals(p2.getName(0))) {
            JOptionPane.showMessageDialog(this,
                "目标文件夹和中转文件夹必须在同一一级目录下\n目标: " + p1 + "\n中转: " + p2,
                "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        saveRecentPath("recentThumbTargets", targetPathModel, targetPathCb, targetPath);
        saveRecentPath("recentThumbTempFolders", tempPathModel, tempPathCb, tempPath);
        metaCache.clear();

        scanBtn.setEnabled(false);
        targetPathCb.setEnabled(false);
        tempPathCb.setEnabled(false);
        modeCb.setEnabled(false);

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

        new ScanWorker().execute();
    }

    private class ScanWorker extends SwingWorker<List<VSmetaFileItem>, String> {

        private final List<VSmetaFileItem> results = new ArrayList<>();
        private int mode;

        @Override
        protected List<VSmetaFileItem> doInBackground() throws Exception {
            mode = modeCb.getSelectedIndex();
            analyzeDir(targetDir);
            return results;
        }

        /** 深度优先遍历：先子目录，再加载本目录vsmeta并分析 */
        private void analyzeDir(File dir) {
            publish("正在分析 " + dir.getAbsolutePath());
            
            File[] entries = dir.listFiles();
            if (entries == null) return;
            Set<String> fileNames = new HashSet<>();
            List<File> subDirs = new ArrayList<>();
            int vsmetaCount = 0;
            for (File f : entries) {
                fileNames.add(f.getName());
                if (f.isDirectory()) subDirs.add(f);
                else if (f.getName().endsWith(".vsmeta")) vsmetaCount++;
            }

            // 先递归子目录
            subDirs.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File sub : subDirs) {
                analyzeDir(sub);
            }

            // 再加载本目录vsmeta
            List<VSmetaFileItem> dirItems = new ArrayList<>();
            int loaded = 0;
            for (File f : entries) {
                if (!f.getName().endsWith(".vsmeta")) continue;
                loaded++;
                publish("正在分析 " + dir.getAbsolutePath() + " (" + loaded + "/" + vsmetaCount + ")");
                String videoName = f.getName().substring(0, f.getName().length() - 7);
                if (!fileNames.contains(videoName)) continue;
                if (!FUtils.isVideoFile(videoName)) continue;
                try {
                    VSmeta meta = new VSmeta(f);
                    metaCache.put(f, meta);
                    if (meta.type == VSmeta.TypeEpisode) {
                        VSmetaFileItem item = new VSmetaFileItem();
                        item.vsmetaFile = f;
                        item.videoFile = new File(dir, videoName);
                        dirItems.add(item);
                    }
                } catch (Exception ignored) {}
            }

            // 最后分析本目录
            filterDirResults(dir.getAbsolutePath(), dirItems);
            // 清理非结果的缓存
            cleanupMetaCache();
        }

        private void cleanupMetaCache() {
            Set<File> keep = new HashSet<>();
            for (VSmetaFileItem item : results) keep.add(item.vsmetaFile);
            metaCache.keySet().removeIf(k -> !keep.contains(k));
        }

        /** 根据当前模式，对本目录的条目做筛选并加入results */
        private void filterDirResults(String dirKey, List<VSmetaFileItem> dirItems) {
            if (dirItems.isEmpty()) return;
            if (mode == 3) { results.addAll(dirItems); return; }

            if (mode == 0 || mode == 2) {
                // 仅无缩略图 / 无+重复
                for (VSmetaFileItem item : dirItems) {
                    VSmeta meta = metaCache.get(item.vsmetaFile);
                    if (meta != null && (meta.episodeThumbData == null || meta.episodeThumbData.isEmpty())) {
                        results.add(item);
                    }
                }
            }
            if (mode == 1 || mode == 2) {
                // 重复缩略图：同目录下MD5相同的全部加入
                Set<String> alreadyAdded = new HashSet<>();
                for (VSmetaFileItem r : results) alreadyAdded.add(r.vsmetaFile.getAbsolutePath());
                Map<String, List<VSmetaFileItem>> md5Groups = new HashMap<>();
                for (VSmetaFileItem item : dirItems) {
                    VSmeta meta = metaCache.get(item.vsmetaFile);
                    if (meta == null) continue;
                    String md5 = meta.episodeThumbMd5 != null ? meta.episodeThumbMd5 : "";
                    if (md5.isEmpty()) continue;
                    md5Groups.computeIfAbsent(md5, k -> new ArrayList<>()).add(item);
                }
                for (List<VSmetaFileItem> dupGroup : md5Groups.values()) {
                    if (dupGroup.size() >= 2) {
                        for (VSmetaFileItem item : dupGroup) {
                            if (alreadyAdded.add(item.vsmetaFile.getAbsolutePath())) {
                                results.add(item);
                            }
                        }
                    }
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
                scanResults.sort((a, b) -> a.vsmetaFile.getAbsolutePath()
                    .compareToIgnoreCase(b.vsmetaFile.getAbsolutePath()));
                if (scanResults.isEmpty()) {
                    centerPanel.removeAll();
                    centerPanel.add(new JLabel("未找到符合条件的剧集vsmeta", JLabel.CENTER));
                    centerPanel.revalidate();
                    centerPanel.repaint();
                    scanBtn.setEnabled(true);
                    targetPathCb.setEnabled(true);
                    tempPathCb.setEnabled(true);
                    modeCb.setEnabled(true);
                } else {
                    showResultTable();
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(VSThumbRefreshDialog.this,
                    "扫描失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                scanBtn.setEnabled(true);
                targetPathCb.setEnabled(true);
                tempPathCb.setEnabled(true);
                modeCb.setEnabled(true);
            }
        }
    }

    // ==================== Phase 3: 结果表格 ====================

    private void showResultTable() {
        centerPanel.removeAll();

        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JLabel infoLb = new JLabel("发现 " + scanResults.size() + " 个剧集vsmeta:");
        Font cjk = Spider4VideoStationApp.getCJKFont();
        if (cjk != null) infoLb.setFont(cjk.deriveFont(12f));
        panel.add(infoLb, BorderLayout.NORTH);

        tableModel = new ThumbTableModel(scanResults);
        resultTable = new JTable(tableModel);
        resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(700);
        if (cjk != null) resultTable.setFont(cjk.deriveFont(12f));
        resultTable.setRowHeight(22);
        JScrollPane tableScroll = new JScrollPane(resultTable);
        panel.add(tableScroll, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
        JButton selectAllBtn = new JButton("全选");
        selectAllBtn.addActionListener(e -> setAllSelected(true));
        btnPanel.add(selectAllBtn);
        JButton deselectAllBtn = new JButton("取消全选");
        deselectAllBtn.addActionListener(e -> setAllSelected(false));
        btnPanel.add(deselectAllBtn);

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 3));
        JButton refreshBtn = new JButton("确认重刷");
        refreshBtn.addActionListener(e -> startRefresh());
        rightBtns.add(refreshBtn);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(btnPanel, BorderLayout.WEST);
        bottomPanel.add(rightBtns, BorderLayout.EAST);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        centerPanel.add(panel, BorderLayout.CENTER);

        JPanel rescanPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton rescanBtn = new JButton("重新扫描");
        rescanBtn.addActionListener(e -> {
            centerPanel.removeAll();
            centerPanel.revalidate();
            centerPanel.repaint();
            scanBtn.setEnabled(true);
            targetPathCb.setEnabled(true);
            tempPathCb.setEnabled(true);
            modeCb.setEnabled(true);
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

    // ==================== Phase 4: 执行重刷 ====================

    private void startRefresh() {
        List<VSmetaFileItem> selected = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (Boolean.TRUE.equals(tableModel.getValueAt(i, 0))) {
                selected.add(scanResults.get(i));
            }
        }
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请至少勾选一项", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String tempPath = getTempPath();

        centerPanel.removeAll();
        topPanel.removeAll();
        topPanel.add(buildPhase1Panel(targetDir.getAbsolutePath()), BorderLayout.CENTER);
        topPanel.revalidate();
        topPanel.repaint();
        scanBtn.setEnabled(false);
        targetPathCb.setEnabled(false);
        tempPathCb.setEnabled(false);
        modeCb.setEnabled(false);

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
        logPanel.setBorder(new TitledBorder("重刷进度"));
        logPanel.add(logScroll, BorderLayout.CENTER);
        logPanel.add(btnPanel, BorderLayout.SOUTH);
        centerPanel.add(logPanel, BorderLayout.CENTER);
        centerPanel.revalidate();
        centerPanel.repaint();

        new RefreshWorker(selected, tempPath).execute();
    }

    private class RefreshWorker extends SwingWorker<Void, String> {

        private final List<VSmetaFileItem> items;
        private final String tempFolder;
        private int successCount;
        private int failCount;

        RefreshWorker(List<VSmetaFileItem> items, String tempFolder) {
            this.items = items;
            this.tempFolder = tempFolder;
        }

        @Override
        protected Void doInBackground() throws Exception {
            int total = items.size();
            for (int i = 0; i < total; i++) {
                VSmetaFileItem item = items.get(i);
                publish("[" + (i + 1) + "/" + total + "] " + item.videoFile.getAbsolutePath());

                try {
                    MediaInfo mediaInfo = FFmpegUtils.mediaInfo(item.videoFile);
                    long position = (long)(mediaInfo.format.parseDuration() * 0.618);
                    publish("  截图位置: " + FFmpegUtils.formatDuration(position));

                    String name = item.videoFile.getName();
                    int dot = name.lastIndexOf('.');
                    String baseName = dot > 0 ? name.substring(0, dot) : name;
                    File snapshot = new File(item.videoFile.getParentFile(), baseName + ".thumb.tmp.jpg");
                    FFmpegUtils.snapshot(item.videoFile, FFmpegUtils.formatDuration(position), snapshot);

                    if (!snapshot.exists() || snapshot.length() == 0) {
                        failCount++;
                        publish("  失败: 截图生成失败");
                        continue;
                    }

                    VSmeta meta = new VSmeta(item.vsmetaFile);
                    meta.episodeThumbData = VSmeta.readImgData(snapshot);
                    meta.episodeThumbMd5 = md5(snapshot);
                    meta.write(item.vsmetaFile);
                    snapshot.delete();
                    publish("  缩略图已更新");

                    Path src = item.videoFile.toPath();
                    Path dest = Paths.get(tempFolder, item.videoFile.getName());
                    publish("  触发VS刷新: " + src + " -> " + dest);
                    Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    publish("  移出成功，等待10秒...");
                    Thread.sleep(10000);
                    Files.move(dest, src, StandardCopyOption.REPLACE_EXISTING);
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
            for (String msg : chunks) logArea.append(msg + "\n");
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

    private static String md5(File file) {
        try {
            byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ==================== 工具方法 ====================

    private String getTargetPath() {
        Object sel = targetPathCb.getSelectedItem();
        return sel != null ? sel.toString().trim() : "";
    }

    private String getTempPath() {
        Object sel = tempPathCb.getSelectedItem();
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

    static class VSmetaFileItem {
        File vsmetaFile;
        File videoFile;
    }

    @SuppressWarnings("serial")
    static class ThumbTableModel extends AbstractTableModel {

        private final String[] columns = {"", "vsmeta文件路径"};
        private final List<VSmetaFileItem> data;
        private final List<Boolean> selected;

        ThumbTableModel(List<VSmetaFileItem> data) {
            this.data = data;
            this.selected = new ArrayList<>(data.size());
            for (int i = 0; i < data.size(); i++) selected.add(true);
        }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }
        @Override public Class<?> getColumnClass(int col) { return col == 0 ? Boolean.class : String.class; }
        @Override public boolean isCellEditable(int row, int col) { return col == 0; }

        @Override
        public Object getValueAt(int row, int col) {
            VSmetaFileItem item = data.get(row);
            switch (col) {
                case 0: return selected.get(row);
                case 1: return item.vsmetaFile.getAbsolutePath();
                default: return null;
            }
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col == 0) { selected.set(row, (Boolean) value); fireTableCellUpdated(row, col); }
        }
    }
}
