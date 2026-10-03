# SS Downloader — Android source project

**Status: source prepared; APK has not been compiled or tested on Android.**

The current host has Java 8, no Android SDK, no Gradle and no usable shell network DNS. It cannot compile this project until a build environment is available. No APK is included in this archive.

## Features implemented in source

- Native Android dark navy interface with purple and turquoise accents.
- Paste video links and receive links from another app's Share menu.
- Public HTTPS links from YouTube, Facebook and TikTok.
- Fetch title before choosing 1080p, 720p or 480p maximum resolution.
- On-device yt-dlp / FFmpeg integration; no custom backend.
- One foreground download with notification, progress and cancellation.
- Save through MediaStore to Downloads/SS Downloader without broad storage permission.
- Saved video list with playback using an installed video player.
- Default quality setting and readable errors.

The generated UI image is a visual reference. This implementation uses native Android controls; the home hero and launcher icon use the user-supplied SS logo with the credit “BY SASMITHA SANDAKEN”. The original PNG is included unchanged at app/src/main/res/drawable-nodpi/ss_logo.png. Quality selection is a dialog. Thumbnails, pause/resume, multiple parallel downloads and login/cookie handling are not implemented. It does not show fake saved videos or simulated progress.

## Mac එකෙන් APK එක build කරන විදිහ

1. Android Studio install කරලා එක වරක් open කරන්න. Setup wizard එකෙන් Android SDK install කරන්න.
2. Tools → SDK Manager තුළ Android SDK Platform 35 සහ Build Tools 35.0.0 install කරන්න.
3. මෙම folder එක Terminal එකෙන් open කර `bash build.command` run කරන්න. Internet connection එක අවශ්‍යයි.
4. Build එක සාර්ථක වුණොත් folder එකට යාබද `outputs/SS-Downloader.apk` ලැබෙනවා.
5. APK එක Android phone එකට copy කරලා open කරන්න. ඒ file එක open කරන app එකට Android ඉල්ලන “Install unknown apps” permission එක දෙන්න.

APK එක Android සඳහායි; iPhone එකක install කරන්න බැහැ. Minimum Android version: 10.

## Alternative: GitHub Actions

Create your own repository and upload the **contents** of this folder, including `.github/workflows/build-apk.yml`, into its root. Actions → Build SS Downloader APK → Run workflow. When the workflow passes, download the `SS-Downloader-debug-APK` artifact and extract `app-debug.apk`. The build workflow has not been run in this session. No repository has been created or modified externally.

## Build tools

Java 17+, Gradle 8.11.1, Android Gradle Plugin 8.9.2, Android SDK 35. The Mac script downloads and verifies Gradle; the GitHub workflow provisions tools. There is no Gradle wrapper JAR in this source archive. Android Studio can import the Gradle project; the script is the explicit build entry point.

The output is a debug-signed test APK, suitable for personal testing. A production release needs a durable release signing key and a separate signed release build.

## Platform limits

Use for videos you own or have permission to save. A platform may block extraction, require login, remove formats or change its APIs. yt-dlp may need updates. YouTube may additionally require a JavaScript runtime or other extractor configuration; this first version does not bundle a JavaScript runtime. Do not assume every platform link will work. Quality is a maximum limit; the downloaded format can be lower resolution. MP4 is preferred; fallback formats may be WebM/MKV.

## Verification completed here

Manifest/resource XML parsing, build/workflow presence, resource and source references, archive integrity and build-script shell syntax were checked. **Compilation, Android lint and device tests were not run** because the SDK/Gradle/network are unavailable. Source checks cannot establish APK runtime correctness.

## Android acceptance checks after a successful build

1. Open on Android 10 and Android 15+; confirm content clears system bars and keyboard.
2. Paste malformed links and `https://youtube.com.example.org/video`; confirm rejection.
3. Share one permitted public link from each target platform; confirm URL fills.
4. Find video, choose a quality and save; confirm audio, video and actual resolution.
5. Background the app; confirm foreground download continues and notification works.
6. Cancel during transfer; confirm no partial video appears in shared Downloads.
7. Confirm denied notification permission does not prevent downloading.
8. Open saved video; confirm another player receives a content URI grant.
9. Try offline and inaccessible/private links; confirm useful errors and retry works.
10. Rotate during lookup/download and reopen app; confirm no crash or duplicate download.

## Third-party software

- youtubedl-android 0.18.1: https://github.com/yausername/youtubedl-android (GPL-3.0).
- yt-dlp: https://github.com/yt-dlp/yt-dlp (see upstream license and bundled component notices).
- FFmpeg: https://ffmpeg.org/legal.html (license depends on bundled build).

This project is offered under GPL-3.0-only. Keep this corresponding source and dependency license notices available when distributing compiled builds. Dependency artifacts supply their own notices and license terms.
