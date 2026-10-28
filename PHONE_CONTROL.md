# Phone control — capability map and tool reference

AgentDeck runs inside the normal Android app sandbox. There is no root, no
hidden system API, and no accessibility service. "Control the phone" therefore
means a set of public intents and system-service calls, each with a different
level of power. This document maps what exists, what is implemented, and what
Android does not allow an ordinary app to do.

## Control tiers

| Tier | What it can do | Permission model | Status |
| --- | --- | --- | --- |
| 1. Public intents and system services | Open apps, screens, dialer, message composer, clock, calendar, browser, share sheet; read battery/network/storage/volume; set stream volume | No dangerous runtime permissions | **Implemented** |
| 2. Runtime-permission APIs | Place a call, send SMS directly, read contacts, capture camera, read location, post notifications | User grants each permission; agent approval still applies | Planned, explicit opt-in |
| 3. Privileged / accessibility | Tap and type inside arbitrary apps, toggle Wi-Fi/Bluetooth/airplane mode, screenshots without consent | Accessibility service or system signature; Play policy restrictions | Not shipped |

Tier 1 is intentionally user-mediated for communication: the agent fills the
dialer or message, then the user presses call or send. Nothing is placed or
sent silently.

## Implemented tools

| Tool | What it does | Mechanism | Approval |
| --- | --- | --- | --- |
| `device_status` | Battery, charging, network transport, storage, volume, ringer, screen, uptime | `BatteryManager`, `ConnectivityManager`, `StatFs`, `AudioManager`, `PowerManager` | No |
| `list_apps` | Lists launchable apps as `label — package` | `PackageManager.queryIntentActivities` with a launcher `<queries>` entry | No |
| `open_app` | Opens an app by exact package, exact label, or unique partial label | Launcher intent | No |
| `open_settings` | Opens one of 26 settings panels | `Settings.ACTION_*` intents | No |
| `open_url` | Opens an `http(s)` URL in the browser | `Intent.ACTION_VIEW` | No |
| `share_text` | Opens the system share sheet with text | `Intent.ACTION_SEND` + chooser | **Yes** |
| `dial` | Opens the dialer with a number prefilled | `Intent.ACTION_DIAL` (`tel:`) | **Yes** |
| `sms_compose` | Opens the messaging app with number and body prefilled | `Intent.ACTION_SENDTO` (`smsto:`) | **Yes** |
| `set_alarm` | Opens Clock with an alarm prefilled | `AlarmClock.ACTION_SET_ALARM` | No |
| `set_timer` | Opens Clock with a timer prefilled | `AlarmClock.ACTION_SET_TIMER` | No |
| `calendar_event` | Opens Calendar with title, start, duration, location, description | `Intent.ACTION_INSERT` on `CalendarContract.Events` | No |
| `set_volume` | Sets music/ring/alarm/notification/call stream volume | `AudioManager.setStreamVolume` | No |

`dial`, `sms_compose`, and `share_text` show the existing approval card with
the exact number and message before the system UI opens.

### Examples

```text
device_status
list_apps
open_app name="WhatsApp"
open_settings panel="wifi"
open_settings panel="app_info" package="com.whatsapp"
open_url url="https://example.com"
share_text text="Meeting notes: ..."
dial number="+919876543210"
sms_compose number="+919876543210" text="Running late, see you at 7"
set_alarm time="07:30" label="Standup"
set_timer duration="10m" label="Tea"
calendar_event title="Dentist" start="2026-10-30 14:30" duration_minutes="45" location="Clinic"
set_volume level="40" stream="music"
```

Settings panels: `wifi`, `bluetooth`, `data`, `airplane`, `battery`, `display`,
`sound`, `apps`, `app_info`, `location`, `security`, `privacy`, `storage`,
`accessibility`, `developer`, `date`, `language`, `nfc`, `cast`, `vpn`, `home`,
`device_info`, `usage`, `write_settings`, `default_apps`, `notifications`.
`app_info` and `notifications` also need `package="…"`.

## Full capability map

| Phone feature | Status | How / limitation |
| --- | --- | --- |
| Place a call | Dialer prefilled | `dial`; direct call needs `CALL_PHONE` (Tier 2) |
| Answer / reject a call | Not possible | Requires the default dialer role or system privileges |
| SMS / MMS | Composer prefilled | `sms_compose`; direct send needs `SEND_SMS` (Tier 2) |
| Read SMS | Not possible | `READ_SMS` is a restricted permission; Play policy blocks it for most apps |
| Contacts | Not implemented | `READ_CONTACTS` (Tier 2); add-contact can use an insert intent |
| Email | Via share sheet | `share_text`, then choose Gmail; a dedicated `mailto:` tool is a small addition |
| List / open apps | Implemented | `list_apps`, `open_app` |
| App details / notifications / uninstall | Settings panel | `open_settings app_info`; uninstall needs `ACTION_DELETE` + user confirmation |
| Deep-link into an app | Not implemented | Only where the app publishes an intent (WhatsApp `wa.me`, YouTube, maps `geo:`); these are app-specific recipes |
| Settings screens | Implemented | 26 panels |
| Toggle Wi-Fi / Bluetooth / airplane | Blocked | Android 10+ removed ordinary app toggles; only the settings panel can open |
| Battery / charging / storage | Implemented | `device_status` |
| Network type / connectivity | Implemented | `device_status`; detailed signal strength is restricted |
| Volume / ringer | Implemented | `set_volume`; changing ringer mode/DND needs notification-policy access |
| Media play/pause | Not reliable | Media key injection is restricted; open the media app and use volume instead |
| Alarm / timer | Implemented | Clock intents |
| Calendar | Create implemented | Insert intent; reading/writing the provider needs `READ/WRITE_CALENDAR` (Tier 2) |
| Camera | Not implemented | `ACTION_IMAGE_CAPTURE` needs a FileProvider and an activity result; direct capture needs `CAMERA` |
| Photos / gallery | Open the app | Device-wide media access needs `READ_MEDIA_IMAGES` or the photo picker |
| Files outside the workspace | Not implemented | Storage Access Framework folder picker can grant a tree; the app currently confines tools to its workspace |
| Location | Settings panel only | Reading location needs `ACCESS_FINE_LOCATION` (Tier 2) |
| Notifications (read) | Not implemented | Requires a `NotificationListenerService` the user enables manually |
| Notifications (post) | Not implemented | Needs `POST_NOTIFICATIONS` runtime permission (Tier 2) |
| Clipboard | Not implemented | Background clipboard reads are restricted; foreground copy/paste is feasible |
| Screenshot / screen record | Not implemented | `MediaProjection` requires a consent dialog and a foreground service |
| Flashlight | Not implemented | `CameraManager.setTorchMode`; may require `CAMERA` on some devices |
| Tap / type in other apps | Not implemented | Only an AccessibilityService can do this; user must enable it, and Play policy scrutinises it |
| Device admin / root | Not used | Would require device-owner provisioning or root |
| Maps / navigation | Not implemented | A `geo:` / `google.navigation:` variant of `open_url` is a small addition |
| Print | Not implemented | `PrintManager` + a print adapter |
| Text-to-speech | Not implemented | Android `TextToSpeech` needs no dangerous permission |
| Voice recording | Not implemented | `MediaRecorder` + `RECORD_AUDIO` (Tier 2) |

## Why some things are impossible

- **Wi-Fi/Bluetooth/airplane toggles:** `WifiManager.setWifiEnabled` and
  `BluetoothAdapter.enable` are deprecated/blocked for apps targeting modern
  Android. Only the settings panel can be opened.
- **Silent SMS:** `SEND_SMS` exists, but Google Play restricts it to default SMS
  handlers or narrowly eligible apps. Sideloaded builds can use it with an
  explicit grant.
- **Reading SMS/call log:** restricted permissions; Play policy generally
  prohibits them outside default handlers.
- **UI automation:** Android has no public "click at coordinates in another
  app" API. AccessibilityService is the supported path, but it is powerful,
  fragile across app updates, and needs manual user enablement.
- **Screenshots:** MediaProjection always shows a consent dialog; there is no
  silent screenshot API for ordinary apps.

## Permissions

Current manifest: `INTERNET`, `ACCESS_NETWORK_STATE`, plus a launcher `<queries>`
entry for package visibility. Tier 1 adds **no dangerous permissions**.

Tier 2 would add, one at a time with a settings explanation and runtime prompt:

| Capability | Permission |
| --- | --- |
| Direct call | `CALL_PHONE` |
| Direct SMS | `SEND_SMS` |
| Contacts | `READ_CONTACTS` (and `WRITE_CONTACTS` for add) |
| Camera capture | `CAMERA` |
| Location | `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` |
| Post notifications | `POST_NOTIFICATIONS` |
| Record audio | `RECORD_AUDIO` |
| Calendar read/write | `READ_CALENDAR` / `WRITE_CALENDAR` |

Notification reading and UI automation are not runtime permissions; they need
the user to enable a service in Settings.

## Safety model

- Communication tools (`dial`, `sms_compose`, `share_text`) require approval and
  show the exact target and body.
- The agent never has a silent `CALL_PHONE` or `SEND_SMS` path in Tier 1.
- `open_app`, `open_settings`, alarms, timers, calendar inserts, URL opens and
  volume changes only open system UI or change reversible state.
- Tool calls come from the model, not from web pages; web content remains
  untrusted data.
- The local model sees the full 23-tool protocol. Needle stays on its scoped
  five/four-tool catalogues and does not yet route phone tools.

## Tests and build

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:assembleRelease -Psnapdragon --console=plain
.\gradlew.bat :app:assembleRelease --console=plain
```

`PhoneToolsTest` covers number normalisation, clock parsing, duration parsing,
calendar start parsing, volume mapping, settings panels, approval policy, and
fenced/JSON protocol parsing. The full suite is **138 tests, 0 failures**.

## Device verification recipe

Install over the current build (same signature, so no uninstall needed):

```powershell
adb install -r dist/agentdeck-snapdragon-1.0.0-phone.apk
```

Open a task in Coding mode with a cloud model (most reliable routing) and try:

| Say this | Expected |
| --- | --- |
| "What's my battery, network, and storage?" | `device_status` output, no approval |
| "List my apps" | `list_apps`, up to 120 entries |
| "Open WhatsApp" | Launcher opens WhatsApp |
| "Open Wi-Fi settings" | Wi-Fi panel opens |
| "Open https://example.com" | Browser opens the page |
| "Set an alarm for 7:30" | Clock opens with the alarm prefilled |
| "Set a 10 minute timer" | Clock opens with the timer prefilled |
| "Create a calendar event called Dentist tomorrow at 14:30" | Calendar insert screen prefilled |
| "Set music volume to 40" | Music stream changes to 40% |
| "Dial +919876543210" | Approval card, then dialer prefilled — no call placed |
| "Text +919876543210 saying I'm running late" | Approval card, then Messages prefilled — not sent |
| "Share 'meeting notes'" | Approval card, then the share sheet |

Then check for crashes:

```powershell
adb logcat -d | Select-String "AndroidRuntime|FATAL|AgentDeck"
```

Local models can route phone tools too, but a small model may pick a near-miss
tool; if that happens, repeat with the exact tool name or use a cloud model.

## Verified on device (2026-10-28)

Device: Xiaomi `2107113SG` (Xiaomi 11T Pro), Android 16 (API 36). The standard
debug build was installed over the existing `1.0.0` install with the same
signing key, so app data survived.

| Tool | Result |
| --- | --- |
| `device_status` | Returned real values: battery 100% and charging, Wi-Fi with internet, 180.1 GB free of 224.2 GB, `Xiaomi 2107113SG`, Android 16 (API 36). |
| `open_settings` | `panel="wifi"` opened `com.android.settings/.Settings$WifiSettingsActivity`. |
| `set_volume` | `level="40", stream="music"` returned `set volume to 40% (12/30)`; `dumpsys audio` confirmed `streamVolume:12`. |
| `dial` | Approval card showed `+919876543210` and "The call is not placed until you press call." **Allow once** opened `com.google.android.dialer` with the number prefilled; nothing was dialled. |
| `sms_compose` | Approval card showed the number and `running late`. **Allow once** started `com.google.android.apps.messaging/...LaunchConversationActivity`; Google Messages did not render its composer on this Xiaomi build, so the prefilled body could not be confirmed visually. |

Model note: the run used local `Qwen3.5-4B-Q4_K_M` on CPU. It routed
`device_status`, `open_settings`, `set_volume`, `dial`, and `sms_compose`. Its
first `set_volume` call omitted `level`; the validator rejected it with
"level must be 0–100" and the model corrected it on the next attempt. It also
saved an unrelated memory ("running late"), which was cleared manually.
OpenRouter's free cloud models returned HTTP 429 throughout the test, so the
cloud path was not exercised.

Not individually exercised on hardware: `list_apps`, `open_app`, `open_url`,
`share_text`, `set_alarm`, `set_timer`, `calendar_event`. They share the same
`launch()` path and are covered by the argument and approval tests.

## Next decisions

1. Direct call / direct SMS with `CALL_PHONE` / `SEND_SMS` and approval.
2. Contacts search and add-contact.
3. Camera capture with a FileProvider result path.
4. Location read.
5. Accessibility-based UI automation (the only route to controlling arbitrary
   apps).
6. A five-tool Needle phone catalogue for the most common device commands.
