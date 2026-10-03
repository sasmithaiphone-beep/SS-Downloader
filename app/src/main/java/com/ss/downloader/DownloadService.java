package com.ss.downloader;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.ffmpeg.FFmpeg;
import java.io.*;
import java.util.concurrent.*;
import kotlin.Unit;

public class DownloadService extends Service {
    public static volatile boolean running;
    public static volatile int progress;
    public static volatile String status = "Ready when you are";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean cancelled;
    private static final String CHANNEL = "downloads", PROCESS = "ss-download";
    private long lastNotification;

    public static synchronized void init(Context context) throws Exception {
        YoutubeDL.getInstance().init(context.getApplicationContext());
        FFmpeg.getInstance().init(context.getApplicationContext());
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel(CHANNEL, "Video downloads", NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if ("cancel".equals(intent.getAction())) {
            cancelled = true;
            workerCancel();
            return START_NOT_STICKY;
        }
        if (running) return START_NOT_STICKY;
        running = true;
        cancelled = false;
        progress = 0;
        status = "Preparing your video…";
        startForeground(42, notification());
        String url = intent.getStringExtra("url");
        int height = intent.getIntExtra("height", 720);
        worker.execute(() -> download(url, height));
        return START_NOT_STICKY;
    }
    private void workerCancel() {
        new Thread(() -> {
            try { YoutubeDL.getInstance().destroyProcessById(PROCESS); }
            catch (Exception ignored) { }
        }, "ss-cancel").start();
    }
    private void download(String url, int height) {
        File directory = new File(getCacheDir(), "job-" + System.currentTimeMillis());
        try {
            if (!MainActivity.validUrl(url)) throw new IOException("Please enter a supported HTTPS video link.");
            if (!directory.mkdirs()) throw new IOException("Could not create download storage.");
            init(this);
            if (cancelled) throw new InterruptedException();
            YoutubeDLRequest request = new YoutubeDLRequest(url);
            request.addOption("--no-playlist");
            request.addOption("--no-mtime");
            request.addOption("--socket-timeout", 30);
            request.addOption("--retries", 3);
            request.addOption("--restrict-filenames");
            request.addOption("--trim-filenames", 100);
            request.addOption("--merge-output-format", "mp4");
            request.addOption("-f", "bestvideo[height<=" + height + "][ext=mp4]+bestaudio[ext=m4a]/best[height<=" + height + "][ext=mp4]/best[height<=" + height + "]");
            request.addOption("-o", new File(directory, "%(title)s-%(id)s.%(ext)s").getAbsolutePath());
            YoutubeDL.getInstance().execute(request, PROCESS, (percent, eta, line) -> {
                progress = Math.max(0, Math.min(99, percent.intValue()));
                status = "Downloading · " + progress + "%";
                updateNotification();
                return Unit.INSTANCE;
            });
            if (cancelled) throw new InterruptedException();
            File[] videos = directory.listFiles(file -> file.isFile() &&
                (file.getName().endsWith(".mp4") || file.getName().endsWith(".webm") || file.getName().endsWith(".mkv")));
            if (videos == null || videos.length == 0) throw new IOException("No finished video was returned. Try another link or quality.");
            status = "Saving to your Downloads folder…";
            for (File video : videos) save(video);
            progress = 100;
            status = "Saved! Find your video in Downloads.";
        } catch (Exception error) {
            status = cancelled ? "Download cancelled" : "Download failed. " + friendly(error);
        } finally {
            delete(directory);
            running = false;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }
    private String friendly(Exception error) {
        String message = error.getMessage();
        if (message == null) return "Check your connection and try again.";
        if (message.toLowerCase().contains("private") || message.toLowerCase().contains("sign in"))
            return "This video needs account access. Private and sign-in-only videos are not supported.";
        return message.substring(0, Math.min(350, message.length()));
    }
    private void save(File file) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, file.getName());
        values.put(MediaStore.Downloads.MIME_TYPE, file.getName().endsWith(".mp4") ? "video/mp4" : file.getName().endsWith(".webm") ? "video/webm" : "video/x-matroska");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SS Downloader/");
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("Could not save the video.");
        try {
            try (InputStream in = new FileInputStream(file); OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("Could not open the Downloads folder.");
                byte[] buffer = new byte[65536];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (cancelled) throw new IOException("Cancelled");
                    out.write(buffer, 0, count);
                }
            }
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
        } catch (IOException | RuntimeException error) {
            getContentResolver().delete(uri, null, null);
            throw error;
        }
    }
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent cancel = PendingIntent.getService(this, 1, new Intent(this, DownloadService.class).setAction("cancel"), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("SS Downloader").setContentText(status).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setProgress(100, progress, progress == 0)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancel).build()).build();
    }
    private void updateNotification() {
        long now = System.currentTimeMillis();
        if (now - lastNotification > 1000) {
            lastNotification = now;
            getSystemService(NotificationManager.class).notify(42, notification());
        }
    }
    private void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }
    @Override public void onTimeout(int startId, int fgsType) {
        cancelled = true; workerCancel(); status = "Download stopped by Android. Please retry.";
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    @Override public void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
