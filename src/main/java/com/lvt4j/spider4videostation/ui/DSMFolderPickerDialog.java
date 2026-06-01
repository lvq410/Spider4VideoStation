package com.lvt4j.spider4videostation.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import com.lvt4j.spider4videostation.Spider4VideoStationApp;
import com.lvt4j.spider4videostation.service.DsmApiClient;
import com.lvt4j.spider4videostation.service.DsmApiClient.FileInfo;
import com.lvt4j.spider4videostation.service.DsmApiClient.Library;

/**
 * DSM 文件夹选择弹窗，通过 FileStation API 浏览 NAS 上的目录
 *
 * @author LV on 2024年6月1日
 */
public class DSMFolderPickerDialog extends JDialog {

    private final DsmApiClient client;

    private DefaultComboBoxModel<String> pathCbModel;
    private JComboBox<String> pathCb;
    private JButton backBtn, forwardBtn;

    private DefaultListModel<String> listModel;
    private JList<String> folderList;
    private JButton selectBtn;

    /** 当前浏览的 DSM 路径 */
    private String currentPath;
    /** 导航历史：后退栈 */
    private Stack<String> backStack = new Stack<>();
    /** 导航历史：前进栈 */
    private Stack<String> forwardStack = new Stack<>();
    /** 用户最终选择的 DSM 路径，null 表示取消 */
    private String selectedPath;

    public DSMFolderPickerDialog(Frame owner, DsmApiClient client, String initialPath) {
        super(owner, "选择DSM文件夹", true);
        this.client = client;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(620, 480);
        setLocationRelativeTo(owner);

        JPanel contentPanel = new JPanel(new BorderLayout(5, 5));
        contentPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        // 顶部：导航栏（仿FilePickerDialog）
        JPanel navPanel = new JPanel(new BorderLayout(5, 0));
        JPanel navBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        JButton upBtn = new JButton("\u2B06"); // ⬆
        upBtn.setToolTipText("上一级");
        upBtn.addActionListener(e -> navigateUp());
        backBtn = new JButton("\u2B05"); // ⬅
        backBtn.setToolTipText("后退");
        backBtn.setEnabled(false);
        backBtn.addActionListener(e -> navigateBack());
        forwardBtn = new JButton("\u27A1"); // ➡
        forwardBtn.setToolTipText("前进");
        forwardBtn.setEnabled(false);
        forwardBtn.addActionListener(e -> navigateForward());
        navBtnPanel.add(upBtn);
        navBtnPanel.add(backBtn);
        navBtnPanel.add(forwardBtn);
        navPanel.add(navBtnPanel, BorderLayout.WEST);

        pathCbModel = new DefaultComboBoxModel<>();
        pathCb = new JComboBox<>(pathCbModel);
        pathCb.setEditable(true);
        Font cjk = Spider4VideoStationApp.getCJKFont();
        if (cjk != null) pathCb.setFont(cjk.deriveFont(12f));
        navPanel.add(pathCb, BorderLayout.CENTER);

        JButton goBtn = new JButton("\u25B6"); // ▶
        goBtn.addActionListener(e -> {
            Object sel = pathCb.getSelectedItem();
            if (sel != null) {
                String path = sel.toString().trim();
                if (!path.isEmpty()) navigateToPath(path);
            }
        });
        navPanel.add(goBtn, BorderLayout.EAST);
        contentPanel.add(navPanel, BorderLayout.NORTH);

        // 中部：文件夹列表
        listModel = new DefaultListModel<>();
        folderList = new JList<>(listModel);
        folderList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        if (cjk != null) folderList.setFont(cjk.deriveFont(13f));
        folderList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int idx = folderList.locationToIndex(e.getPoint());
                    if (idx >= 0) enterFolder(listModel.getElementAt(idx));
                }
            }
        });
        JScrollPane listScroll = new JScrollPane(folderList);
        contentPanel.add(listScroll, BorderLayout.CENTER);

        // 底部：按钮
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        selectBtn = new JButton("选择此文件夹");
        selectBtn.setEnabled(false);
        selectBtn.addActionListener(e -> {
            selectedPath = currentPath;
            dispose();
        });
        bottomPanel.add(selectBtn);
        contentPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(contentPanel);

        // 立刻显示初始路径并加载其子目录
        String path = (initialPath != null && !initialPath.isEmpty()) ? initialPath : "/";
        pathCb.setSelectedItem(path);
        navigateToPath(path);

        // 异步加载库列表到下拉框（不影响初始路径的展示）
        loadLibrariesAsync();
    }

    /** 异步加载库根路径到下拉选项 */
    private void loadLibrariesAsync() {
        new SwingWorker<Map<String, String>, Void>() {
            @Override
            protected Map<String, String> doInBackground() throws Exception {
                Map<String, String> result = new LinkedHashMap<>();
                List<Library> libs = client.listLibraries();
                for (Library lib : libs) {
                    String rootPath = client.getLibraryFolderPath(lib.type, lib.id);
                    if (rootPath != null && !rootPath.isEmpty()) {
                        result.put(rootPath, lib.title + " (" + lib.type + ")");
                    }
                }
                return result;
            }
            @Override
            protected void done() {
                try {
                    Map<String, String> libRoots = get();
                    for (String rootPath : libRoots.keySet()) {
                        // 避免重复添加
                        boolean exists = false;
                        for (int i = 0; i < pathCbModel.getSize(); i++) {
                            if (rootPath.equals(pathCbModel.getElementAt(i))) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) pathCbModel.addElement(rootPath);
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    /** 导航到指定 DSM 路径，列出其子目录 */
    private void navigateToPath(String path) {
        // 去掉末尾/（DSM API报418）
        String normalizedPath = (path.endsWith("/") && path.length() > 1)
            ? path.substring(0, path.length() - 1) : path;
        new SwingWorker<List<FileInfo>, Void>() {
            @Override
            protected List<FileInfo> doInBackground() throws Exception {
                return client.listFolder(normalizedPath);
            }
            @Override
            protected void done() {
                try {
                    List<FileInfo> files = get();
                    if (currentPath != null) {
                        backStack.push(currentPath);
                        forwardStack.clear();
                    }
                    refreshView(normalizedPath, files);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(DSMFolderPickerDialog.this,
                        "列出文件夹失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void refreshView(String path, List<FileInfo> files) {
        currentPath = path;
        pathCb.setSelectedItem(path);
        listModel.clear();
        List<FileInfo> dirs = new ArrayList<>();
        for (FileInfo f : files) {
            if (f.isdir) dirs.add(f);
        }
        dirs.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        for (FileInfo d : dirs) {
            listModel.addElement(d.name);
        }
        selectBtn.setEnabled(true);
        backBtn.setEnabled(!backStack.isEmpty());
        forwardBtn.setEnabled(!forwardStack.isEmpty());
    }

    /** 双击进入子目录 */
    private void enterFolder(String name) {
        String subPath = currentPath.endsWith("/")
            ? currentPath + name : currentPath + "/" + name;
        navigateToPath(subPath);
    }

    /** ⬆ 回到上一级目录 */
    private void navigateUp() {
        if (currentPath == null) return;
        int slash = currentPath.lastIndexOf('/');
        if (slash <= 0) return; // 已经是根目录
        String parent = currentPath.substring(0, slash);
        if (parent.isEmpty()) parent = "/";
        navigateToPath(parent);
    }

    /** ⬅ 后退 */
    private void navigateBack() {
        if (backStack.isEmpty()) return;
        forwardStack.push(currentPath);
        String prev = backStack.pop();
        loadPathNoHistory(prev);
    }

    /** ➡ 前进 */
    private void navigateForward() {
        if (forwardStack.isEmpty()) return;
        backStack.push(currentPath);
        String next = forwardStack.pop();
        loadPathNoHistory(next);
    }

    /** 加载路径但不修改历史栈（用于后退/前进） */
    private void loadPathNoHistory(String path) {
        new SwingWorker<List<FileInfo>, Void>() {
            @Override
            protected List<FileInfo> doInBackground() throws Exception {
                return client.listFolder(path);
            }
            @Override
            protected void done() {
                try {
                    refreshView(path, get());
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(DSMFolderPickerDialog.this,
                        "列出文件夹失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /** 获取用户选择的 DSM 路径，未选择时返回 null */
    public String getSelectedPath() {
        return selectedPath;
    }
}
