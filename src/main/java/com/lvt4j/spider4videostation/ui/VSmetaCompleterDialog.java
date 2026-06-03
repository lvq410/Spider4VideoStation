package com.lvt4j.spider4videostation.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
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
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

/**
 * VS追加剧集meta补全对话框——扫描目标文件夹中缺失或信息不全的vsmeta文件，
 * 从同目录同season第一集的完整vsmeta复制元数据补全，并通过本地文件移出移入触发VS刷新
 *
 * @author LV on 2024年6月1日
 */
public class VSmetaCompleterDialog extends JDialog {

    private static final Pattern EpPattern = Pattern.compile(
        "(.+)\\.S(\\d{1,2})\\.E(\\d{1,4})(\\.|$)");

    private final ConfigService configService;
    private File targetDir;

    // Phase 1
    private DefaultComboBoxModel<String> targetPathModel;
    private JComboBox<String> targetPathCb;
    private DefaultComboBoxModel<String> tempPathModel;
    private JComboBox<String> tempPathCb;
    private JButton scanBtn;

    // Phase 2
    private JLabel progressLb;
    private JProgressBar progressBar;

    // Phase 3
    private JTable resultTable;
    private IncompleteTableModel tableModel;

    // Phase 4
    private JTextArea logArea;
    private JButton closeBtn;

    private JPanel centerPanel;
    private JPanel topPanel;

    private List<IncompleteEpisode> scanResults;
    /** vsmeta文件缓存，key=vsmetaFile，扫描时填充，补全时复用避免重复读取 */
    private final Map<File, VSmeta> metaCache = new HashMap<>();
    /** 模板缓存，key=folderAbsPath|season，扫描时填充，补全时复用 */
    private final Map<String, Optional<VSmeta>> templateCache = new HashMap<>();

    public VSmetaCompleterDialog(Frame owner, ConfigService configService, String initialTargetPath) {
        super(owner, "VS追加剧集meta补全", true);
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
        loadRecentPaths("recentMetaCompleteTargets", targetPathModel);
        if (initialTargetPath != null && !initialTargetPath.isEmpty()) {
            // 去重：如已存在则移除旧位置
            for (int i = 0; i < targetPathModel.getSize(); i++) {
                if (initialTargetPath.equals(targetPathModel.getElementAt(i))) {
                    targetPathModel.removeElementAt(i);
                    break;
                }
            }
            targetPathModel.insertElementAt(initialTargetPath, 0);
        }
        targetPathCb = new JComboBox<>(targetPathModel);
        targetPathCb.setEditable(true);
        targetPathCb.setSelectedItem(initialTargetPath);
        targetPanel.add(targetPathCb, BorderLayout.CENTER);
        JButton targetBrowseBtn = new JButton("...");
        targetBrowseBtn.addActionListener(e -> browseFolder(true));
        targetPanel.add(targetBrowseBtn, BorderLayout.EAST);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(targetPanel, gbc);

        // 中转文件夹
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("中转文件夹:"), gbc);
        JPanel tempPanel = new JPanel(new BorderLayout(5, 0));
        tempPathModel = new DefaultComboBoxModel<>();
        loadRecentPaths("recentMetaCompleteTempFolders", tempPathModel);
        tempPathCb = new JComboBox<>(tempPathModel);
        tempPathCb.setEditable(true);
        tempPanel.add(tempPathCb, BorderLayout.CENTER);
        JButton tempBrowseBtn = new JButton("...");
        tempBrowseBtn.addActionListener(e -> browseFolder(false));
        tempPanel.add(tempBrowseBtn, BorderLayout.EAST);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(tempPanel, gbc);

        // 开始扫描
        gbc.gridx = 1; gbc.gridy = 2;
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
            for (JsonNode item : arr) {
                String path = item.asText();
                // 去重
                boolean dup = false;
                for (int i = 0; i < model.getSize(); i++) {
                    if (path.equals(model.getElementAt(i))) { dup = true; break; }
                }
                if (!dup) model.addElement(path);
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

    private void browseFolder(boolean isTarget) {
        String initialPath = isTarget ? getTargetPath() : getTempPath();
        File initialDir = (initialPath != null && !initialPath.isEmpty())
            ? new File(initialPath) : new File("N:\\");
        FilePickerDialog picker = new FilePickerDialog((Frame) getOwner(), initialDir, true);
        picker.setVisible(true);
        FilePickerDialog.DialogResult result = picker.getResult();
        if (result != null && result.path != null && !result.path.isEmpty()) {
            String configKey = isTarget ? "recentMetaCompleteTargets" : "recentMetaCompleteTempFolders";
            DefaultComboBoxModel<String> model = isTarget ? targetPathModel : tempPathModel;
            JComboBox<String> cb = isTarget ? targetPathCb : tempPathCb;
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
        // 校验一级目录相同
        Path p1 = targetDir.toPath().toAbsolutePath().normalize();
        Path p2 = tempDir.toPath().toAbsolutePath().normalize();
        if (p1.getNameCount() < 1 || p2.getNameCount() < 1
                || !p1.getName(0).equals(p2.getName(0))) {
            JOptionPane.showMessageDialog(this,
                "目标文件夹和中转文件夹必须在同一一级目录下\n目标: " + p1 + "\n中转: " + p2,
                "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        saveRecentPath("recentMetaCompleteTargets", targetPathModel, targetPathCb, targetPath);
        saveRecentPath("recentMetaCompleteTempFolders", tempPathModel, tempPathCb, tempPath);
        metaCache.clear();
        templateCache.clear();

        scanBtn.setEnabled(false);
        targetPathCb.setEnabled(false);
        tempPathCb.setEnabled(false);

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

    private class ScanWorker extends SwingWorker<List<IncompleteEpisode>, String> {

        @Override
        protected List<IncompleteEpisode> doInBackground() throws Exception {
            List<IncompleteEpisode> results = new ArrayList<>();
            analyzeDir(targetDir, results);
            return results;
        }

        /** 深度优先遍历本地目录 */
        private void analyzeDir(File localDir, List<IncompleteEpisode> results) throws Exception {
            publish("正在分析 " + localDir.getAbsolutePath());

            File[] entries = localDir.listFiles();
            if (entries == null) return;
            List<File> subDirs = new ArrayList<>();
            List<File> videoFiles = new ArrayList<>();
            Set<String> fileNames = new HashSet<>();

            for (File f : entries) {
                fileNames.add(f.getName());
                if (f.isDirectory()) {
                    subDirs.add(f);
                } else if (FUtils.isVideoFile(f.getName())) {
                    videoFiles.add(f);
                }
            }

            // 子目录按名称排序，深度优先递归
            subDirs.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File sub : subDirs) {
                analyzeDir(sub, results);
            }

            // 分析本目录的vsmeta
            if (!videoFiles.isEmpty()) {
                analyzeVideos(localDir, videoFiles, fileNames, results);
            }
            // 清理本目录非结果非模板的缓存，释放内存
            cleanupMetaCache(results);
        }

        private void cleanupMetaCache(List<IncompleteEpisode> results) {
            Set<File> keep = new HashSet<>();
            for (IncompleteEpisode ie : results) {
                if (ie.vsmetaFile != null) keep.add(ie.vsmetaFile);
            }
            for (Optional<VSmeta> opt : templateCache.values()) {
                // 模板的VSmeta对象已缓存，但其文件引用需从metaCache反查
                opt.ifPresent(t -> {
                    for (Map.Entry<File, VSmeta> e : metaCache.entrySet()) {
                        if (e.getValue() == t) { keep.add(e.getKey()); break; }
                    }
                });
            }
            metaCache.keySet().removeIf(k -> !keep.contains(k));
        }

        /** 分析一个目录下的视频文件，找出待补全的 */
        private void analyzeVideos(File localDir, List<File> videoFiles,
                Set<String> fileNames, List<IncompleteEpisode> results) throws Exception {
            // 第一轮：按season分组、episode顺排，逐个找模板（第一个可解析的vsmeta即为该season基准）
            Map<Integer, List<File>> seasonFiles = new HashMap<>();
            for (File vf : videoFiles) {
                Matcher m = EpPattern.matcher(vf.getName());
                if (!m.find()) continue;
                int s = Integer.parseInt(m.group(2));
                int ep = Integer.parseInt(m.group(3));
                if (ep == 0) continue; // 跳过第0集，从第1集开始找基准
                seasonFiles.computeIfAbsent(s, k -> new ArrayList<>()).add(vf);
            }
            for (List<File> list : seasonFiles.values()) {
                list.sort((a, b) -> {
                    Matcher ma = EpPattern.matcher(a.getName());
                    Matcher mb = EpPattern.matcher(b.getName());
                    int ea = ma.find() ? Integer.parseInt(ma.group(3)) : 0;
                    int eb = mb.find() ? Integer.parseInt(mb.group(3)) : 0;
                    return Integer.compare(ea, eb);
                });
            }

            int total = videoFiles.size();
            int parsed = 0;
            for (File vf : videoFiles) {
                parsed++;
                String vsmetaName = vf.getName() + ".vsmeta";
                if (!fileNames.contains(vsmetaName)) continue;
                File vsmetaFile = new File(localDir, vsmetaName);
                try {
                    VSmeta meta = new VSmeta(vsmetaFile);
                    metaCache.put(vsmetaFile, meta);
                } catch (Exception ignored) {}
                if (parsed % 20 == 0 || parsed == total) {
                    publish("正在分析 " + localDir.getAbsolutePath()
                        + " (" + parsed + "/" + total + ")");
                }
            }

            // 从已缓存的metaCache中按season的episode顺排找每个season的模板
            String folderKey = localDir.getAbsolutePath();
            for (Map.Entry<Integer, List<File>> entry : seasonFiles.entrySet()) {
                int season = entry.getKey();
                for (File vf : entry.getValue()) {
                    File vsmetaFile = new File(localDir, vf.getName() + ".vsmeta");
                    VSmeta meta = metaCache.get(vsmetaFile);
                    if (meta != null) {
                        templateCache.put(folderKey + "|" + season, Optional.of(meta));
                        break;
                    }
                }
            }

            // 第二轮：用缓存对象 + 对应season的ref判断（纯内存，无需进度）
            for (File vf : videoFiles) {
                String vsmetaName = vf.getName() + ".vsmeta";
                boolean vsmetaExists = fileNames.contains(vsmetaName);
                File vsmetaFile = new File(localDir, vsmetaName);

                if (vsmetaExists) {
                    VSmeta meta = metaCache.get(vsmetaFile);
                    if (meta != null) {
                        VSmeta ref = getSeasonRef(folderKey, vf);
                        if (ref != null) {
                            List<String> missing = checkMissingFields(meta, ref);
                            if (!missing.isEmpty()) {
                                addResult(results, vf, vsmetaFile, true, missing);
                            }
                        }
                    } else {
                        addResult(results, vf, vsmetaFile, true, null);
                    }
                } else {
                    VSmeta ref = getSeasonRef(folderKey, vf);
                    if (ref != null) {
                        addResult(results, vf, null, false, null);
                    }
                }
            }
        }

        private VSmeta getSeasonRef(String folderKey, File vf) {
            Matcher m = EpPattern.matcher(vf.getName());
            if (!m.find()) return null;
            int s = Integer.parseInt(m.group(2));
            return templateCache.getOrDefault(folderKey + "|" + s, Optional.empty()).orElse(null);
        }

        private void addResult(List<IncompleteEpisode> results, File vf,
                File vsmetaFile, boolean hasVsmeta, List<String> missingFields) {
            IncompleteEpisode ie = new IncompleteEpisode();
            ie.videoFile = vf;
            ie.vsmetaFile = vsmetaFile;
            ie.hasVsmeta = hasVsmeta;
            ie.missingFields = missingFields;
            parseSeasonEpisode(vf.getName(), ie);
            results.add(ie);
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
                scanResults.sort((a, b) -> a.videoFile.getAbsolutePath()
                    .compareToIgnoreCase(b.videoFile.getAbsolutePath()));
                if (scanResults.isEmpty()) {
                    centerPanel.removeAll();
                    centerPanel.add(new JLabel("未发现需要补全的剧集，所有vsmeta信息完整", JLabel.CENTER));
                    centerPanel.revalidate();
                    centerPanel.repaint();
                    scanBtn.setEnabled(true);
                    targetPathCb.setEnabled(true);
                    tempPathCb.setEnabled(true);
                } else {
                    showResultTable();
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(VSmetaCompleterDialog.this,
                    "扫描失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                scanBtn.setEnabled(true);
                targetPathCb.setEnabled(true);
                tempPathCb.setEnabled(true);
            }
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
        resultTable.getColumnModel().getColumn(2).setPreferredWidth(200);
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
        JButton completeBtn = new JButton("确认补全");
        completeBtn.addActionListener(e -> startComplete());
        rightBtns.add(completeBtn);

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

        String tempPath = getTempPath();

        centerPanel.removeAll();
        topPanel.removeAll();
        topPanel.add(buildPhase1Panel(targetDir.getAbsolutePath()), BorderLayout.CENTER);
        topPanel.revalidate();
        topPanel.repaint();
        scanBtn.setEnabled(false);
        targetPathCb.setEnabled(false);
        tempPathCb.setEnabled(false);

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

        new CompleteWorker(selected, tempPath).execute();
    }

    private class CompleteWorker extends SwingWorker<Void, String> {

        private final List<IncompleteEpisode> items;
        private final String tempFolder;
        private int successCount;
        private int failCount;

        CompleteWorker(List<IncompleteEpisode> items, String tempFolder) {
            this.items = items;
            this.tempFolder = tempFolder;
        }

        @Override
        protected Void doInBackground() throws Exception {
            int total = items.size();
            for (int i = 0; i < total; i++) {
                IncompleteEpisode ie = items.get(i);
                String status = ie.hasVsmeta
                    ? (ie.missingFields != null && !ie.missingFields.isEmpty()
                        ? " 缺: " + String.join(",", ie.missingFields)
                        : " 信息不全")
                    : " 缺失vsmeta";
                publish("[" + (i + 1) + "/" + total + "] " + ie.videoFile.getAbsolutePath() + status);

                File folder = ie.videoFile.getParentFile();
                String cacheKey = folder.getAbsolutePath() + "|" + ie.season;

                Optional<VSmeta> cached = templateCache.get(cacheKey);
                if (cached == null || !cached.isPresent()) {
                    publish("  跳过: " + cacheKey + " 无模板");
                    failCount++;
                    continue;
                }
                VSmeta template = cached.get();
                publish("  使用模板: " + folder.getAbsolutePath()
                    + "/S" + String.format("%02d", template.season)
                    + "E" + String.format("%02d", template.episode) + ".vsmeta");

                try {
                    VSmeta meta;
                    File vsmetaFile;
                    if (ie.hasVsmeta) {
                        meta = metaCache.get(ie.vsmetaFile);
                        vsmetaFile = ie.vsmetaFile;
                    } else {
                        meta = new VSmeta();
                        meta.type = VSmeta.TypeEpisode;
                        vsmetaFile = new File(folder, ie.videoFile.getName() + ".vsmeta");
                    }
                    copyFromTemplate(meta, template, ie);
                    meta.write(vsmetaFile);
                    publish("  写入 vsmeta 成功: " + vsmetaFile.getAbsolutePath());

                    Path src = ie.videoFile.toPath();
                    Path dest = Paths.get(tempFolder, ie.videoFile.getName());
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

        /** 分析指定文件夹（仅本层），为每个season找最小episode的可解析vsmeta，一并填入缓存 */
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

    /** 判断vsmeta是否"信息不全"：返回缺失字段列表，空列表表示完整 */
    private static List<String> checkMissingFields(VSmeta meta, VSmeta ref) {
        List<String> missing = new ArrayList<>();
        // 剧集标题缺失放在最前面
        if (meta.episodeTitle == null || meta.episodeTitle.isEmpty()) missing.add("剧集标题");
        if (ref.year != 0 && meta.year == 0) missing.add("年份");
        if (isNotEmpty(ref.casts) && isEmpty(meta.casts)) missing.add("演员");
        if (isNotEmpty(ref.directors) && isEmpty(meta.directors)) missing.add("导演");
        if (isNotEmpty(ref.genres) && isEmpty(meta.genres)) missing.add("类型");
        if (isNotEmpty(ref.episodeReleaseDate) && isEmpty(meta.episodeReleaseDate)) missing.add("发布日期");
        if (isNotEmpty(ref.chapterSummary) && isEmpty(meta.chapterSummary)) missing.add("简介");
        return missing;
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

    /** 从模板复制元数据到目标vsmeta，目标已有值的字段不覆盖 */
    private static void copyFromTemplate(VSmeta target, VSmeta template, IncompleteEpisode ie) {
        if (target.showTitle == null || target.showTitle.isEmpty())
            target.showTitle = template.showTitle;
        if (target.showTitle2 == null || target.showTitle2.isEmpty())
            target.showTitle2 = template.showTitle2;
        if (target.year == 0) target.year = template.year;
        if (target.tvShowYear == 0) target.tvShowYear = template.tvShowYear;
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
        if (target.timestamp == null) target.timestamp = template.timestamp;
        target.season = ie.season;
        target.episode = ie.episode;
        if (target.episodeTitle == null || target.episodeTitle.isEmpty()) {
            target.episodeTitle = ie.episode == 0 ? "SP" : "第" + ie.episode + "集";
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

    static class IncompleteEpisode {
        File videoFile;
        File vsmetaFile;
        boolean hasVsmeta;
        int season;
        int episode;
        List<String> missingFields; // 信息不全的具体字段列表，null=缺失vsmeta
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
                case 2:
                    if (ie.missingFields != null && !ie.missingFields.isEmpty())
                        return String.join(", ", ie.missingFields) + " 缺失";
                    return ie.hasVsmeta ? "未知" : "缺失vsmeta";
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
