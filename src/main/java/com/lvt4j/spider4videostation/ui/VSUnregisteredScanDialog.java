package com.lvt4j.spider4videostation.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;

import com.lvt4j.spider4videostation.Spider4VideoStationApp;
import com.lvt4j.spider4videostation.service.ConfigService;
import com.lvt4j.spider4videostation.service.DsmApiClient;
import com.lvt4j.spider4videostation.service.DsmApiClient.FileInfo;
import com.lvt4j.spider4videostation.service.DsmApiClient.InvalidVideo;
import com.lvt4j.spider4videostation.service.DsmApiClient.Library;
import com.lvt4j.spider4videostation.service.DsmApiClient.PageResult;
import com.lvt4j.spider4videostation.service.DsmApiClient.TVShow;

public class VSUnregisteredScanDialog extends JDialog {

    private final DsmApiClient client;
    private final ConfigService configService;

    private JComboBox<Library> libraryCb;
    private JTextField tempFolderField;
    private JButton scanBtn;
    private JPanel centerPanel;

    private JProgressBar progressBar;
    private JLabel progressLabel;

    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>();
    static {
        String[] exts = {"mkv", "mp4", "avi", "ts", "m2ts", "mts", "mov", "wmv", "flv",
            "m4v", "mpg", "mpeg", "rmvb", "rm", "iso", "vob", "divx", "webm", "ogv", "asf", "3gp"};
        for (String ext : exts) VIDEO_EXTENSIONS.add(ext);
    }

    public VSUnregisteredScanDialog(JFrame parent, DsmApiClient client, ConfigService configService) {
        super(parent, "VS未注册视频扫描", true);
        this.client = client;
        this.configService = configService;
        setSize(900, 600);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(5, 5));

        JPanel topPanel = buildTopPanel();
        add(topPanel, BorderLayout.NORTH);

        centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(new EmptyBorder(0, 10, 10, 10));
        add(centerPanel, BorderLayout.CENTER);

        loadLibraries();
    }

    private JPanel buildTopPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        panel.add(new JLabel("视频库:"));
        libraryCb = new JComboBox<>();
        libraryCb.setPreferredSize(new Dimension(180, 28));
        panel.add(libraryCb);

        scanBtn = new JButton("开始扫描");
        scanBtn.addActionListener(e -> startScan());
        panel.add(scanBtn);

        String savedTempFolder = configService.gets(
            Collections.singletonList("vsUnregisteredScanTempFolder"))
            .getOrDefault("vsUnregisteredScanTempFolder", "");
        panel.add(new JLabel("中转文件夹:"));
        tempFolderField = new JTextField(savedTempFolder, 22);
        tempFolderField.setPreferredSize(new Dimension(180, 28));
        tempFolderField.setToolTipText("如 /video/temp，用于触发文件变动");
        panel.add(tempFolderField);
        return panel;
    }

    private void loadLibraries() {
        libraryCb.setEnabled(false);
        scanBtn.setEnabled(false);
        new SwingWorker<List<Library>, Void>() {
            @Override
            protected List<Library> doInBackground() throws Exception {
                return client.listLibraries();
            }
            @Override
            protected void done() {
                try {
                    for (Library lib : get()) libraryCb.addItem(lib);
                    libraryCb.setEnabled(true);
                    scanBtn.setEnabled(true);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(VSUnregisteredScanDialog.this,
                        "加载视频库失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static boolean isVideoFile(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return VIDEO_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase());
    }

    // ==================== Scan ====================

    private void startScan() {
        Library lib = (Library) libraryCb.getSelectedItem();
        if (lib == null) return;

        libraryCb.setEnabled(false);
        scanBtn.setEnabled(false);

        centerPanel.removeAll();
        progressLabel = new JLabel("正在获取视频库文件夹路径...");
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressPanel.add(progressLabel, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.setBorder(new EmptyBorder(20, 0, 0, 0));
        centerPanel.add(progressPanel, BorderLayout.NORTH);
        centerPanel.revalidate();
        centerPanel.repaint();

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return client.getLibraryFolderPath(lib.type, lib.id);
            }
            @Override
            protected void done() {
                try {
                    String scanPath = get();
                    if (scanPath == null || scanPath.isEmpty()) {
                        scanPath = JOptionPane.showInputDialog(VSUnregisteredScanDialog.this,
                            "无法获取视频库路径，请手动输入要扫描的文件夹路径：\n（如 /video/Movie）",
                            "输入扫描路径", JOptionPane.QUESTION_MESSAGE);
                        if (scanPath == null || scanPath.trim().isEmpty()) {
                            libraryCb.setEnabled(true);
                            scanBtn.setEnabled(true);
                            return;
                        }
                        scanPath = scanPath.trim();
                    }
                    progressLabel.setText("正在扫描 " + scanPath + " ...");
                    new ScanWorker(lib, scanPath).execute();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(VSUnregisteredScanDialog.this,
                        "获取库路径失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    libraryCb.setEnabled(true);
                    scanBtn.setEnabled(true);
                }
            }
        }.execute();
    }

    private class ScanWorker extends SwingWorker<List<FileInfo>, String> {

        private final Library lib;
        private final String scanPath;

        ScanWorker(Library lib, String scanPath) {
            this.lib = lib;
            this.scanPath = scanPath;
        }

        @Override
        protected List<FileInfo> doInBackground() throws Exception {
            publish("库类型: " + lib.type + ", 库ID: " + lib.id);
            Set<String> registeredPaths = collectRegisteredPaths();
            publish("VS已注册: " + registeredPaths.size() + " 条记录");
            if (!registeredPaths.isEmpty()) {
                publish("VS示例路径: " + registeredPaths.iterator().next());
            }
            return scanDiskFiles(registeredPaths);
        }

        private Set<String> collectRegisteredPaths() throws Exception {
            Set<String> paths = new HashSet<>();
            for (InvalidVideo v : collectAllVideos()) paths.add(v.sharepath);
            return paths;
        }

        private List<InvalidVideo> collectAllVideos() throws Exception {
            List<InvalidVideo> all = new ArrayList<>();
            String type = lib.type;
            if ("movie".equals(type)) {
                int offset = 0;
                while (true) {
                    PageResult<InvalidVideo> page = client.listMovies(lib.id, offset, 500);
                    all.addAll(page.items);
                    offset += 500;
                    if (offset >= page.total) break;
                    publish("加载VS视频列表... " + all.size());
                }
            } else if ("tvshow".equals(type)) {
                List<TVShow> shows = new ArrayList<>();
                int offset = 0;
                while (true) {
                    PageResult<TVShow> page = client.listTVShows(lib.id, offset, 500);
                    shows.addAll(page.items);
                    offset += 500;
                    if (offset >= page.total) break;
                    publish("加载剧集列表... " + shows.size() + " 部");
                }
                List<InvalidVideo> syncAll = Collections.synchronizedList(all);
                AtomicInteger completed = new AtomicInteger(0);
                ExecutorService exec = Executors.newFixedThreadPool(12);
                for (TVShow show : shows) {
                    exec.submit(() -> {
                        try {
                            int epOffset = 0;
                            while (true) {
                                PageResult<InvalidVideo> page = client.listEpisodes(lib.id, show.id, show.title, epOffset, 500);
                                syncAll.addAll(page.items);
                                epOffset += 500;
                                if (epOffset >= page.total) break;
                            }
                        } catch (Exception e) {
                            publish("加载剧集 " + show.title + " 失败: " + e.getMessage());
                        }
                        publish("加载VS视频列表... " + syncAll.size() + " 集 (" + completed.incrementAndGet() + "/" + shows.size() + ")");
                    });
                }
                exec.shutdown();
                exec.awaitTermination(30, TimeUnit.MINUTES);
            } else if ("home_video".equals(type)) {
                int offset = 0;
                while (true) {
                    PageResult<InvalidVideo> page = client.listHomeVideos(lib.id, offset, 500);
                    all.addAll(page.items);
                    offset += 500;
                    if (offset >= page.total) break;
                    publish("加载VS视频列表... " + all.size());
                }
            } else {
                publish("警告: 未知库类型 " + type + "，将尝试按movie方式获取");
                int offset = 0;
                while (true) {
                    PageResult<InvalidVideo> page = client.listMovies(lib.id, offset, 500);
                    all.addAll(page.items);
                    offset += 500;
                    if (offset >= page.total) break;
                    publish("加载VS视频列表... " + all.size());
                }
            }
            return all;
        }

        private List<FileInfo> scanDiskFiles(Set<String> registeredPaths) throws Exception {
            List<FileInfo> unregistered = Collections.synchronizedList(new ArrayList<>());
            ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
            queue.add(scanPath);
            AtomicInteger pending = new AtomicInteger(1);
            AtomicInteger scanned = new AtomicInteger(0);
            AtomicBoolean done = new AtomicBoolean(false);

            int threads = 16;
            Thread[] workers = new Thread[threads];
            for (int t = 0; t < threads; t++) {
                workers[t] = new Thread(() -> {
                    while (!done.get()) {
                        String dir = queue.poll();
                        if (dir == null) {
                            if (pending.get() == 0) done.set(true);
                            continue;
                        }
                        try {
                            List<FileInfo> files = client.listFolder(dir);
                            int newDirs = 0;
                            for (FileInfo f : files) {
                                if (f.isdir) {
                                    queue.add(f.path);
                                    pending.incrementAndGet();
                                    newDirs++;
                                } else if (isVideoFile(f.name)) {
                                    int c = scanned.incrementAndGet();
                                    if (c == 1) publish("磁盘示例路径: " + f.path);
                                    if (!registeredPaths.contains(f.path)) {
                                        unregistered.add(f);
                                    }
                                }
                            }
                            if (newDirs == 0) publish("已扫描 " + scanned.get() + " 个视频文件, 未注册 " + unregistered.size());
                        } catch (Exception e) {
                            publish("跳过无法访问: " + dir);
                        }
                        pending.decrementAndGet();
                    }
                }, "dir-scanner-" + t);
                workers[t].start();
            }
            for (Thread w : workers) w.join();
            return unregistered;
        }

        @Override
        protected void process(List<String> chunks) {
            String last = chunks.get(chunks.size() - 1);
            progressLabel.setText(last);
            progressBar.setIndeterminate(true);
            progressBar.setString(last);
        }

        @Override
        protected void done() {
            try {
                List<FileInfo> unregistered = get();
                showResultTable(unregistered, scanPath);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(VSUnregisteredScanDialog.this,
                    "扫描失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                libraryCb.setEnabled(true);
                scanBtn.setEnabled(true);
            }
        }
    }

    // ==================== Result Table ====================

    private static class FileTableModel extends AbstractTableModel {
        private final List<FileInfo> data;
        private final List<Boolean> checked;

        FileTableModel(List<FileInfo> data) {
            this.data = new ArrayList<>(data);
            this.checked = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) checked.add(true);
        }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return 4; }
        @Override public String getColumnName(int col) {
            switch (col) {
                case 0: return "✓";
                case 1: return "文件名";
                case 2: return "路径";
                case 3: return "大小";
                default: return "";
            }
        }
        @Override public Class<?> getColumnClass(int col) {
            return col == 0 ? Boolean.class : String.class;
        }
        @Override public boolean isCellEditable(int row, int col) { return col == 0; }

        @Override
        public Object getValueAt(int row, int col) {
            FileInfo f = data.get(row);
            switch (col) {
                case 0: return checked.get(row);
                case 1: return f.name;
                case 2: return f.path;
                case 3: return formatSize(f.size);
                default: return "";
            }
        }
        @Override
        public void setValueAt(Object val, int row, int col) {
            if (col == 0) { checked.set(row, (Boolean) val); fireTableCellUpdated(row, col); }
        }

        void setAll(boolean val) {
            for (int i = 0; i < checked.size(); i++) checked.set(i, val);
            fireTableDataChanged();
        }

        List<FileInfo> getChecked() {
            List<FileInfo> result = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                if (checked.get(i)) result.add(data.get(i));
            }
            return result;
        }

        private static String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    private void showResultTable(List<FileInfo> unregistered, String scanPath) {
        centerPanel.removeAll();

        if (unregistered.isEmpty()) {
            centerPanel.add(new JLabel("扫描 " + scanPath + "：所有视频文件均已注册到 Video Station。"), BorderLayout.CENTER);
            libraryCb.setEnabled(true);
            scanBtn.setEnabled(true);
            centerPanel.revalidate();
            centerPanel.repaint();
            return;
        }

        JLabel infoLabel = new JLabel("扫描 " + scanPath + "，发现 " + unregistered.size() + " 个未注册的视频文件:");
        infoLabel.setBorder(new EmptyBorder(0, 0, 5, 0));

        FileTableModel model = new FileTableModel(unregistered);
        JTable table = new JTable(model);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(0).setMinWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(450);
        table.getColumnModel().getColumn(3).setPreferredWidth(80);
        table.setAutoCreateRowSorter(true);
        Font cjk = Spider4VideoStationApp.getCJKFont();
        if (cjk != null) table.setFont(cjk.deriveFont(12f));
        JScrollPane tableScroll = new JScrollPane(table);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 5));
        JButton selectAllBtn = new JButton("全选");
        selectAllBtn.addActionListener(e -> model.setAll(true));
        JButton deselectAllBtn = new JButton("取消全选");
        deselectAllBtn.addActionListener(e -> model.setAll(false));
        JButton triggerBtn = new JButton("触发视频变动");
        triggerBtn.addActionListener(e -> {
            List<FileInfo> toTrigger = model.getChecked();
            if (toTrigger.isEmpty()) {
                JOptionPane.showMessageDialog(this, "请至少勾选一个文件", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String tempFolder = tempFolderField.getText().trim();
            if (tempFolder.isEmpty()) {
                JOptionPane.showMessageDialog(this, "请先填写中转文件夹路径", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            startTriggerChange(toTrigger, tempFolder);
        });
        btnPanel.add(selectAllBtn);
        btnPanel.add(deselectAllBtn);
        btnPanel.add(triggerBtn);

        centerPanel.add(infoLabel, BorderLayout.NORTH);
        centerPanel.add(tableScroll, BorderLayout.CENTER);
        centerPanel.add(btnPanel, BorderLayout.SOUTH);

        libraryCb.setEnabled(true);
        scanBtn.setEnabled(true);

        centerPanel.revalidate();
        centerPanel.repaint();
    }

    // ==================== Trigger Change ====================

    private void startTriggerChange(List<FileInfo> files, String tempFolder) {
        System.setProperty("vsUnregisteredScanTempFolder", tempFolder);
        try { configService.set("vsUnregisteredScanTempFolder", tempFolder); } catch (Exception ignored) {}

        centerPanel.removeAll();
        libraryCb.setEnabled(false);
        scanBtn.setEnabled(false);

        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        Font cjk = Spider4VideoStationApp.getCJKFont();
        if (cjk != null) logArea.setFont(cjk.deriveFont(12f));
        JScrollPane logScroll = new JScrollPane(logArea);

        JButton closeBtn = new JButton("关闭");
        closeBtn.setEnabled(false);
        closeBtn.addActionListener(e -> dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(closeBtn);

        centerPanel.add(logScroll, BorderLayout.CENTER);
        centerPanel.add(btnPanel, BorderLayout.SOUTH);
        centerPanel.revalidate();
        centerPanel.repaint();

        new SwingWorker<Void, String>() {
            private int successCount = 0;
            private int failCount = 0;

            @Override
            protected Void doInBackground() throws Exception {
                int total = files.size();
                for (int i = 0; i < total; i++) {
                    FileInfo f = files.get(i);
                    publish("[" + (i + 1) + "/" + total + "] 移动 " + f.path + " → " + tempFolder);
                    try {
                        client.moveFiles(Collections.singletonList(f.path), tempFolder);
                        publish("  移动成功，等待10秒...");
                        Thread.sleep(10000);
                        String movedPath = tempFolder + "/" + f.name;
                        publish("  移回 " + movedPath + " → " + f.path);
                        client.moveFiles(Collections.singletonList(movedPath),
                            f.path.substring(0, f.path.lastIndexOf('/')));
                        successCount++;
                        publish("  移回成功，等待10秒...");
                        Thread.sleep(10000);
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
        }.execute();
    }
}
