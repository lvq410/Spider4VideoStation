package com.lvt4j.spider4videostation.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
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
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.lvt4j.spider4videostation.metadata.VSmeta;

/**
 * 元数据文件查看器 —— 统一查看 vsmeta / nfo
 *
 * @author LV on 2023年2月9日
 */
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
        JButton goBtn = new JButton("确认");
        goBtn.addActionListener(e -> navigateTo(pathTf.getText().trim()));
        pathTf.addActionListener(e -> navigateTo(pathTf.getText().trim()));
        navPanel.add(navBtnPanel, BorderLayout.WEST);
        navPanel.add(pathTf, BorderLayout.CENTER);
        navPanel.add(goBtn, BorderLayout.EAST);

        // 左侧文件列表 — .vsmeta + .nfo + 文件夹
        listModel = new DefaultListModel<>();
        fileList = new JList<>(listModel);
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
                    detailPanel.removeAll();
                    GridBagConstraints c = new GridBagConstraints();
                    c.gridx = 0; c.gridy = 0;
                    c.weightx = 1.0; c.weighty = 1.0;
                    c.anchor = GridBagConstraints.NORTHWEST;
                    c.fill = GridBagConstraints.HORIZONTAL;
                    detailPanel.add(built, c);
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

    private static JPanel buildVSmetaPanel(File vsmetaFile) throws Exception {
        VSmeta vsmeta = new VSmeta(vsmetaFile);

        java.util.List<java.awt.Component> rows = new java.util.ArrayList<>();
        addRow(rows, "类型", vsmeta.type == VSmeta.TypeMovie ? "电影" : "剧集");
        addRow(rows, "标题", vsmeta.showTitle);
        if (!isEmpty(vsmeta.episodeTitle) && !vsmeta.episodeTitle.equals(vsmeta.showTitle))
            addRow(rows, "剧集标题", vsmeta.episodeTitle);
        addRow(rows, "年份", vsmeta.year > 0 ? String.valueOf(vsmeta.year) : null);
        addRow(rows, "发布日期", vsmeta.episodeReleaseDate);
        addRow(rows, "简介", vsmeta.chapterSummary);
        addRow(rows, "分级", vsmeta.classification);
        addRow(rows, "类型", listStr(vsmeta.genres));
        addRow(rows, "演员", listStr(vsmeta.casts));
        addRow(rows, "导演", listStr(vsmeta.directors));
        addRow(rows, "编剧", listStr(vsmeta.writers));
        if (vsmeta.type == VSmeta.TypeEpisode) {
            addRow(rows, "季", String.valueOf(vsmeta.season));
            addRow(rows, "集", String.valueOf(vsmeta.episode));
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

    private static JPanel buildNfoPanel(File nfoFile) throws Exception {
        Document doc = Jsoup.parse(nfoFile, "UTF-8");
        File parentDir = nfoFile.getParentFile();

        java.util.List<java.awt.Component> rows = new java.util.ArrayList<>();
        addRow(rows, "标题", ownText(doc, "title"));
        addRow(rows, "标语", ownText(doc, "tagline"));
        addRow(rows, "情节", ownText(doc, "plot"));
        addRow(rows, "首映", ownText(doc, "premiered"));
        addRow(rows, "年份", ownText(doc, "year"));
        addRow(rows, "播出", ownText(doc, "aired"));
        addRow(rows, "季", ownText(doc, "season"));
        addRow(rows, "集", ownText(doc, "episode"));
        addRow(rows, "分级", ownText(doc, "mpaa"));
        addRow(rows, "评分", ownText(doc, "rating"));
        addRow(rows, "类型", listText(doc, "genre"));
        addRow(rows, "演员", listText(doc, "actor"));
        addRow(rows, "导演", listText(doc, "director"));
        addRow(rows, "编剧", listText(doc, "credits"));

        for (Element el : doc.select("thumb"))
            addImageRow(rows, "缩略图", loadImageFile(parentDir, ownText(el)));
        for (Element el : doc.select("fanart"))
            addImageRow(rows, "背景", loadImageFile(parentDir, ownText(el)));

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

    /** 添加文本行 */
    private static void addRow(java.util.List<java.awt.Component> rows, String label, String value) {
        if (isEmpty(value)) return;
        rows.add(new JLabel(label + ":"));
        rows.add(new JLabel("<html>" + value.replace("\n", "<br>") + "</html>"));
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
            String v = el.ownText();
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
}
