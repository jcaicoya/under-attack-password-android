# CuarzoPolar — Password Android

Android app for the `password` cybershow module. Runs on the show phone and acts as the fake "secure app" where the hook creates passwords during the live performance.

Communicates with the `password_qt` Qt desktop app over WebSocket on port `8767`, tunnelled via ADB reverse so the Android always connects to `localhost` regardless of network.

## Show flow

1. The hook is asked to set a password in this app (presented as a trusted, secure app).
2. The app sends the password to the Qt operator console.
3. The Qt console runs theatrical attack simulations (brute force, dictionary, oracle).
4. The Qt console sends a verdict back: cracked or safe.
5. The app shows the result — either the password revealed in red, or a green safe confirmation.
6. The cycle repeats for each password attempt.

## Screens

| Screen | Description |
|--------|-------------|
| Form | Two password fields + confirm + send button |
| Waiting | Pulsing cuarzito animation while Qt processes |
| Result | Green (safe) or red (cracked, password revealed) |

## Build

1. Open this folder in Android Studio.
2. Ensure `local.properties` exists with the correct `sdk.dir`.
3. Sync Gradle and run on the show device.

The app connects automatically to `localhost:8767` on launch. The ADB reverse tunnel (`adb reverse tcp:8767 tcp:8767`) is set up by `password_qt` on startup, or manually via `adb-bridge`.

## Protocol

**Android → Qt:**
```json
{"type": "password", "value": "the-password"}
```

**Qt → Android:**
```json
{"type": "verdict", "cracked": true,  "password": "the-password"}
{"type": "verdict", "cracked": false}
```

## Dependencies

- OkHttp 4.12.0 — WebSocket client
- AndroidX AppCompat, ConstraintLayout, Material
- Min SDK: 26 (Android 8.0)
