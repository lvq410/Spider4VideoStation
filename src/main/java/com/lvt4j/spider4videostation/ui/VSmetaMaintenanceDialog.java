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

import lombok.extern.slf4j.Slf4j;

/**
 * VS剧集元数据维护对话框——合并了元数据补全与缩略图重刷功能。
 * <p>
 * 扫描目标文件夹，同时检测元数据缺失和缩略图问题，统一展示并一次性修复。
 * 元数据补全从同目录同season模板复制字段，缩略图重刷用FFmpeg截取61.8%位置帧。
 * 修复后通过本地文件移出移入触发VS刷新，两种修复共享一次VS刷新以节省时间。
 * </p>
 *
 * @author LV on 2024年6月1日
 */
@Slf4j
public class VSmetaMaintenanceDialog extends JDialog {

    /** 文件名匹配模式：xxx.S01.E01.ext */
    private static final Pattern EpPattern = Pattern.compile(
        "(.+)\\.S(\\d{1,2})\\.E(\\d{1,4})(\\.|$)");

    /** 缩略图扫描模式 */
    private static final String[] THUMB_MODES = {"仅无缩略图", "重复缩略图", "无+重复", "全部重刷"};

    private final ConfigService configService;
    /** 多目标文件夹列表（扫描时填充） */
    private List<File> targetDirs;

    // Phase 1: 参数设置控件
    private DefaultComboBoxModel<String> targetPathModel;
    private JComboBox<String> targetPathCb;
    private DefaultComboBoxModel<String> tempPathModel;
    private JComboBox<String> tempPathCb;
    private JComboBox<String> thumbModeCb;
    private TargetFolderTablePanel targetFolderTable;
    private JButton scanBtn;

    // Phase 2: 扫描进度
    private JLabel progressLb;
    private JProgressBar progressBar;

    // Phase 3: 结果表格
    private JTable resultTable;
    private IssueTableModel tableModel;

    // Phase 4: 执行日志
    private JTextArea logArea;
    private JButton closeBtn;

    private JPanel centerPanel;
    private JPanel topPanel;

    private List<EpisodeIssue> scanResults;
    /** vsmeta文件缓存，key=vsmetaFile，扫描时填充，执行时复用避免重复读取 */
    private final Map<File, VSmeta> metaCache = new HashMap<>();
    /** 模板缓存，key=folderAbsPath|season，value=模板vsmeta文件，需要VSmeta时从metaCache取 */
    private final Map<String, Optional<File>> templateCache = new HashMap<>();

    public VSmetaMaintenanceDialog(Frame owner, ConfigService configService, String initialTargetPath) {
        super(owner, "VS剧集元数据维护", true);
        this.configService = configService;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(1050, 720);
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

    // ==================== Phase 1: 参数设置 ====================

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
        loadRecentPaths("recentMaintenanceTargets", targetPathModel);
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
        // "..." 浏览按钮和 "+" 添加按钮
        JPanel targetBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        JButton targetBrowseBtn = new JButton("...");
        targetBrowseBtn.addActionListener(e -> browseFolder(
            "recentMaintenanceTargets", targetPathModel, targetPathCb));
        targetBtnPanel.add(targetBrowseBtn);
        JButton targetAddBtn = new JButton("+");
        targetAddBtn.setToolTipText("将当前路径添加到目标文件夹列表");
        targetAddBtn.addActionListener(e -> {
            String path = getTargetPath();
            if (!path.isEmpty()) targetFolderTable.addPath(path);
        });
        targetBtnPanel.add(targetAddBtn);
        targetPanel.add(targetBtnPanel, BorderLayout.EAST);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(targetPanel, gbc);

        // 目标文件夹多选表格
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.gridwidth = 2; gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        targetFolderTable = new TargetFolderTablePanel(configService, "maintenanceTargetFolders");
        panel.add(targetFolderTable, gbc);
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 中转文件夹
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("中转文件夹:"), gbc);
        JPanel tempPanel = new JPanel(new BorderLayout(5, 0));
        tempPathModel = new DefaultComboBoxModel<>();
        loadRecentPaths("recentMaintenanceTempFolders", tempPathModel);
        tempPathCb = new JComboBox<>(tempPathModel);
        tempPathCb.setEditable(true);
        tempPanel.add(tempPathCb, BorderLayout.CENTER);
        JButton tempBrowseBtn = new JButton("...");
        tempBrowseBtn.addActionListener(e -> browseFolder(
            "recentMaintenanceTempFolders", tempPathModel, tempPathCb));
        tempPanel.add(tempBrowseBtn, BorderLayout.EAST);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(tempPanel, gbc);

        // 缩略图扫描模式
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.weightx = 0;
        panel.add(new JLabel("缩略图扫描模式:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        thumbModeCb = new JComboBox<>(THUMB_MODES);
        thumbModeCb.setSelectedIndex(2); // 默认"无+重复"
        panel.add(thumbModeCb, gbc);

        // 开始扫描
        gbc.gridx = 1; gbc.gridy = 4;
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
        Object sel = cb.getSelectedItem();
        String initialPath = sel != null ? sel.toString().trim() : "";
        File initialDir = (!initialPath.isEmpty()) ? new File(initialPath) : new File("N:\\");
        FilePickerDialog picker = new FilePickerDialog((Frame) getOwner(), initialDir, true);
        picker.setVisible(true);
        FilePickerDialog.DialogResult result = picker.getResult();
        if (result != null && result.path != null && !result.path.isEmpty()) {
            saveRecentPath(configKey, model, cb, result.path);
        }
    }

    // ==================== Phase 2: 扫描 ====================

    private void startScan() {
        // 从表格获取勾选的目标文件夹列表
        List<String> checkedPaths = targetFolderTable.getCheckedPaths();
        if (checkedPaths.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "请在目标文件夹列表中添加并勾选至少一个文件夹", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String tempPath = getTempPath();
        if (tempPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请选择中转文件夹", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File tempDir = new File(tempPath);
        Path p2 = tempDir.toPath().toAbsolutePath().normalize();
        // 逐个校验目标文件夹存在性和一级目录一致性
        targetDirs = new ArrayList<>();
        for (String tp : checkedPaths) {
            File dir = new File(tp);
            if (!dir.isDirectory()) {
                JOptionPane.showMessageDialog(this, "目标文件夹不存在: " + tp, "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            Path p1 = dir.toPath().toAbsolutePath().normalize();
            if (p1.getNameCount() < 1 || p2.getNameCount() < 1
                    || !p1.getName(0).equals(p2.getName(0))) {
                JOptionPane.showMessageDialog(this,
                    "目标文件夹和中转文件夹必须在同一一级目录下\n目标: " + p1 + "\n中转: " + p2,
                    "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            targetDirs.add(dir);
        }

        saveRecentPath("recentMaintenanceTempFolders", tempPathModel, tempPathCb, tempPath);
        metaCache.clear();
        templateCache.clear();

        scanBtn.setEnabled(false);
        targetPathCb.setEnabled(false);
        tempPathCb.setEnabled(false);
        thumbModeCb.setEnabled(false);
        targetFolderTable.setEnabled(false);

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

    /**
     * 扫描Worker——深度优先遍历目标文件夹，同时检测元数据缺失和缩略图问题。
     * <p>
     * 以视频文件为主入口，解析对应vsmeta，按season分组找模板，
     * 与模板对比检测元数据缺失字段，同时检查缩略图状态。
     * </p>
     */
    private class ScanWorker extends SwingWorker<List<EpisodeIssue>, String> {

        private final List<EpisodeIssue> results = new ArrayList<>();
        private int thumbMode;

        @Override
        protected List<EpisodeIssue> doInBackground() throws Exception {
            thumbMode = thumbModeCb.getSelectedIndex();
            for (File dir : targetDirs) {
                analyzeDir(dir);
            }
            return results;
        }

        /** 深度优先遍历本地目录，同时检测元数据和缩略图问题 */
        private void analyzeDir(File localDir) throws Exception {
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
                analyzeDir(sub);
            }

            if (videoFiles.isEmpty()) return;

            analyzeVideos(localDir, videoFiles, fileNames);
        }

        /**
         * 分析本目录的视频文件：同时检测元数据缺失和缩略图问题。
         * <p>
         * 第一步：按EpPattern解析season/episode，分组排序<br>
         * 第二步：加载所有视频对应的vsmeta到metaCache<br>
         * 第三步：每season找模板（最低episode的可解析vsmeta）→ templateCache<br>
         * 第四步：逐个视频检测元数据和缩略图问题<br>
         * 第五步：清理非结果非模板的缓存释放内存
         * </p>
         */
        private void analyzeVideos(File localDir, List<File> videoFiles, Set<String> fileNames) {
            // 按season分组（跳过episode=0的特殊集）
            Map<Integer, List<File>> seasonFiles = new HashMap<>();
            for (File vf : videoFiles) {
                Matcher m = EpPattern.matcher(vf.getName());
                if (!m.find()) continue;
                int season = Integer.parseInt(m.group(2));
                int episode = Integer.parseInt(m.group(3));
                if (episode == 0) continue;
                seasonFiles.computeIfAbsent(season, k -> new ArrayList<>()).add(vf);
            }
            // 每个season内按episode升序
            for (List<File> files : seasonFiles.values()) {
                files.sort((a, b) -> {
                    Matcher ma = EpPattern.matcher(a.getName());
                    Matcher mb = EpPattern.matcher(b.getName());
                    ma.find(); mb.find();
                    return Integer.compare(
                        Integer.parseInt(ma.group(3)),
                        Integer.parseInt(mb.group(3)));
                });
            }

            // 加载所有视频对应的vsmeta到缓存
            Set<File> loadedHere = new HashSet<>();
            Set<File> keep = new HashSet<>();
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
                    loadedHere.add(vsmetaFile);
                } catch (Exception ignored) {}
                if (parsed % 20 == 0 || parsed == total) {
                    publish("正在分析 " + localDir.getAbsolutePath()
                        + " (" + parsed + "/" + total + ")");
                }
            }

            // 每season找模板：最低episode的可解析vsmeta
            String folderKey = localDir.getAbsolutePath();
            for (Map.Entry<Integer, List<File>> entry : seasonFiles.entrySet()) {
                int season = entry.getKey();
                for (File vf : entry.getValue()) {
                    File vsmetaFile = new File(localDir, vf.getName() + ".vsmeta");
                    VSmeta meta = metaCache.get(vsmetaFile);
                    if (meta != null) {
                        templateCache.put(folderKey + "|" + season, Optional.of(vsmetaFile));
                        keep.add(vsmetaFile);
                        break;
                    }
                }
            }

            // ---- 同时检测元数据和缩略图问题 ----

            // 收集本目录所有缩略图MD5用于重复检测
            Map<String, List<EpisodeIssue>> md5Groups = new HashMap<>();
            List<EpisodeIssue> dirIssues = new ArrayList<>();

            for (File vf : videoFiles) {
                String vsmetaName = vf.getName() + ".vsmeta";
                boolean vsmetaExists = fileNames.contains(vsmetaName);
                File vsmetaFile = new File(localDir, vsmetaName);

                EpisodeIssue issue = new EpisodeIssue();
                issue.videoFile = vf;
                issue.vsmetaFile = vsmetaExists ? vsmetaFile : null;
                issue.hasVsmeta = vsmetaExists;
                parseSeasonEpisode(vf.getName(), issue);

                // ---- 元数据检测 ----
                if (vsmetaExists) {
                    VSmeta meta = metaCache.get(vsmetaFile);
                    if (meta != null) {
                        VSmeta ref = getSeasonRef(folderKey, vf);
                        if (ref != null) {
                            List<String> missing = checkMissingFields(meta, ref);
                            if (!missing.isEmpty()) {
                                issue.needMetaFix = true;
                                issue.missingFields = missing;
                                keep.add(vsmetaFile);
                            }
                        }
                    } else {
                        // vsmeta存在但无法解析
                        issue.needMetaFix = true;
                        keep.add(vsmetaFile);
                    }
                } else {
                    // vsmeta不存在
                    VSmeta ref = getSeasonRef(folderKey, vf);
                    if (ref != null) {
                        issue.needMetaFix = true;
                    }
                }

                // ---- 缩略图检测 ----
                if (vsmetaExists) {
                    VSmeta meta = metaCache.get(vsmetaFile);
                    if (meta != null && meta.type == VSmeta.TypeEpisode) {
                        if (thumbMode == 3) {
                            // 全部重刷模式：所有TypeEpisode的都标记
                            issue.needThumbFix = true;
                            issue.thumbIssue = "需重刷";
                            keep.add(vsmetaFile);
                        } else if (thumbMode == 0 || thumbMode == 2) {
                            // 检测无缩略图
                            if (meta.episodeThumbData == null || meta.episodeThumbData.isEmpty()) {
                                issue.needThumbFix = true;
                                issue.thumbIssue = "无缩略图";
                                keep.add(vsmetaFile);
                            }
                        }
                        // 收集MD5用于重复检测（mode 1 或 2 时需要）
                        if (thumbMode == 1 || thumbMode == 2) {
                            String md5 = meta.episodeThumbMd5 != null ? meta.episodeThumbMd5 : "";
                            if (!md5.isEmpty()) {
                                md5Groups.computeIfAbsent(md5, k -> new ArrayList<>()).add(issue);
                            }
                        }
                    }
                } else {
                    // 无vsmeta也意味着无缩略图
                    if (thumbMode == 0 || thumbMode == 2 || thumbMode == 3) {
                        issue.needThumbFix = true;
                        issue.thumbIssue = "无缩略图";
                    }
                }

                dirIssues.add(issue);
            }

            // 重复缩略图检测（目录维度）
            if (thumbMode == 1 || thumbMode == 2) {
                for (List<EpisodeIssue> dupGroup : md5Groups.values()) {
                    if (dupGroup.size() >= 2) {
                        for (EpisodeIssue issue : dupGroup) {
                            if (!issue.needThumbFix) {
                                issue.needThumbFix = true;
                                issue.thumbIssue = "重复缩略图";
                                if (issue.vsmetaFile != null) keep.add(issue.vsmetaFile);
                            }
                        }
                    }
                }
            }

            // 只有至少一种问题的才加入结果
            for (EpisodeIssue issue : dirIssues) {
                if (issue.needMetaFix || issue.needThumbFix) {
                    results.add(issue);
                }
            }

            // 清理本目录非结果非模板的缓存释放内存
            loadedHere.removeAll(keep);
            metaCache.keySet().removeAll(loadedHere);
        }

        /** 从templateCache查找指定视频文件所属season的模板VSmeta */
        private VSmeta getSeasonRef(String folderKey, File vf) {
            Matcher m = EpPattern.matcher(vf.getName());
            if (!m.find()) return null;
            int s = Integer.parseInt(m.group(2));
            File templateFile = templateCache.getOrDefault(folderKey + "|" + s, Optional.empty()).orElse(null);
            return templateFile != null ? metaCache.get(templateFile) : null;
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
                    centerPanel.add(new JLabel("未发现需要维护的剧集", JLabel.CENTER));
                    centerPanel.revalidate();
                    centerPanel.repaint();
                    enableControls();
                } else {
                    showResultTable();
                }
            } catch (Exception e) {
                log.error("扫描失败", e);
                JOptionPane.showMessageDialog(VSmetaMaintenanceDialog.this,
                    "扫描失败: " + e, "错误", JOptionPane.ERROR_MESSAGE);
                enableControls();
            }
        }
    }

    // ==================== Phase 3: 结果表格 ====================

    private void showResultTable() {
        centerPanel.removeAll();

        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JLabel infoLb = new JLabel("发现 " + scanResults.size() + " 个需要维护的剧集:");
        Font cjk = Spider4VideoStationApp.getCJKFont();
        if (cjk != null) infoLb.setFont(cjk.deriveFont(12f));
        panel.add(infoLb, BorderLayout.NORTH);

        tableModel = new IssueTableModel(scanResults);
        resultTable = new JTable(tableModel);
        resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        resultTable.getColumnModel().getColumn(0).setMinWidth(40);
        resultTable.getColumnModel().getColumn(0).setMaxWidth(40);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(500);
        resultTable.getColumnModel().getColumn(2).setPreferredWidth(200);
        resultTable.getColumnModel().getColumn(3).setPreferredWidth(120);
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
        JButton executeBtn = new JButton("确认执行");
        executeBtn.addActionListener(e -> startExecute());
        rightBtns.add(executeBtn);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(btnPanel, BorderLayout.WEST);
        bottomPanel.add(rightBtns, BorderLayout.EAST);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        centerPanel.add(panel, BorderLayout.CENTER);

        // 重新扫描按钮
        JPanel rescanPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton rescanBtn = new JButton("重新扫描");
        rescanBtn.addActionListener(e -> {
            centerPanel.removeAll();
            centerPanel.revalidate();
            centerPanel.repaint();
            enableControls();
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

    // ==================== Phase 4: 执行修复 ====================

    private void startExecute() {
        List<EpisodeIssue> selected = new ArrayList<>();
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
        topPanel.add(buildPhase1Panel(null), BorderLayout.CENTER);
        topPanel.revalidate();
        topPanel.repaint();
        scanBtn.setEnabled(false);
        targetPathCb.setEnabled(false);
        tempPathCb.setEnabled(false);
        thumbModeCb.setEnabled(false);
        targetFolderTable.setEnabled(false);

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
        logPanel.setBorder(new TitledBorder("执行进度"));
        logPanel.add(logScroll, BorderLayout.CENTER);
        logPanel.add(btnPanel, BorderLayout.SOUTH);
        centerPanel.add(logPanel, BorderLayout.CENTER);
        centerPanel.revalidate();
        centerPanel.repaint();

        new ExecuteWorker(selected, tempPath).execute();
    }

    /**
     * 执行Worker——对每个勾选项依次进行元数据补全和缩略图重刷，共享一次VS刷新。
     */
    private class ExecuteWorker extends SwingWorker<Void, String> {

        private final List<EpisodeIssue> items;
        private final String tempFolder;
        private int successCount;
        private int failCount;

        ExecuteWorker(List<EpisodeIssue> items, String tempFolder) {
            this.items = items;
            this.tempFolder = tempFolder;
        }

        @Override
        protected Void doInBackground() throws Exception {
            int total = items.size();
            for (int i = 0; i < total; i++) {
                EpisodeIssue ie = items.get(i);

                // 构建问题描述
                StringBuilder status = new StringBuilder();
                if (ie.needMetaFix) {
                    if (!ie.hasVsmeta) status.append(" 缺失vsmeta");
                    else if (ie.missingFields != null && !ie.missingFields.isEmpty())
                        status.append(" 缺: ").append(String.join(",", ie.missingFields));
                    else status.append(" 元数据不全");
                }
                if (ie.needThumbFix) {
                    if (status.length() > 0) status.append(";");
                    status.append(" ").append(ie.thumbIssue);
                }
                publish("[" + (i + 1) + "/" + total + "] " + ie.videoFile.getAbsolutePath() + status);

                try {
                    VSmeta meta;
                    File vsmetaFile;
                    if (ie.hasVsmeta) {
                        meta = metaCache.get(ie.vsmetaFile);
                        if (meta == null) {
                            // 缓存丢失时重新读取，解析失败则降级为空VSmeta
                            try {
                                meta = new VSmeta(ie.vsmetaFile);
                            } catch (Exception parseEx) {
                                log.warn("vsmeta解析失败，将重建: {}", ie.vsmetaFile, parseEx);
                                publish("  vsmeta文件损坏，将重建");
                                meta = new VSmeta();
                                meta.type = VSmeta.TypeEpisode;
                            }
                        }
                        vsmetaFile = ie.vsmetaFile;
                    } else {
                        meta = new VSmeta();
                        meta.type = VSmeta.TypeEpisode;
                        vsmetaFile = new File(ie.videoFile.getParentFile(),
                            ie.videoFile.getName() + ".vsmeta");
                    }

                    boolean modified = false;

                    // ---- 修复1：元数据补全（先执行，可能创建新vsmeta）----
                    if (ie.needMetaFix) {
                        File folder = ie.videoFile.getParentFile();
                        String cacheKey = folder.getAbsolutePath() + "|" + ie.season;
                        Optional<File> cachedFile = templateCache.get(cacheKey);
                        if (cachedFile != null && cachedFile.isPresent()) {
                            File templateFile = cachedFile.get();
                            VSmeta template = metaCache.get(templateFile);
                            if (template != null) {
                                publish("  使用模板: " + templateFile.getAbsolutePath());
                                copyFromTemplate(meta, template, ie);
                                modified = true;
                                publish("  元数据补全完成");
                            } else {
                                publish("  跳过元数据补全: 模板缓存丢失");
                            }
                        } else {
                            publish("  跳过元数据补全: 无模板");
                        }
                    }

                    // ---- 修复2：缩略图重刷 ----
                    if (ie.needThumbFix) {
                        try {
                            MediaInfo mediaInfo = FFmpegUtils.mediaInfo(ie.videoFile);
                            long position = (long)(mediaInfo.format.parseDuration() * 0.618);

                            String name = ie.videoFile.getName();
                            int dot = name.lastIndexOf('.');
                            String baseName = dot > 0 ? name.substring(0, dot) : name;
                            File snapshot = new File(ie.videoFile.getParentFile(),
                                baseName + ".thumb.tmp.jpg");

                            // 先尝试 61.8% 位置，失败则用第一帧兜底
                            publish("  截图位置: " + FFmpegUtils.formatDuration(position));
                            FFmpegUtils.snapshot(ie.videoFile,
                                FFmpegUtils.formatDuration(position), snapshot);
                            if ((!snapshot.exists() || snapshot.length() == 0) && position > 0) {
                                publish("  截图失败，尝试第一帧...");
                                FFmpegUtils.snapshot(ie.videoFile, "00:00:00.000", snapshot);
                            }

                            if (snapshot.exists() && snapshot.length() > 0) {
                                meta.episodeThumbData = VSmeta.readImgData(snapshot);
                                meta.episodeThumbMd5 = md5(snapshot);
                                snapshot.delete();
                                modified = true;
                                publish("  缩略图已更新");
                            } else {
                                publish("  缩略图重刷失败: 截图生成失败");
                            }
                        } catch (Exception ex) {
                            log.error("缩略图重刷失败: {}", ie.videoFile, ex);
                            publish("  缩略图重刷失败: " + ex);
                        }
                    }

                    // ---- 单次写入 + 单次VS刷新 ----
                    if (modified) {
                        meta.write(vsmetaFile);
                        publish("  写入vsmeta成功: " + vsmetaFile.getAbsolutePath());

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
                    } else {
                        failCount++;
                        publish("  跳过: 无有效修复操作");
                    }
                } catch (Exception e) {
                    failCount++;
                    log.error("执行失败: {}", ie.videoFile, e);
                    publish("  失败: " + e);
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

    // ==================== 元数据检测与修复方法 ====================

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
//        if (isNotEmpty(ref.chapterSummary) && isEmpty(meta.chapterSummary)) missing.add("简介");
        return missing;
    }

    private static boolean isNotEmpty(String s) { return s != null && !s.isEmpty(); }
    private static boolean isEmpty(String s) { return s == null || s.isEmpty(); }
    private static boolean isNotEmpty(List<?> l) { return l != null && !l.isEmpty(); }
    private static boolean isEmpty(List<?> l) { return l == null || l.isEmpty(); }

    /** 从文件名解析 season 和 episode */
    private static void parseSeasonEpisode(String fileName, EpisodeIssue ie) {
        Matcher m = EpPattern.matcher(fileName);
        if (m.find()) {
            ie.season = Integer.parseInt(m.group(2));
            ie.episode = Integer.parseInt(m.group(3));
        }
    }

    /** 从模板复制元数据到目标vsmeta，目标已有值的字段不覆盖 */
    private static void copyFromTemplate(VSmeta target, VSmeta template, EpisodeIssue ie) {
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
//        if (target.chapterSummary == null || target.chapterSummary.isEmpty())
//            target.chapterSummary = template.chapterSummary;
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

    // ==================== 缩略图工具方法 ====================

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

    // ==================== 通用工具方法 ====================

    /** 启用所有Phase1控件（扫描完成或失败时调用） */
    private void enableControls() {
        scanBtn.setEnabled(true);
        targetPathCb.setEnabled(true);
        tempPathCb.setEnabled(true);
        thumbModeCb.setEnabled(true);
        targetFolderTable.setEnabled(true);
    }

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

    /** 扫描结果条目——表示一个视频文件存在的问题（元数据缺失、缩略图问题，或两者兼有） */
    static class EpisodeIssue {
        File videoFile;           // 视频文件（始终非空）
        File vsmetaFile;          // vsmeta文件（null=不存在）
        boolean hasVsmeta;
        int season;
        int episode;
        // 元数据问题
        boolean needMetaFix;
        List<String> missingFields; // 缺失的字段列表，null=整个vsmeta缺失或无法解析
        // 缩略图问题
        boolean needThumbFix;
        String thumbIssue;        // "无缩略图" / "重复缩略图" / "需重刷" / null
    }

    /** 4列结果表格模型：勾选 | 文件路径 | 元数据问题 | 缩略图问题 */
    @SuppressWarnings("serial")
    static class IssueTableModel extends AbstractTableModel {

        private final String[] columns = {"", "文件路径", "元数据问题", "缩略图问题"};
        private final List<EpisodeIssue> data;
        private final List<Boolean> selected;

        IssueTableModel(List<EpisodeIssue> data) {
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
            EpisodeIssue ie = data.get(row);
            switch (col) {
                case 0: return selected.get(row);
                case 1: return ie.videoFile.getAbsolutePath();
                case 2: // 元数据问题
                    if (!ie.needMetaFix) return "";
                    if (!ie.hasVsmeta) return "缺失vsmeta";
                    if (ie.missingFields != null && !ie.missingFields.isEmpty())
                        return String.join(", ", ie.missingFields) + " 缺失";
                    return "信息不全";
                case 3: // 缩略图问题
                    if (!ie.needThumbFix) return "";
                    return ie.thumbIssue != null ? ie.thumbIssue : "";
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
