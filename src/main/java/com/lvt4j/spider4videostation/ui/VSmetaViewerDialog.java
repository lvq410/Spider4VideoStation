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
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import com.lvt4j.spider4videostation.metadata.VSmeta;

/**
 * vsmeta 文件查看器
 *
 * @author LV on 2023年2月9日
 */
public class VSmetaViewerDialog extends JDialog {

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

    public VSmetaViewerDialog(Frame owner, File initialDir) {
        super(owner, "vsmeta 查看器", true);
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

        // 左侧文件列表 — 仅 .vsmeta
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
                } else if (!f.getName().toLowerCase().endsWith(".vsmeta")) {
                    continue;
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

    private void showDetail(File vsmetaFile) {
        detailPanel.removeAll();
        try {
            VSmeta vsmeta = new VSmeta(vsmetaFile);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(3, 5, 3, 5);
            gbc.anchor = GridBagConstraints.NORTHWEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            int row = 0;

            row = addField(detailPanel, gbc, row, "类型", vsmeta.type == VSmeta.TypeMovie ? "电影" : "剧集");
            row = addField(detailPanel, gbc, row, "标题", vsmeta.showTitle);
            if (!isEmpty(vsmeta.episodeTitle) && !vsmeta.episodeTitle.equals(vsmeta.showTitle))
                row = addField(detailPanel, gbc, row, "剧集标题", vsmeta.episodeTitle);
            row = addField(detailPanel, gbc, row, "年份", vsmeta.year > 0 ? String.valueOf(vsmeta.year) : null);
            row = addField(detailPanel, gbc, row, "发布日期", vsmeta.episodeReleaseDate);
            row = addField(detailPanel, gbc, row, "简介", vsmeta.chapterSummary);
            row = addField(detailPanel, gbc, row, "分级", vsmeta.classification);
            row = addField(detailPanel, gbc, row, "类型", listStr(vsmeta.genres));
            row = addField(detailPanel, gbc, row, "演员", listStr(vsmeta.casts));
            row = addField(detailPanel, gbc, row, "导演", listStr(vsmeta.directors));
            row = addField(detailPanel, gbc, row, "编剧", listStr(vsmeta.writers));
            if (vsmeta.type == VSmeta.TypeEpisode) {
                row = addField(detailPanel, gbc, row, "季", String.valueOf(vsmeta.season));
                row = addField(detailPanel, gbc, row, "集", String.valueOf(vsmeta.episode));
            }

            // 图片
            if (!isEmpty(vsmeta.episodeThumbData)) {
                row = addImageField(detailPanel, gbc, row, "缩略图", vsmeta.episodeThumbData);
            }
            if (!isEmpty(vsmeta.posterData)) {
                row = addImageField(detailPanel, gbc, row, "海报", vsmeta.posterData);
            }
            if (!isEmpty(vsmeta.backdropData)) {
                row = addImageField(detailPanel, gbc, row, "背景", vsmeta.backdropData);
            }

            // 占位撑开垂直空间
            gbc.gridy = row;
            gbc.weighty = 1.0;
            detailPanel.add(new JLabel(), gbc);
        } catch (Exception e) {
            detailPanel.add(new JLabel("解析失败: " + e.getMessage()));
        }
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private static int addField(JPanel panel, GridBagConstraints gbc, int row, String label, String value) {
        if (isEmpty(value)) return row;
        gbc.gridx = 0; gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label + ":"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel valLb = new JLabel("<html>" + value.replace("\n", "<br>") + "</html>");
        panel.add(valLb, gbc);
        return row + 1;
    }

    private static int addImageField(JPanel panel, GridBagConstraints gbc, int row, String label, String base64) {
        try {
            byte[] data = Base64.getDecoder().decode(base64.replace("\n", "").replace("\r", ""));
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img == null) return addField(panel, gbc, row, label, "不支持的图片格式(需webp插件)");
            Image scaled = img;
            if (img.getWidth() > 400) {
                int h = img.getHeight() * 400 / img.getWidth();
                scaled = img.getScaledInstance(400, h, Image.SCALE_SMOOTH);
            }
            gbc.gridx = 0; gbc.gridy = row;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            gbc.gridwidth = 1;
            panel.add(new JLabel(label + ":"), gbc);
            gbc.gridx = 1;
            panel.add(new JLabel(new ImageIcon(scaled)), gbc);
            return row + 1;
        } catch (Exception e) {
            return addField(panel, gbc, row, label, "解码失败: " + e.getMessage());
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private static String listStr(java.util.List<String> list) {
        return list == null || list.isEmpty() ? null : String.join(", ", list);
    }
}