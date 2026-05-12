package com.lvt4j.spider4videostation.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import com.lvt4j.spider4videostation.Spider4VideoStationApp;
import com.lvt4j.spider4videostation.service.DoubanService;
import com.lvt4j.spider4videostation.service.DoubanService.LoginState;
import com.lvt4j.spider4videostation.service.ImageDownloadService;

/**
 * 豆瓣登录弹窗 —— Swing实现，替代原 HTML 中的 QR 扫码登录流程
 *
 * @author LV on 2022年7月7日
 */
public class DoubanLoginDialog extends JDialog {

    private JLabel statusLb;
    private JLabel qrLb;

    public DoubanLoginDialog(Frame owner) {
        super(owner, "豆瓣登录", true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(15, 15, 15, 15));

        statusLb = new JLabel("正在检查登录状态...", SwingConstants.CENTER);
        content.add(statusLb, BorderLayout.NORTH);

        qrLb = new JLabel("", SwingConstants.CENTER);
        content.add(qrLb, BorderLayout.CENTER);

        JButton closeBtn = new JButton("关闭");
        closeBtn.addActionListener(e -> dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.add(closeBtn);
        content.add(btnPanel, BorderLayout.SOUTH);

        setContentPane(content);
        setSize(350, 450);
        setLocationRelativeTo(owner);

        startLogin();
    }

    private void startLogin() {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                DoubanService doubanService = Spider4VideoStationApp.getBean(DoubanService.class);
                ImageDownloadService imageDownloadService = Spider4VideoStationApp.getBean(ImageDownloadService.class);

                LoginState state = doubanService.login();
                if (state.logined) {
                    statusLb.setText("豆瓣当前为已登录状态");
                    return null;
                }

                // 未登录，下载并显示 QR 码
                String localPath = imageDownloadService.download(state.qrLoginImg, DoubanService.Name);
                ImageIcon icon = new ImageIcon(localPath);
                if (icon.getIconWidth() > 300) {
                    icon = new ImageIcon(icon.getImage().getScaledInstance(300, -1, java.awt.Image.SCALE_SMOOTH));
                }
                qrLb.setIcon(icon);
                statusLb.setText("请使用豆瓣手机APP扫描二维码登录");

                // 轮询等待登录成功
                LoginState successState = doubanService.checkLoginSuccess();
                if (successState.logined) {
                    statusLb.setText("登录成功！");
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    statusLb.setText("登录失败: " + e.getCause().getMessage());
                }
            }
        }.execute();
    }
}