package com.lvt4j.spider4videostation.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;

import com.lvt4j.spider4videostation.Spider4VideoStationApp;
import com.lvt4j.spider4videostation.service.DsmApiClient;
import com.lvt4j.spider4videostation.service.DsmApiClient.InvalidVideo;
import com.lvt4j.spider4videostation.service.DsmApiClient.Library;
import com.lvt4j.spider4videostation.service.DsmApiClient.PageResult;
import com.lvt4j.spider4videostation.service.DsmApiClient.TVShow;

public class VSCleanupDialog extends JDialog {

    private final DsmApiClient client;

    private JComboBox<Library> libraryCb;
    private JButton startBtn;
    private JPanel centerPanel;

    private JProgressBar progressBar;
    private JLabel progressLabel;

    public VSCleanupDialog(JFrame parent, DsmApiClient client) {
        super(parent, "VS无效视频清理", true);
        this.client = client;
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
        libraryCb.setPreferredSize(new Dimension(250, 28));
        panel.add(libraryCb);
        startBtn = new JButton("开始检查");
        startBtn.addActionListener(e -> startScan());
        panel.add(startBtn);
        return panel;
    }

    private void loadLibraries() {
        libraryCb.setEnabled(false);
        startBtn.setEnabled(false);
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
                    startBtn.setEnabled(true);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(VSCleanupDialog.this,
                        "加载视频库失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ==================== Phase 2: Scan ====================

    private void startScan() {
        Library lib = (Library) libraryCb.getSelectedItem();
        if (lib == null) return;

        libraryCb.setEnabled(false);
        startBtn.setEnabled(false);

        centerPanel.removeAll();
        progressLabel = new JLabel("正在检查...");
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressPanel.add(progressLabel, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.setBorder(new EmptyBorder(20, 0, 0, 0));
        centerPanel.add(progressPanel, BorderLayout.NORTH);
        centerPanel.revalidate();
        centerPanel.repaint();

        new ScanWorker(lib).execute();
    }

    private class ScanWorker extends SwingWorker<List<InvalidVideo>, String> {

        private final Library lib;
        private int totalFiles = 0;
        private int checkedFiles = 0;

        ScanWorker(Library lib) { this.lib = lib; }

        @Override
        protected List<InvalidVideo> doInBackground() throws Exception {
            List<InvalidVideo> allVideos = collectAllVideos();
            totalFiles = allVideos.size();
            publish("0/" + totalFiles);

            List<InvalidVideo> invalid = new ArrayList<>();
            int batchSize = 100;
            for (int i = 0; i < allVideos.size(); i += batchSize) {
                int end = Math.min(i + batchSize, allVideos.size());
                List<InvalidVideo> batch = allVideos.subList(i, end);
                List<String> paths = new ArrayList<>();
                for (InvalidVideo v : batch) paths.add(v.sharepath);
                Map<String, Boolean> exists = client.checkFilesExist(paths);
                for (InvalidVideo v : batch) {
                    Boolean e = exists.get(v.sharepath);
                    if (e == null || !e) invalid.add(v);
                }
                checkedFiles = end;
                publish(checkedFiles + "/" + totalFiles);
            }
            return invalid;
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
                    publish("加载视频列表... " + all.size());
                }
            } else if ("tvshow".equals(type)) {
                List<TVShow> shows = new ArrayList<>();
                int offset = 0;
                while (true) {
                    PageResult<TVShow> page = client.listTVShows(lib.id, offset, 500);
                    shows.addAll(page.items);
                    offset += 500;
                    if (offset >= page.total) break;
                }
                publish("加载视频列表... " + shows.size() + " 部剧集");
                for (TVShow show : shows) {
                    int epOffset = 0;
                    while (true) {
                        PageResult<InvalidVideo> page = client.listEpisodes(lib.id, show.id, show.title, epOffset, 500);
                        all.addAll(page.items);
                        epOffset += 500;
                        if (epOffset >= page.total) break;
                    }
                    publish("加载视频列表... " + all.size() + " 集");
                }
            } else if ("home_video".equals(type)) {
                int offset = 0;
                while (true) {
                    PageResult<InvalidVideo> page = client.listHomeVideos(lib.id, offset, 500);
                    all.addAll(page.items);
                    offset += 500;
                    if (offset >= page.total) break;
                    publish("加载视频列表... " + all.size());
                }
            }
            return all;
        }

        @Override
        protected void process(List<String> chunks) {
            String last = chunks.get(chunks.size() - 1);
            progressLabel.setText("正在检查... " + last);
            if (totalFiles > 0 && last.contains("/")) {
                try {
                    int done = Integer.parseInt(last.split("/")[0]);
                    progressBar.setValue(done * 100 / totalFiles);
                    progressBar.setString(done + "/" + totalFiles);
                } catch (NumberFormatException ignored) {
                    progressBar.setString(last);
                }
            } else {
                progressBar.setString(last);
            }
        }

        @Override
        protected void done() {
            try {
                List<InvalidVideo> invalid = get();
                showResultTable(invalid);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(VSCleanupDialog.this,
                    "检查失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                libraryCb.setEnabled(true);
                startBtn.setEnabled(true);
            }
        }
    }

    // ==================== Phase 3: Result Table ====================

    private void showResultTable(List<InvalidVideo> invalid) {
        centerPanel.removeAll();

        if (invalid.isEmpty()) {
            centerPanel.add(new JLabel("未发现无效视频，该视频库所有文件均存在。"), BorderLayout.CENTER);
            libraryCb.setEnabled(true);
            startBtn.setText("重新检查");
            startBtn.setEnabled(true);
            centerPanel.revalidate();
            centerPanel.repaint();
            return;
        }

        JLabel infoLabel = new JLabel("发现 " + invalid.size() + " 个无效视频:");
        infoLabel.setBorder(new EmptyBorder(0, 0, 5, 0));

        InvalidVideoTableModel model = new InvalidVideoTableModel(invalid);
        JTable table = new JTable(model);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(0).setMinWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(500);
        Font cjk = Spider4VideoStationApp.getCJKFont();
        if (cjk != null) table.setFont(cjk.deriveFont(12f));
        JScrollPane tableScroll = new JScrollPane(table);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 5));
        JButton selectAllBtn = new JButton("全选");
        selectAllBtn.addActionListener(e -> { model.setAll(true); });
        JButton deselectAllBtn = new JButton("取消全选");
        deselectAllBtn.addActionListener(e -> { model.setAll(false); });
        JButton removeBtn = new JButton("移除选中");
        removeBtn.addActionListener(e -> {
            model.removeChecked();
            infoLabel.setText("剩余 " + model.getRowCount() + " 个无效视频:");
            if (model.getRowCount() == 0) {
                JOptionPane.showMessageDialog(this, "已清空列表", "提示", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        JButton cleanBtn = new JButton("确认清理");
        cleanBtn.addActionListener(e -> {
            List<InvalidVideo> toClean = model.getChecked();
            if (toClean.isEmpty()) {
                JOptionPane.showMessageDialog(this, "列表为空", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(this,
                "确认删除 " + toClean.size() + " 个无效视频条目？\n此操作将从 Video Station 数据库中移除这些记录。",
                "确认清理", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.OK_OPTION) {
                startCleanup(toClean);
            }
        });
        btnPanel.add(selectAllBtn);
        btnPanel.add(deselectAllBtn);
        btnPanel.add(removeBtn);
        btnPanel.add(cleanBtn);

        centerPanel.add(infoLabel, BorderLayout.NORTH);
        centerPanel.add(tableScroll, BorderLayout.CENTER);
        centerPanel.add(btnPanel, BorderLayout.SOUTH);

        libraryCb.setEnabled(true);
        startBtn.setText("重新检查");
        startBtn.setEnabled(true);

        centerPanel.revalidate();
        centerPanel.repaint();
    }

    private static class InvalidVideoTableModel extends AbstractTableModel {
        private final List<InvalidVideo> data;
        private final List<Boolean> checked;

        InvalidVideoTableModel(List<InvalidVideo> data) {
            this.data = new ArrayList<>(data);
            this.checked = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) checked.add(true);
        }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return 3; }
        @Override public String getColumnName(int col) {
            switch (col) {
                case 0: return "✓";
                case 1: return "名称";
                case 2: return "文件地址";
                default: return "";
            }
        }
        @Override public Class<?> getColumnClass(int col) {
            return col == 0 ? Boolean.class : String.class;
        }
        @Override public boolean isCellEditable(int row, int col) { return col == 0; }

        @Override
        public Object getValueAt(int row, int col) {
            switch (col) {
                case 0: return checked.get(row);
                case 1: return data.get(row).title;
                case 2: return data.get(row).sharepath;
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

        void removeChecked() {
            for (int i = data.size() - 1; i >= 0; i--) {
                if (checked.get(i)) { data.remove(i); checked.remove(i); }
            }
            fireTableDataChanged();
        }

        List<InvalidVideo> getAll() { return new ArrayList<>(data); }

        List<InvalidVideo> getChecked() {
            List<InvalidVideo> result = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                if (checked.get(i)) result.add(data.get(i));
            }
            return result;
        }
    }

    // ==================== Phase 4: Cleanup ====================

    private void startCleanup(List<InvalidVideo> toClean) {
        centerPanel.removeAll();
        libraryCb.setEnabled(false);
        startBtn.setEnabled(false);

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
                int total = toClean.size();
                for (int i = 0; i < total; i++) {
                    InvalidVideo v = toClean.get(i);
                    publish("[" + (i + 1) + "/" + total + "] 删除 " + v.sharepath);
                    try {
                        List<Integer> ids = Collections.singletonList(v.id);
                        switch (v.type) {
                            case "movie": client.deleteMovies(ids); break;
                            case "episode": client.deleteEpisodes(ids); break;
                            case "home_video": client.deleteHomeVideos(ids); break;
                        }
                        successCount++;
                        publish(" 成功");
                    } catch (Exception e) {
                        failCount++;
                        publish(" 失败: " + e.getMessage());
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
