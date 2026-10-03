package com.ss.downloader;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    private final int navy = Color.rgb(12,18,34), card = Color.rgb(25,34,55), purple = Color.rgb(155,97,255), muted = Color.rgb(174,186,211), cyan = Color.rgb(53,223,223);
    private LinearLayout root, body, navigation;
    private EditText link;
    private Button find;
    private String input = "", resolvedUrl = "", videoTitle = "", page = "Home";
    private int quality = 720;
    private boolean looking;
    private TextView downloadStatus;
    private ProgressBar progress;
    private boolean wasRunning;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (downloadStatus != null) downloadStatus.setText(DownloadService.status);
            if (progress != null) { progress.setIndeterminate(DownloadService.running && DownloadService.progress == 0); progress.setProgress(DownloadService.progress); }
            if (wasRunning && !DownloadService.running && page.equals("Downloads")) show("Downloads");
            wasRunning = DownloadService.running;
            handler.postDelayed(this, 600);
        }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        quality = getPreferences(0).getInt("quality", 720);
        if (saved != null) { input = saved.getString("input", ""); quality = saved.getInt("quality", 720); }
        acceptShare(getIntent());
        show("Home");
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 5);
    }
    @Override public void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); acceptShare(intent); show("Home"); }
    private void acceptShare(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null) {
                Matcher match = Pattern.compile("https://[^\\s<>]+").matcher(shared);
                input = match.find() ? match.group() : shared;
            }
        }
    }
    @Override public void onSaveInstanceState(Bundle out) { out.putString("input", link == null ? input : link.getText().toString()); out.putInt("quality", quality); super.onSaveInstanceState(out); }
    @Override public void onResume() { super.onResume(); handler.post(ticker); }
    @Override public void onPause() { handler.removeCallbacks(ticker); super.onPause(); }
    @Override public void onDestroy() { worker.shutdown(); super.onDestroy(); }

    public static boolean validUrl(String value) {
        if (value == null || value.length() > 4096) return false;
        Uri uri = Uri.parse(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) return false;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        for (String domain : new String[]{"youtube.com", "youtu.be", "facebook.com", "fb.watch", "tiktok.com"})
            if (host.equals(domain) || host.endsWith("." + domain)) return true;
        return false;
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable background(int color) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(22)); return drawable; }
    private GradientDrawable gradient() { GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{purple, Color.rgb(103,45,238)}); drawable.setCornerRadius(dp(20)); return drawable; }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private void gap(int size) { View space = new View(this); body.addView(space, new LinearLayout.LayoutParams(1, dp(size))); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setLineSpacing(dp(3), 1); return view;
    }
    private Button button(String title, Runnable action, boolean primary) {
        Button view = new Button(this); view.setText(title); view.setTextColor(Color.WHITE); view.setTextSize(16); view.setAllCaps(false);
        view.setBackground(primary ? gradient() : background(card)); view.setMinHeight(dp(56)); view.setPadding(dp(14), dp(10), dp(14), dp(10));
        view.setOnClickListener(v -> action.run()); return view;
    }
    private LinearLayout panel() { LinearLayout layout = column(); layout.setBackground(background(card)); layout.setPadding(dp(18), dp(18), dp(18), dp(18)); body.addView(layout); return layout; }
    private void show(String destination) {
        if (link != null) input = link.getText().toString();
        page = destination; link = null; downloadStatus = null; progress = null;
        root = column(); root.setBackgroundColor(navy); root.setPadding(dp(20), 0, dp(20), 0);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            root.setPadding(dp(20) + bars.left, bars.top + dp(12), dp(20) + bars.right, bars.bottom + dp(12)); return insets;
        });
        setContentView(root);
        root.addView(text(destination.equals("Home") ? "SS Downloader" : destination.equals("Downloads") ? "Your downloads" : "Make it yours", 27, Color.WHITE, true));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        body = column(); body.setPadding(0, dp(20), 0, dp(20)); scroll.addView(body);
        if (destination.equals("Home")) home(); else if (destination.equals("Downloads")) downloads(); else settings();
        navigation = new LinearLayout(this); navigation.setPadding(dp(4), dp(10), dp(4), 0);
        for (String item : new String[]{"Home", "Downloads", "Settings"}) {
            Button tab = button((item.equals("Home") ? "⌂  " : item.equals("Downloads") ? "↓  " : "⚙  ") + item, () -> show(item), false);
            tab.setTextSize(12); tab.setTextColor(item.equals(page) ? purple : muted);
            navigation.addView(tab, new LinearLayout.LayoutParams(0, dp(54), 1));
        }
        root.addView(navigation);
    }
    private void home() {
        body.addView(text("Your videos. One happy place.", 15, muted, false)); gap(20);
        ImageView hero = new ImageView(this); hero.setImageResource(R.drawable.ss_logo);
        hero.setScaleType(ImageView.ScaleType.FIT_CENTER); hero.setContentDescription("SS Downloader logo, by Sasmitha Sandaken");
        body.addView(hero, new LinearLayout.LayoutParams(-1, dp(180))); gap(24);
        body.addView(text("Save a video", 28, Color.WHITE, true)); gap(8);
        body.addView(text("Got a link? Let's bring it home.", 15, muted, false)); gap(20);
        link = new EditText(this); link.setText(input); link.setHint("Paste your video link"); link.setTextColor(Color.WHITE); link.setHintTextColor(muted);
        link.setTextSize(15); link.setSingleLine(true); link.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        link.setPadding(dp(16), dp(18), dp(16), dp(18)); link.setBackground(background(card)); body.addView(link);
        gap(10); body.addView(button("Paste from clipboard", () -> {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence value = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
                link.setText(value == null ? "" : value.toString());
            } else toast("Copy a video link first.");
        }, false)); gap(14);
        find = button(looking ? "Finding your video…" : "Find video  →", this::lookup, true); find.setEnabled(!looking); body.addView(find);
        gap(24); body.addView(text("YouTube   •   Facebook   •   TikTok", 15, muted, true)); gap(12);
        body.addView(text("Save videos you own or have permission to download. Some links need login or may be unavailable.", 12, muted, false));
        if (DownloadService.running) { gap(16); body.addView(button("View active download", () -> show("Downloads"), false)); }
    }
    private void lookup() {
        String url = link.getText().toString().trim(); input = url;
        if (!validUrl(url)) { link.setError("Use a YouTube, Facebook or TikTok HTTPS link."); return; }
        if (DownloadService.running) { toast("Finish or cancel the current download first."); show("Downloads"); return; }
        looking = true; find.setEnabled(false); find.setText("Finding your video…");
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(link.getWindowToken(), 0);
        worker.execute(() -> {
            try {
                DownloadService.init(this);
                YoutubeDLRequest request = new YoutubeDLRequest(url); request.addOption("--no-playlist"); request.addOption("--socket-timeout", 20);
                String title = YoutubeDL.getInstance().getInfo(request).getTitle();
                runOnUiThread(() -> { looking = false; resolvedUrl = url; videoTitle = title == null ? "Your video" : title; if (!isFinishing() && !isDestroyed()) qualityDialog(); });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    looking = false;
                    if (isFinishing() || isDestroyed()) return;
                    if (page.equals("Home")) { find.setEnabled(true); find.setText("Find video  →"); }
                    new AlertDialog.Builder(this).setTitle("Couldn't open this video")
                        .setMessage("Check your connection and try another public link. Private videos and some platform restrictions are unsupported.\n\n" + shortError(error))
                        .setPositiveButton("OK", null).show();
                });
            }
        });
    }
    private String shortError(Exception error) { String value = error.getMessage(); return value == null ? "Please try again." : value.substring(0, Math.min(250, value.length())); }
    private void qualityDialog() {
        if (page.equals("Home")) { find.setEnabled(true); find.setText("Find video  →"); }
        new AlertDialog.Builder(this).setTitle(videoTitle)
            .setSingleChoiceItems(new String[]{"1080p · Full HD", "720p · HD", "480p · Standard"}, quality == 1080 ? 0 : quality == 720 ? 1 : 2,
                (dialog, which) -> quality = new int[]{1080,720,480}[which])
            .setPositiveButton("Download", (dialog, which) -> {
                if (DownloadService.running) { toast("A download is already running."); return; }
                startForegroundService(new Intent(this, DownloadService.class).putExtra("url", resolvedUrl).putExtra("height", quality));
                show("Downloads");
            }).setNegativeButton("Back", null).show();
    }
    private void downloads() {
        LinearLayout active = panel(); active.addView(text(DownloadService.running ? "Bringing your video home" : "Download status", 18, Color.WHITE, true));
        downloadStatus = text(DownloadService.status, 14, muted, false); active.addView(downloadStatus);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(cyan));
        progress.setMax(100); progress.setProgress(DownloadService.progress); active.addView(progress, new LinearLayout.LayoutParams(-1, dp(24)));
        if (DownloadService.running) active.addView(button("Cancel download", () -> { startService(new Intent(this, DownloadService.class).setAction("cancel")); toast("Cancelling…"); }, false));
        gap(24); body.addView(text("Saved videos", 22, Color.WHITE, true)); gap(12);
        int count = 0;
        try (Cursor cursor = getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            new String[]{MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.SIZE, MediaStore.Downloads.MIME_TYPE},
            MediaStore.Downloads.RELATIVE_PATH + " = ? AND " + MediaStore.Downloads.IS_PENDING + " = 0",
            new String[]{Environment.DIRECTORY_DOWNLOADS + "/SS Downloader/"}, MediaStore.Downloads.DATE_ADDED + " DESC")) {
            if (cursor != null) while (cursor.moveToNext()) {
                count++; Uri uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0));
                String name = cursor.getString(1), mime = cursor.getString(3);
                LinearLayout row = panel(); row.addView(text("▶  " + name, 16, Color.WHITE, true));
                row.addView(text(String.format(Locale.ROOT, "%.1f MB", cursor.getLong(2) / 1048576.0), 13, muted, false));
                row.addView(button("Play video", () -> play(uri, mime), false)); gap(10);
            }
        } catch (Exception error) { body.addView(text("Couldn't read saved videos. " + shortError(error), 14, muted, false)); }
        if (count == 0) {
            gap(24); body.addView(text("Your collection starts here ✨", 21, Color.WHITE, true)); gap(10);
            body.addView(text("Saved videos will appear here. You'll also find them in Files → Downloads → SS Downloader.", 15, muted, false)); gap(20);
            body.addView(button("Find your first video", () -> show("Home"), true));
        }
    }
    private void play(Uri uri, String mime) {
        try { startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime == null ? "video/*" : mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
        catch (ActivityNotFoundException error) { toast("Install a video player to open this file."); }
    }
    private void settings() {
        LinearLayout storage = panel(); storage.addView(text("A home for your videos", 18, Color.WHITE, true)); storage.addView(text("Downloads / SS Downloader\nAndroid 10 or newer\nVersion 0.1.0", 15, muted, false)); gap(20);
        body.addView(text("Default quality", 20, Color.WHITE, true)); gap(12);
        body.addView(button(quality + "p · Tap to change", () -> new AlertDialog.Builder(this).setTitle("Default quality")
            .setItems(new String[]{"1080p", "720p", "480p"}, (dialog, which) -> { quality = new int[]{1080,720,480}[which]; getPreferences(0).edit().putInt("quality", quality).apply(); show("Settings"); }).show(), false));
        gap(24); body.addView(text("About SS Downloader", 20, Color.WHITE, true)); gap(12);
        body.addView(text("Public video links are processed on your phone with yt-dlp and FFmpeg. No SS Downloader server or account is required. Availability depends on the platform, video and extractor version. Selected quality is a maximum; a lower available quality may be used.\n\nThis first version has one download at a time, cancellation and video playback. Pause/resume, private-account login and guaranteed YouTube support are not included.\n\nOpen-source dependencies: youtubedl-android (GPL-3.0), yt-dlp and FFmpeg. Source and build instructions are included with this app.", 14, muted, false));
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
