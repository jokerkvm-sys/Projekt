package com.syetologvm.imecachecleaner;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int REQUEST_SHIZUKU = 712;
    private static final String PREFS = "settings";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_HOURS = "hours";
    private SharedPreferences prefs;
    private TextView status;
    private Switch autoSwitch;
    private Spinner interval;

    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> {
        if (requestCode == REQUEST_SHIZUKU) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                status.setText("Shizuku разрешён. Можно включить автоматическую очистку.");
            } else {
                status.setText("Нет разрешения Shizuku. Очистка не выполняется.");
            }
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        buildUi();
        autoSwitch.setChecked(prefs.getBoolean(KEY_ENABLED, false));
        int savedHours = prefs.getInt(KEY_HOURS, 6);
        interval.setSelection(savedHours == 3 ? 0 : savedHours == 6 ? 1 : savedHours == 12 ? 2 : 3);
        refreshStatus();
        if (autoSwitch.isChecked()) scheduleAuto(true);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(22));
        root.setBackgroundColor(0xFFF5F7FA);

        TextView title = new TextView(this);
        title.setText("Очистка кеша iMe"); title.setTextSize(25); title.setTextColor(0xFF172033); title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, matchWrap());

        TextView description = new TextView(this);
        description.setText("Автоматически удаляет только кеш iMe. Переписки, аккаунты, настройки и данные входа не должны удаляться.");
        description.setTextSize(15); description.setTextColor(0xFF536078); description.setPadding(0, dp(12), 0, dp(20));
        root.addView(description, matchWrap());

        Button permission = new Button(this); permission.setText("Подключить / проверить Shizuku");
        permission.setOnClickListener(v -> requestShizuku()); root.addView(permission, matchWrap());

        autoSwitch = new Switch(this); autoSwitch.setText("Автоматическая очистка"); autoSwitch.setTextSize(17); autoSwitch.setPadding(0, dp(18), 0, dp(12));
        autoSwitch.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(KEY_ENABLED, checked).apply();
            if (checked) scheduleAuto(true); else WorkManager.getInstance(this).cancelUniqueWork(CacheWorker.PERIODIC_NAME);
            refreshStatus();
        });
        root.addView(autoSwitch, matchWrap());

        TextView intervalLabel = new TextView(this); intervalLabel.setText("Интервал очистки"); intervalLabel.setTextSize(15); intervalLabel.setTextColor(0xFF172033);
        root.addView(intervalLabel, matchWrap());
        interval = new Spinner(this);
        String[] values = {"Каждые 3 часа", "Каждые 6 часов", "Каждые 12 часов", "Каждые 24 часа"};
        interval.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
        interval.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                int hours = position == 0 ? 3 : position == 1 ? 6 : position == 2 ? 12 : 24;
                prefs.edit().putInt(KEY_HOURS, hours).apply();
                if (autoSwitch != null && autoSwitch.isChecked()) scheduleAuto(false);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        root.addView(interval, matchWrap());

        Button cleanNow = new Button(this); cleanNow.setText("Очистить кеш сейчас");
        cleanNow.setOnClickListener(v -> {
            if (!hasShizukuPermission()) { requestShizuku(); return; }
            WorkManager.getInstance(this).enqueue(new OneTimeWorkRequest.Builder(CacheWorker.class).build());
            status.setText("Команда очистки отправлена. Результат появится в статусе после выполнения.");
        });
        LinearLayout.LayoutParams btnParams = matchWrap(); btnParams.topMargin = dp(18); root.addView(cleanNow, btnParams);

        status = new TextView(this); status.setTextSize(14); status.setTextColor(0xFF536078); status.setPadding(0, dp(20), 0, 0);
        root.addView(status, matchWrap());

        TextView note = new TextView(this);
        note.setText("Важно: Shizuku должен быть запущен и разрешён этому приложению. Без root на некоторых прошивках фоновые задачи могут задерживаться. На Android без поддержки --cache-only очистка будет отменена ради защиты данных.");
        note.setTextSize(12); note.setTextColor(0xFF737F94); note.setPadding(0, dp(20), 0, 0); root.addView(note, matchWrap());
        setContentView(root);
    }

    private void requestShizuku() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    status.setText("Shizuku работает и разрешён.");
                } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                    status.setText("Разрешение ранее отклонено. Открой Shizuku и разреши доступ этому приложению.");
                } else {
                    Shizuku.requestPermission(REQUEST_SHIZUKU);
                }
            } else {
                status.setText("Shizuku не запущен. Запусти его и вернись сюда.");
                try { startActivity(getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api")); } catch (Exception ignored) { }
            }
        } catch (Exception e) { status.setText("Ошибка Shizuku: " + e.getMessage()); }
    }

    private boolean hasShizukuPermission() {
        try { return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (Exception e) { return false; }
    }

    private void scheduleAuto(boolean runInitial) {
        int hours = prefs.getInt(KEY_HOURS, 6);
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build();
        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(CacheWorker.class, hours, TimeUnit.HOURS)
                .setConstraints(constraints).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(CacheWorker.PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, periodic);
        if (runInitial) WorkManager.getInstance(this).enqueueUniqueWork(CacheWorker.INITIAL_NAME, androidx.work.ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(CacheWorker.class).build());
        refreshStatus();
    }

    private void refreshStatus() {
        if (status == null) return;
        boolean enabled = prefs.getBoolean(KEY_ENABLED, false);
        String shizuku = hasShizukuPermission() ? "Shizuku: разрешён" : "Shizuku: не подключён / нет разрешения";
        status.setText(shizuku + "\nАвтоочистка: " + (enabled ? "включена" : "выключена") + (enabled ? "\nСледующие запуски планирует Android; точное время может сдвигаться." : ""));
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDestroy() { Shizuku.removeRequestPermissionResultListener(permissionListener); super.onDestroy(); }
}
