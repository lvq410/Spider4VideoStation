package com.lvt4j.spider4videostation.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Stack;

import com.lvt4j.spider4videostation.Spider4VideoStationApp;
import com.lvt4j.spider4videostation.metadata.FUtils;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

/**
 * 文件/文件夹选择弹窗
 *
 * @author LV on 2023年2月9日
 */
public class FilePickerDialog extends JDialog {

    private File currentDir;
    private File selectedPath;
    private DialogResult result;

    private Stack<File> backStack = new Stack<>();
    private Stack<File> forwardStack = new Stack<>();

    private JTextField pathTf;
    private DefaultListModel<String> listModel;
    private JList<String> fileList;
    private java.util.List<File> fileEntries = new java.util.ArrayList<>();
    private JButton backBtn;
    private JButton forwardBtn;

    public FilePickerDialog(Frame owner, File initialDir) {
        super(owner, "选择文件或文件夹", true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        this.currentDir = initialDir.isDirectory() ? initialDir : initialDir.getParentFile();
        if (currentDir == null) currentDir = new File("E:\\Downloads");

        JPanel content = new JPanel(new BorderLayout(5, 5));
        content.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 导航栏
        JPanel navPanel = new JPanel(new BorderLayout(5, 5));

        JPanel navBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        JButton upBtn = new JButton("\u2B06");
        upBtn.setToolTipText("上一级");
        upBtn.addActionListener(e -> navigateUp());
        backBtn = new JButton("\u2B05");
        backBtn.setToolTipText("后退");
        backBtn.addActionListener(e -> navigateBack());
        forwardBtn = new JButton("\u27A1");
        forwardBtn.setToolTipText("前进");
        forwardBtn.addActionListener(e -> navigateForward());
        navBtnPanel.add(upBtn);
        navBtnPanel.add(backBtn);
        navBtnPanel.add(forwardBtn);
        pathTf = new JTextField(currentDir.getAbsolutePath());
        JButton goBtn = new JButton("\u25B6");
        goBtn.addActionListener(e -> navigateTo(pathTf.getText().trim()));
        pathTf.addActionListener(e -> navigateTo(pathTf.getText().trim()));
        navPanel.add(navBtnPanel, BorderLayout.WEST);
        navPanel.add(pathTf, BorderLayout.CENTER);
        navPanel.add(goBtn, BorderLayout.EAST);

        // 文件列表 — JList<String>，仿 ResourceSpider 风格
        listModel = new DefaultListModel<>();
        fileList = new JList<>(listModel);
        Font cjkFont = Spider4VideoStationApp.getCJKFont();
        if (cjkFont != null) fileList.setFont(cjkFont.deriveFont((float)fileList.getFont().getSize()));
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int idx = fileList.getSelectedIndex();
                    if (idx < 0 || idx >= fileEntries.size()) return;
                    File f = fileEntries.get(idx);
                    if (f.isDirectory()) {
                        enterDir(f);
                    } else {
                        selectedPath = f;
                        closeWithResult();
                    }
                }
            }
        });
        JScrollPane scrollPane = new JScrollPane(fileList);
        scrollPane.setPreferredSize(new Dimension(500, 350));

        // 底部确认按钮
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        JButton confirmBtn = new JButton("确认");
        confirmBtn.addActionListener(e -> {
            if (selectedPath == null) selectedPath = currentDir;
            closeWithResult();
        });
        bottomPanel.add(confirmBtn);

        content.add(navPanel, BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);
        content.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(content);
        refreshList();
        updateNavButtons();
        pack();
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
                } else if (!FUtils.isVideoFile(f)) {
                    continue;
                }
                fileEntries.add(f);
                listModel.addElement(f.isDirectory() ? f.getName() : formatSize(f.length()) + " | " + f.getName());
            }
        }
    }

    private void updateNavButtons() {
        backBtn.setEnabled(!backStack.isEmpty());
        forwardBtn.setEnabled(!forwardStack.isEmpty());
    }

    private void closeWithResult() {
        result = new DialogResult();
        result.path = selectedPath.getAbsolutePath();
        result.name = selectedPath.getName();
        dispose();
    }

    public DialogResult getResult() {
        return result;
    }

    private static String formatSize(long bytes) {
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int idx = 0;
        double size = (double) bytes;
        while (size > 1024 && idx < units.length - 1) {
            size /= 1024;
            idx++;
        }
        return String.format("%.2f%s", size, units[idx]);
    }

    public static class DialogResult {
        public String path;
        public String name;
    }
}