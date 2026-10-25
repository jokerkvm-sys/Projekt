package com.syetologvm.imecachecleaner;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import rikka.shizuku.Shizuku;

public class CacheWorker extends Worker {
    public static final String PERIODIC_NAME = "ime_cache_periodic";
    public static final String INITIAL_NAME = "ime_cache_initial";
    private static final String TAG = "iMeCacheCleaner";
    private static final String PACKAGE_NAME = "com.iMe.android";

    public CacheWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }

    @NonNull @Override public Result doWork() {
        try {
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Skipped: Shizuku is not running or permission is missing");
                return Result.retry();
            }
            // Safety check: only the cache-only form is used. Never run pm clear without --cache-only.
            String[] command = new String[]{"sh", "-c", "pm clear --cache-only " + PACKAGE_NAME};
            Process process = Shizuku.newProcess(command, null, null);
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errors = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append('\n');
                while ((line = errors.readLine()) != null) output.append(line).append('\n');
            }
            int code = process.waitFor();
            String result = output.toString().trim();
            Log.i(TAG, "pm clear exit=" + code + " output=" + result);
            if (code == 0 && result.toLowerCase().contains("success")) return Result.success();
            // Unsupported --cache-only must fail safely; no destructive fallback is attempted.
            return Result.failure();
        } catch (SecurityException e) {
            Log.e(TAG, "Shizuku permission denied", e);
            return Result.retry();
        } catch (Throwable e) {
            Log.e(TAG, "Cache clear failed", e);
            return Result.retry();
        }
    }
}
