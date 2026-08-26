package com.lvt4j.spider4videostation.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lvt4j.spider4videostation.Utils;
import com.lvt4j.spider4videostation.service.ConfigService;

/**
 * 多目标文件夹选择表格面板
 * <p>
 * 提供勾选框+路径+删除按钮的表格，支持增删改查和持久化。
 * 被 VSmetaCompleterDialog 和 VSThumbRefreshDialog 共用。
 * </p>
 *
 * @author LV
 */
public class TargetFolderTablePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** 表格数据条目：路径 + 勾选状态 */
    private static class FolderEntry {
        boolean checked;
        String path;

        FolderEntry(String path, boolean checked) {
            this.path = path;
            this.checked = checked;
        }
    }

    private final ConfigService configService;
    /** 持久化到 application-local.yml 的配置 key */
    private final String configKey;
    /** 表格数据 */
    private final List<FolderEntry> entries = new ArrayList<>();
    private final FolderTableModel tableModel;
    private final JTable table;

    public TargetFolderTablePanel(ConfigService configService, String configKey) {
        super(new BorderLayout());
        this.configService = configService;
        this.configKey = configKey;

        tableModel = new FolderTableModel();
        table = new JTable(tableModel);
        table.setRowHeight(22);

        // 列宽设置
        TableColumnModel colModel = table.getColumnModel();
        // 勾选列
        colModel.getColumn(0).setPreferredWidth(40);
        colModel.getColumn(0).setMinWidth(40);
        colModel.getColumn(0).setMaxWidth(40);
        // 路径列自适应
        colModel.getColumn(1).setPreferredWidth(500);
        // 删除列
        colModel.getColumn(2).setPreferredWidth(40);
        colModel.getColumn(2).setMinWidth(40);
        colModel.getColumn(2).setMaxWidth(40);

        // 删除列居中渲染 "X"
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        colModel.getColumn(2).setCellRenderer(centerRenderer);

        // 点击删除列触发删除
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                int row = table.rowAtPoint(e.getPoint());
                if (col == 2 && row >= 0 && row < entries.size() && table.isEnabled()) {
                    entries.remove(row);
                    tableModel.fireTableRowsDeleted(row, row);
                    saveToConfig();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(0, 100));
        add(scrollPane, BorderLayout.CENTER);

        // 从配置加载已保存的条目
        loadFromConfig();
    }

    /**
     * 添加路径到表格（默认勾选）
     * <p>已存在的路径不会重复添加</p>
     */
    public void addPath(String path) {
        if (path == null || path.trim().isEmpty()) return;
        path = path.trim();
        // 去重检查
        for (FolderEntry entry : entries) {
            if (entry.path.equals(path)) return;
        }
        entries.add(new FolderEntry(path, true));
        tableModel.fireTableRowsInserted(entries.size() - 1, entries.size() - 1);
        saveToConfig();
    }

    /** 获取所有勾选的路径列表 */
    public List<String> getCheckedPaths() {
        List<String> result = new ArrayList<>();
        for (FolderEntry entry : entries) {
            if (entry.checked) result.add(entry.path);
        }
        return result;
    }

    /** 从配置中加载已保存的条目（启动时恢复） */
    private void loadFromConfig() {
        try {
            String json = configService.gets(Collections.singletonList(configKey)).get(configKey);
            if (json == null || json.isEmpty()) return;
            ArrayNode arr = (ArrayNode) Utils.ObjectMapper.readTree(json);
            for (JsonNode node : arr) {
                if (node.isObject()) {
                    String path = node.has("path") ? node.get("path").asText() : "";
                    boolean checked = !node.has("checked") || node.get("checked").asBoolean();
                    if (!path.isEmpty()) {
                        entries.add(new FolderEntry(path, checked));
                    }
                }
            }
            tableModel.fireTableDataChanged();
        } catch (Exception ignored) {}
    }

    /** 将表格数据序列化为 JSON 并持久化到配置 */
    private void saveToConfig() {
        try {
            ArrayNode arr = Utils.ObjectMapper.createArrayNode();
            for (FolderEntry entry : entries) {
                ObjectNode obj = Utils.ObjectMapper.createObjectNode();
                obj.put("path", entry.path);
                obj.put("checked", entry.checked);
                arr.add(obj);
            }
            configService.set(configKey, Utils.ObjectMapper.writeValueAsString(arr));
        } catch (Exception ignored) {}
    }

    /** 覆写以在扫描期间整体禁用/启用表格交互 */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        table.setEnabled(enabled);
    }

    /** 三列表格模型：勾选 | 路径 | 删除 */
    private class FolderTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private final String[] COLUMNS = {"", "路径", ""};

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            if (column == 0) return Boolean.class; // checkbox
            return String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            // 仅勾选列可编辑（通过 checkbox 切换）
            return column == 0 && table.isEnabled();
        }

        @Override
        public Object getValueAt(int row, int column) {
            FolderEntry entry = entries.get(row);
            switch (column) {
                case 0: return entry.checked;
                case 1: return entry.path;
                case 2: return "X";
                default: return null;
            }
        }

        @Override
        public void setValueAt(Object val, int row, int column) {
            if (column == 0 && val instanceof Boolean) {
                entries.get(row).checked = (Boolean) val;
                fireTableCellUpdated(row, column);
                saveToConfig();
            }
        }
    }
}
