package com.lvt4j.spider4videostation.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import com.lvt4j.spider4videostation.ffmpeg.FFmpegUtils;
import com.lvt4j.spider4videostation.ffmpeg.MediaInfo;
import com.lvt4j.spider4videostation.metadata.FUtils;
import com.lvt4j.spider4videostation.metadata.VSmeta;
import com.lvt4j.spider4videostation.service.ConfigService;
import com.lvt4j.spider4videostation.service.DsmApiClient;

/**
 * VS剧集缩略图重刷对话框——扫描目标文件夹下所有有效剧集vsmeta，
 * 用ffmpeg重新生成缩略图覆盖，并通过DSM移出移入触发VS刷新
 *
 * @author LV on 2024年6月1日
 */
public class VSThumbRefreshDialog extends JDialog {

    private final DsmApiClient client;
    private final ConfigService configService;
    private final String targetPath;
    private final File targetDir;

    // Phase 1
    private JTextField targetTf;
    private DefaultComboBoxModel<String> dsmPathModel;
    private JComboBox<String> dsmPathCb;
    private JTextField tempFolderTf;
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

    public VSThumbRefreshDialog(Frame owner, DsmApiClient client,
            ConfigService configService, String targetPath) {
        super(owner, "VS剧集缩略图重刷", true);
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

    // ==================== Phase 1 ====================

    private JPanel buildPhase1Panel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("参数设置"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("目标文件夹:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        targetTf = new JTextField(targetPath);
        targetTf.setEditable(false);
        targetTf.setEnabled(false);
        panel.add(targetTf, gbc);

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

        gbc.gridx = 0; gbc.gridy = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("中转文件夹:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        tempFolderTf = new JTextField();
        String savedTemp = getConfig("vsUnregisteredScanTempFolder");
        if (savedTemp != null) tempFolderTf.setText(savedTemp);
        panel.add(tempFolderTf, gbc);

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
        for (int i = 0; i < dsmPathModel.getSize(); i++) {
            if (path.equals(dsmPathModel.getElementAt(i))) {
                dsmPathModel.removeElementAt(i);
                break;
            }
        }
        dsmPathModel.insertElementAt(path, 0);
        dsmPathCb.setSelectedItem(path);
        while (dsmPathModel.getSize() > 10)
            dsmPathModel.removeElementAt(dsmPathModel.getSize() - 1);
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
        try { configService.set("vsUnregisteredScanTempFolder", tempFolder); } catch (Exception ignored) {}
        saveRecentDsmPath(dsmPath);

        scanBtn.setEnabled(false);
        dsmPathCb.setEnabled(false);
        tempFolderTf.setEnabled(false);

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

        @Override
        protected List<VSmetaFileItem> doInBackground() throws Exception {
            List<VSmetaFileItem> results = new ArrayList<>();
            List<File> vsmetaFiles = new ArrayList<>();
            collectVsmetaFiles(targetDir, vsmetaFiles);
            int total = vsmetaFiles.size();
            int checked = 0;

            for (File vf : vsmetaFiles) {
                checked++;
                // 检查对应视频文件是否存在
                String name = vf.getName();
                String videoName = name.endsWith(".vsmeta")
                    ? name.substring(0, name.length() - 7) : name;
                File videoFile = new File(vf.getParentFile(), videoName);
                if (!videoFile.exists() || !FUtils.isVideoFile(videoFile)) continue;

                // 检查是否为剧集类型
                try {
                    VSmeta meta = new VSmeta(vf);
                    if (meta.type != VSmeta.TypeEpisode) continue;
                } catch (Exception e) {
                    continue; // 解析失败跳过
                }

                VSmetaFileItem item = new VSmetaFileItem();
                item.vsmetaFile = vf;
                item.videoFile = videoFile;
                results.add(item);

                publish("正在扫描... 已检查 " + checked + "/" + total + "，发现 " + results.size() + " 个剧集vsmeta");
            }
            return results;
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
                    centerPanel.add(new JLabel("目标文件夹下未找到有效的剧集vsmeta文件", JLabel.CENTER));
                    centerPanel.revalidate();
                    centerPanel.repaint();
                    scanBtn.setEnabled(true);
                    dsmPathCb.setEnabled(true);
                    tempFolderTf.setEnabled(true);
                } else {
                    showResultTable();
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(VSThumbRefreshDialog.this,
                    "扫描失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                scanBtn.setEnabled(true);
                dsmPathCb.setEnabled(true);
                tempFolderTf.setEnabled(true);
            }
        }
    }

    private void collectVsmetaFiles(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectVsmetaFiles(f, out);
            } else if (f.getName().endsWith(".vsmeta")) {
                out.add(f);
            }
        }
    }

    // ==================== Phase 3: 结果表格 ====================

    private void showResultTable() {
        centerPanel.removeAll();

        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JLabel infoLb = new JLabel("发现 " + scanResults.size() + " 个有效的剧集vsmeta:");
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

        String dsmPath = getDsmPath();
        String tempFolder = tempFolderTf.getText().trim();

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
        logPanel.setBorder(new TitledBorder("重刷进度"));
        logPanel.add(logScroll, BorderLayout.CENTER);
        logPanel.add(btnPanel, BorderLayout.SOUTH);
        centerPanel.add(logPanel, BorderLayout.CENTER);
        centerPanel.revalidate();
        centerPanel.repaint();

        new RefreshWorker(selected, dsmPath, tempFolder).execute();
    }

    private class RefreshWorker extends SwingWorker<Void, String> {

        private final List<VSmetaFileItem> items;
        private final String dsmTargetPath;
        private final String tempFolder;
        private int successCount;
        private int failCount;

        RefreshWorker(List<VSmetaFileItem> items, String dsmTargetPath, String tempFolder) {
            this.items = items;
            this.dsmTargetPath = dsmTargetPath.endsWith("/")
                ? dsmTargetPath.substring(0, dsmTargetPath.length() - 1) : dsmTargetPath;
            this.tempFolder = tempFolder;
        }

        @Override
        protected Void doInBackground() throws Exception {
            int total = items.size();
            for (int i = 0; i < total; i++) {
                VSmetaFileItem item = items.get(i);
                String label = item.videoFile.getName();
                publish("[" + (i + 1) + "/" + total + "] " + label);

                try {
                    // 获取视频时长并计算截图位置
                    publish("  获取视频信息...");
                    MediaInfo mediaInfo = FFmpegUtils.mediaInfo(item.videoFile);
                    long position = (long)(mediaInfo.format.parseDuration() * 0.618);
                    publish("  截图位置: " + FFmpegUtils.formatDuration(position));

                    // 生成缩略图
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

                    // 更新vsmeta
                    VSmeta meta = new VSmeta(item.vsmetaFile);
                    meta.episodeThumbData = VSmeta.readImgData(snapshot);
                    meta.episodeThumbMd5 = md5(snapshot);
                    meta.write(item.vsmetaFile);
                    snapshot.delete();
                    publish("  缩略图已更新");

                    // 触发VS刷新
                    String videoDsmPath = dsmTargetPath + "/" + item.videoFile.getName();
                    publish("  触发VS刷新: " + videoDsmPath);
                    client.moveFiles(Collections.singletonList(videoDsmPath), tempFolder);
                    publish("  移出成功，等待10秒...");
                    Thread.sleep(10000);

                    String movedPath = tempFolder + "/" + item.videoFile.getName();
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

    /** 计算文件MD5 */
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
            VSmetaFileItem item = data.get(row);
            switch (col) {
                case 0: return selected.get(row);
                case 1: return item.vsmetaFile.getAbsolutePath();
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
