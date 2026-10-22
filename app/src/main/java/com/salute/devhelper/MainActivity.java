package com.salute.devhelper;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private LinearLayout statusBadge;
    private View statusDot;
    private TextView tvStatusText;
    private TextView tvIpAddress;
    private TextView tvAdbCmd;
    private TextView tvSshCmd;
    private TextView tvPortsStatus;
    private TextView badgeAdbPort;
    private TextView badgeSshPort;
    private Button btnOpenDev;
    private Button btnOpenSettings;
    private Button btnRefresh;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean autoLaunched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusBadge = findViewById(R.id.tv_status_badge);
        statusDot = findViewById(R.id.status_dot);
        tvStatusText = findViewById(R.id.tv_status_text);
        tvIpAddress = findViewById(R.id.tv_ip_address);
        tvAdbCmd = findViewById(R.id.tv_adb_cmd);
        tvSshCmd = findViewById(R.id.tv_ssh_cmd);
        tvPortsStatus = findViewById(R.id.tv_ports_status);
        badgeAdbPort = findViewById(R.id.badge_adb_port);
        badgeSshPort = findViewById(R.id.badge_ssh_port);
        btnOpenDev = findViewById(R.id.btn_open_dev);
        btnOpenSettings = findViewById(R.id.btn_open_settings);
        btnRefresh = findViewById(R.id.btn_refresh);

        btnOpenDev.setOnClickListener(v -> launchDevelopmentSettings(false));
        btnOpenSettings.setOnClickListener(v -> launchGeneralSettings());
        btnRefresh.setOnClickListener(v -> refreshNetworkAndPorts());

        setupClipboardActions();

        btnOpenDev.requestFocus();
        refreshNetworkAndPorts();

        if (savedInstanceState == null && !autoLaunched) {
            autoLaunched = true;
            mainHandler.postDelayed(() -> launchDevelopmentSettings(true), 600);
        }
    }

    private void setupClipboardActions() {
        View cardIp = findViewById(R.id.card_ip);
        View cardAdb = findViewById(R.id.card_adb_cmd);
        View cardSsh = findViewById(R.id.card_ssh_cmd);

        if (cardIp != null) {
            cardIp.setOnClickListener(v -> copyToClipboard("IP Address", tvIpAddress.getText().toString()));
        }
        if (cardAdb != null) {
            cardAdb.setOnClickListener(v -> copyToClipboard("ADB Command", tvAdbCmd.getText().toString()));
        }
        if (cardSsh != null) {
            cardSsh.setOnClickListener(v -> copyToClipboard("SSH Command", tvSshCmd.getText().toString()));
        }
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(this, "Скопировано: " + text, Toast.LENGTH_SHORT).show();
        }
    }

    private void launchDevelopmentSettings(boolean isAuto) {
        boolean launched = false;
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.android.tv.settings",
                    "com.android.tv.settings.system.development.DevelopmentActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            launched = true;
        } catch (Exception ignored) {
        }
        if (!launched) {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                launched = true;
            } catch (Exception ignored) {
            }
        }
        if (!launched) {
            try {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.mstar.tv.factory",
                        "com.mstar.tv.factory.ui.MainActivity"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                launched = true;
            } catch (Exception ignored) {
            }
        }
        if (!launched && !isAuto) {
            Toast.makeText(this, "Не удалось открыть настройки разработчика автоматически", Toast.LENGTH_LONG).show();
        }
    }

    private void launchGeneralSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка запуска настроек: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshNetworkAndPorts() {
        tvPortsStatus.setText("Диагностика портов ADB / SSH...");
        executor.execute(() -> {
            String ip = getLocalIpAddress();
            boolean adbOpen = isPortOpen("127.0.0.1", 5555, 300);
            boolean sshOpen = isPortOpen("127.0.0.1", 2222, 300);

            mainHandler.post(() -> {
                tvIpAddress.setText(ip != null ? ip : "Нет сети");
                if (ip != null) {
                    tvAdbCmd.setText("adb connect " + ip + ":5555");
                    tvSshCmd.setText("ssh admin@" + ip + " -p 2222");
                } else {
                    tvAdbCmd.setText("adb connect <IP>:5555");
                    tvSshCmd.setText("ssh admin@<IP> -p 2222");
                }

                int colorGreen = getResources().getColor(R.color.accent_green);
                int colorCyan = getResources().getColor(R.color.accent_cyan);
                int colorMuted = getResources().getColor(R.color.text_muted);

                if (badgeAdbPort != null) {
                    badgeAdbPort.setTextColor(adbOpen ? colorGreen : getResources().getColor(R.color.accent_red));
                }
                if (badgeSshPort != null) {
                    badgeSshPort.setTextColor(sshOpen ? colorGreen : colorMuted);
                }

                StringBuilder portsInfo = new StringBuilder();
                portsInfo.append("ADB: ").append(adbOpen ? "Активен" : "Закрыт");
                portsInfo.append("  |  ");
                portsInfo.append("SSH: ").append(sshOpen ? "Активен" : "Закрыт");
                tvPortsStatus.setText(portsInfo.toString());

                // Верхняя статус-пилюля: цвет фона + точка + текст меняются вместе
                if (adbOpen) {
                    applyStatus(R.drawable.badge_status_green, "ADB ONLINE", colorGreen);
                } else if (sshOpen) {
                    applyStatus(R.drawable.badge_status_blue, "SSH ONLINE", colorCyan);
                } else {
                    applyStatus(R.drawable.badge_status_orange, "ОЖИДАНИЕ", getResources().getColor(R.color.accent_orange));
                }
            });
        });
    }

    private void applyStatus(int backgroundRes, String text, int color) {
        if (statusBadge != null) {
            statusBadge.setBackgroundResource(backgroundRes);
        }
        if (tvStatusText != null) {
            tvStatusText.setText(text);
            tvStatusText.setTextColor(color);
        }
        if (statusDot != null) {
            statusDot.setBackgroundTintList(ColorStateList.valueOf(color));
        }
    }

    private String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (intf.isLoopback() || !intf.isUp()) continue;
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
