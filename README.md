# DGC BEATS — Android music player MVP

Native Android starter project using Kotlin, Jetpack Compose, AndroidX Media3 ExoPlayer and Coil.

## Implemented in this source milestone
- Runtime permission request and local music discovery through MediaStore.
- Local audio playback with ExoPlayer.
- Home, Library, Albums and Settings screens.
- Now-playing panel with animated decorative visualizer, play/pause, previous/next controls.
- Custom album cover selection from the device gallery for the currently playing album; selected image URI is persisted.
- Custom home banner text and image selection; values are persisted locally.
- Manual lyrics editor saved per local track.
- Android Equalizer effect toggle and per-band level sliders where supported by the device.
- Basic theme preference selection (visual theme palette switching is not yet fully applied).
- Spotify hand-off button opens Spotify's official web/app entry point.

## Not yet implemented
- Spotify OAuth/account linking, catalog search, or Spotify playback. This requires a Spotify developer app, approved scopes/SDK access and compliance with Spotify's current developer terms. This MVP does not stream Spotify audio.
- Android home-screen widget provider and widget controls.
- System media-session/foreground playback service and lock-screen controls.
- Automatic lyrics provider, timed lyrics, and audio-synchronized FFT visualizer. The current visualizer is decorative animation.
- Full theme engine; theme selection is currently stored as a preference.
- Full album art display for every track row; custom art is shown on album tiles and now-playing view.

## Build in Android Studio
1. Open the `DGC-BEATS` folder in Android Studio.
2. Use JDK 17 and install Android SDK Platform 35.
3. Allow Gradle sync to download dependencies.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. The debug APK should be generated at `app/build/outputs/apk/debug/app-debug.apk`.

Package: `com.dgcbeats.player`  
Version: `0.2.0` (development source milestone)

## Notes
- Local audio access is read-only. The app does not upload the user's music.
- Artwork and banner images are selected using Android's document picker and persisted as URI permissions.
- Equalizer effects vary by Android device and audio output path.

## Build an APK in GitHub Actions (no Android Studio required on your phone)

1. Create a GitHub repository and upload the contents of this project.
2. Open the repository's **Actions** tab and run **Build DGC BEATS APK** (or push to `main`).
3. When the workflow finishes, open its run, scroll to **Artifacts**, and download `DGC-BEATS-debug-apk`.
4. Extract the artifact; it contains `app-debug.apk`. Install it on your Android device and allow installation from the browser/file app if Android asks.

This workflow is a build setup, not evidence that the app has already compiled. The APK is only available after a successful workflow run.
