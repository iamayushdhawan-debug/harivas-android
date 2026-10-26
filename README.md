# HARIVAS Android App

A native Android WebView wrapper for the **HARIVAS Delivery Commerce** platform — a WordPress/WooCommerce PWA with five portals:

| Portal | Path |
|---|---|
| 🛒 Store | `/harivas/` |
| 🛵 Rider | `/harivas-rider/` |
| 🏪 Vendor | `/harivas-vendor/` |
| 👷 Staff | `/harivas-staff/` |
| 🔧 Admin | `/harivas-admin/` |

---

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android device / emulator running API 26+
- A Firebase project with Cloud Messaging enabled

---

## Setup Steps

### 1. Set your WordPress site URL

Open [`app/build.gradle.kts`](app/build.gradle.kts) and replace the placeholder:

```kotlin
buildConfigField("String", "SITE_URL", "\"https://YOUR-SITE.com\"")
// Change to e.g.:
buildConfigField("String", "SITE_URL", "\"https://harivas.example.com\"")
```

> [!IMPORTANT]
> Also update the `android:host` values in **`AndroidManifest.xml`** deep-link intent filters to match your real domain.

---

### 2. Add google-services.json from Firebase

1. Open [Firebase Console](https://console.firebase.google.com/) → your project
2. **Project Settings → Your Apps → Add app (Android)**
3. Use package name: `com.harivas.app`
4. Download `google-services.json`
5. Place the file at **`app/google-services.json`** (next to `app/build.gradle.kts`)

A placeholder with instructions lives at [`google-services.json.example`](google-services.json.example).

---

### 3. Build in Android Studio

```bash
# Or via Gradle wrapper:
./gradlew assembleDebug
```

1. Open the project root in Android Studio
2. Wait for Gradle sync to complete
3. **Run → Run 'app'** or press ▶

---

### 4. WordPress REST API endpoints required

The app calls these endpoints on your site:

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/wp-json/harivas/v1/rider/location` | Stream rider GPS |
| POST | `/wp-json/harivas/v1/push/register` | Register FCM token |

Implement these in your HARIVAS WordPress plugin.

---

### 5. Play Store upload

#### a) Generate a signing keystore
```bash
keytool -genkeypair -v \
  -keystore harivas-release.jks \
  -alias harivas \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

#### b) Configure signing in `app/build.gradle.kts`
```kotlin
signingConfigs {
    create("release") {
        storeFile = file("harivas-release.jks")
        storePassword = System.getenv("KEYSTORE_PASS")
        keyAlias = "harivas"
        keyPassword = System.getenv("KEY_PASS")
    }
}
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
    }
}
```

> [!CAUTION]
> Never commit your `.jks` keystore or passwords to version control. Use environment variables or a secrets manager.

#### c) Build release AAB
```bash
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

#### d) Upload to Play Store
1. [Google Play Console](https://play.google.com/console) → Create app
2. Fill in store listing, screenshots, content rating
3. **Production → Create new release → Upload AAB**
4. Submit for review (typically 1–3 days for a new app)

---

## JavaScript Bridge (`window.HvNative`)

The app injects `window.HvNative` into every page. Your WordPress/JS code can call:

```js
// Detection
HvNative.isAndroidApp()              // → true
HvNative.getAppVersion()             // → "1.0.0"

// Location tracking (rider)
HvNative.startLocationTracking("ORDER_ID")
HvNative.stopLocationTracking()

// Permissions
HvNative.requestNotificationPermission()

// Native UI
HvNative.openDialer("+911234567890")
HvNative.openMaps(12.9716, 77.5946, "HARIVAS HQ")
HvNative.shareText("Track my order: https://...")
HvNative.vibrate("0,200,100,200")

// Alerts
HvNative.showFullScreenAlert("New Order!", "Order #123 assigned", "123")
```

---

## FCM Push Payload

### Rider Alert (wakes device):
```json
{
  "data": {
    "type": "rider_alert",
    "title": "New Delivery!",
    "body": "Order #456 is ready for pickup",
    "order_id": "456"
  }
}
```

### Order Update:
```json
{
  "data": {
    "type": "order_update",
    "title": "Order Shipped",
    "body": "Your order #789 is on the way",
    "order_id": "789"
  }
}
```

---

## Architecture

```
MainActivity
  ├── WebView (all 5 portals via path routing)
  ├── HvWebViewClient  — navigation guard, offline detection
  ├── HvJsBridge       — window.HvNative JS ↔ Kotlin bridge
  └── SwipeRefreshLayout

HvLocationService (ForegroundService)
  └── FusedLocationProviderClient → POST /wp-json/harivas/v1/rider/location

HvFirebaseService (FirebaseMessagingService)
  └── onNewToken → POST /wp-json/harivas/v1/push/register
  └── onMessageReceived → HvNotifications

HvNotifications
  ├── CHANNEL_ORDERS (normal)
  ├── CHANNEL_RIDER_ALERT (high + FullScreenIntent)
  └── CHANNEL_LOCATION (low, ongoing)
```

---

## Permissions Requested at Runtime

| Permission | When |
|---|---|
| `POST_NOTIFICATIONS` | On first launch (Android 13+) |
| `ACCESS_FINE_LOCATION` | When JS calls `startLocationTracking()` |
| `CAMERA` | When WebView `<input type="file">` asks for camera |

`ACCESS_BACKGROUND_LOCATION` — users must grant manually in Settings after fine location is approved.
