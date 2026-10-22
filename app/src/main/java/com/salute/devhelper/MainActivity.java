package com.salute.devhelper;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
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

    private TextView tvStatusBadge;
    private TextView tvIpAddress;
    private TextView tvAdbCmd;
    private TextView tvSshCmd;
    private TextView tvPortsStatus;
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

        tvStatusBadge = findViewById(R.id.tv_status_badge);
        tvIpAddress = findViewById(R.id.tv_ip_address);
        tvAdbCmd = findViewById(R.id.tv_adb_cmd);
        tvSshCmd = findViewById(R.id.tv_ssh_cmd);
        tvPortsStatus = findViewById(R.id.tv_ports_status);
        btnOpenDev = findViewById(R.id.btn_open_dev);
        btnOpenSettings = findViewById(R.id.btn_open_settings);
        btnRefresh = findViewById(R.id.btn_refresh);

        btnOpenDev.setOnClickListener(v -> launchDevelopmentSettings(false));
        btnOpenSettings.setOnClickListener(v -> launchGeneralSettings());
        btnRefresh.setOnClickListener(v -> refreshNetworkAndPorts());

        btnOpenDev.requestFocus();

        refreshNetworkAndPorts();

        if (savedInstanceState == null && !autoLaunched) {
            autoLaunched = true;
            mainHandler.postDelayed(() -> launchDevelopmentSettings(true), 600);
        }
    }

    private void launchDevelopmentSettings(boolean isAuto) {
        boolean launched = false;
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.system.development.DevelopmentActivity"));
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
                intent.setComponent(new ComponentName("com.mstar.tv.factory", "com.mstar.tv.factory.ui.MainActivity"));
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
        tvPortsStatus.setText("Сканирование локальных портов ADB и SSH...");
        executor.execute(() -> {
            String ip = getLocalIpAddress();
            boolean adbOpen = isPortOpen("127.0.0.1", 5555, 300);
            boolean sshOpen = isPortOpen("127.0.0.1", 2222, 300);

            mainHandler.post(() -> {
                tvIpAddress.setText(ip != null ? ip : "Сеть не подключена");
                if (ip != null) {
                    tvAdbCmd.setText("adb connect " + ip + ":5555");
                    tvSshCmd.setText("ssh admin@" + ip + " -p 2222");
                } else {
                    tvAdbCmd.setText("adb connect <IP>:5555");
                    tvSshCmd.setText("ssh admin@<IP> -p 2222");
                }

                StringBuilder portsInfo = new StringBuilder();
                portsInfo.append("ADB (5555): ").append(adbOpen ? "✅ Активен" : "❌ Закрыт/Не запущен");
                portsInfo.append("  |  ");
                portsInfo.append("SSH (2222): ").append(sshOpen ? "✅ Активен" : "❌ Не запущен");
                tvPortsStatus.setText(portsInfo.toString());

                if (adbOpen) {
                    tvStatusBadge.setText("● ADB Online");
                    tvStatusBadge.setTextColor(0xFF4ADE80);
                } else if (sshOpen) {
                    tvStatusBadge.setText("● SSH Online");
                    tvStatusBadge.setTextColor(0xFF38BDF8);
                } else {
                    tvStatusBadge.setText("● Waiting Setup");
                    tvStatusBadge.setTextColor(0xFFFB923C);
                }
            });
        });
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
