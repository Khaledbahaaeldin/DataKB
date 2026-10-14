# Emberbyte Real-Device Verification Checklist

This checklist contains manual verification procedures to be performed by a tester or maintainer on a physical Android device prior to general distribution. Each step tests hardware-specific, accessibility, OS integration, or thermal/battery behaviors that cannot be fully validated inside headless or synthetic emulator environments.

**Test Target**: Emberbyte v1.0.0 (`app-release-unsigned.apk` signed by release/maintainer key)  
**Minimum Target Device**: Physical Android device running Android 10 (API 29) through Android 16 (API 36+).  
**Instructions**: Follow the numbered steps below. Mark the result of each step in the provided checkbox. **Leave all checkboxes unchecked until tested on real hardware.**

---

### Step 1: TalkBack Accessibility Navigation Across Core Screens

**Goal**: Verify that blind and low-vision users using Google TalkBack receive clear, contextual spoken feedback without focus traps, missing descriptions, or unintelligible symbols.

1. Enable TalkBack in **Settings > Accessibility > TalkBack**.
2. Launch Emberbyte from the home screen.
3. Swipe right repeatedly to traverse each screen in linear reading order:
   - **Home Screen**: Spoken announcements for speed gauge ("Live speed ... bytes per second"), remaining quota, forecast sentence, chart summaries, and top app tiles.
   - **Apps Screen**: Traverse the time window filter chips ("Today", "Last 7 days", "This cycle") and individual application items with data usage descriptions.
   - **App Detail Screen**: Focus on the usage breakdown, the 7-day bar chart (verifying day-by-day spoken values), data budget ring, and spike indicators.
   - **Plans Screen & Plan Editor**: Verify accessible names on all form fields (Plan Name, Cap input, Cycle interval, Alert threshold toggles), error summaries, and the Save button.
   - **Settings Screen**: Verify all settings toggles speak their state (checked/unchecked) and summary text, and open the "Open-source licences" screen to ensure the full text is scrollable and readable.
4. Verify that decorative icons are ignored by TalkBack and that interactive touch targets measure at least 48x48 dp.

- **Expected Result**: Focus traversal follows intuitive top-to-bottom reading order on all 5 screens. Spoken announcements are grammatical, concise, and informative. No elements read raw internal IDs, "unlabelled button", or unhandled symbols.
- [ ] **PASS / FAIL** — TalkBack navigation on all 5 screens.

---

### Step 2: Floating Pill Navigation Drag & Touch Gesture

**Goal**: Verify physical touch ergonomics, drag physics, and accessibility alternatives on `FloatingPillNavBar`.

1. Place your thumb on the floating navigation pill at the bottom of the screen.
2. Drag horizontally across destinations (Home, Apps, Plans, Settings) and release:
   - Test free drag with smooth finger tracking.
   - Test quick flick/fling gesture to snap to adjacent items.
   - Test tapping directly on each destination icon.
3. With TalkBack enabled:
   - Verify each destination pill item can be directly selected with double-tap.
   - Verify TalkBack does not require drag gestures to navigate.
4. If device haptics are enabled, observe the subtle haptic tick upon snapping between destinations.

- **Expected Result**: Pill tracks finger position without latency or visual stuttering. Snap physics smoothly settle onto the active tab. Direct tapping switches screens immediately. TalkBack users can activate destinations via standard double-tap.
- [ ] **PASS / FAIL** — Drag and tap gesture on FloatingPillNavBar with and without TalkBack.

---

### Step 3: Glance AppWidget Integration (`HeroWidget`)

**Goal**: Verify homescreen widget pin, layout scaling, periodic updates, and deep linking on third-party device launchers.

1. Long-press on the device home launcher screen and select **Widgets**.
2. Locate **Emberbyte** in the widget picker and add the **Hero Widget** (2x2 or 4x2 Bento layout).
3. Resize the widget across available grid sizes supported by the launcher (e.g., 2x2, 3x2, 4x2, 4x3).
4. Verify data rendering:
   - Widget displays current data usage, cycle remaining quota, and top apps.
   - Tap directly on the widget to ensure it immediately launches Emberbyte into the Home screen or relevant detail view.
5. Generate cellular traffic (e.g., download a test file or stream audio for 1–2 minutes) and observe widget update cadence.

- **Expected Result**: Widget renders without truncation across different grid sizes. Content mirrors in-app data accurately. Tapping the widget cleanly opens the app without delay.
- [ ] **PASS / FAIL** — Glance HeroWidget homescreen pin, resize, and deep-link behavior.

---

### Step 4: Budget and Quota Alert Notifications

**Goal**: Verify that high-priority alert notifications fire reliably when app budgets or plan quota thresholds are crossed.

1. In Emberbyte, navigate to a high-usage app (e.g., YouTube or Chrome) and set a tight daily data budget (e.g., 10 MB with warning at 80%).
2. Set a plan quota with alert thresholds enabled at 50%, 80%, 90%, and 100%.
3. Generate mobile traffic on the budgeted app until the warning threshold is crossed.
4. Verify notification delivery in the system notification shade:
   - Check channel name (`Plan alerts` or `Budget alerts`).
   - Verify alert sound, vibration, and heads-up banner if configured.
   - Verify notification action buttons ("Data settings", "View app").
5. Tap the notification and verify it opens directly to the targeted app details screen (`emberbyte://apps/{package}`).

- **Expected Result**: Alerts trigger promptly upon crossing configured percentage thresholds. Tapping the notification takes the user directly to the corresponding screen. Consecutive duplicate notifications are suppressed.
- [ ] **PASS / FAIL** — Budget and plan quota alert notifications and deep actions.

---

### Step 5: Android 16 Live Update Notification & Status-Bar Chip

**Goal**: Verify platform `Notification.ProgressStyle` live update promotion and status-bar chip integration on Android 16 (API 36+) physical devices.

*(Note: On Android 15 and older devices, this verifies fallback to standard ongoing `NotificationCompat` without crash or missing progress).*

1. Ensure Live Notification is enabled in **Settings > Notifications > Live notification**.
2. Ensure at least one active plan exists with a non-zero data cap.
3. Pull down the status bar and observe the ongoing Emberbyte notification:
   - On Android 16+: Check for promoted ongoing style with status-bar chip showing remaining data (e.g. `13.8 GB` or `0 B`).
   - Check progress bar indicator reflecting current cycle consumption percentage.
4. Start active network traffic and verify live speed updates (e.g., "↓ 1.2 MB/s · ↑ 45 KB/s") update at most once per second without notification flashing or sound spam.
5. Tap the notification to confirm it opens the Home screen.

- **Expected Result**: On Android 16+, notification shows native ProgressStyle with short critical text chip. On older releases, ongoing notification updates smoothly without flicker. Home intent opens cleanly.
- [ ] **PASS / FAIL** — Live update notification and Android 16 status-bar chip.

---

### Step 6: Multi-SIM / Dual-SIM Hardware Behavior

**Goal**: Verify subscription identification, SIM switching, and plan association on dual-SIM or eSIM+pSIM hardware.

*(Skip if physical test device does not support multiple SIMs).*

1. Insert two active SIM cards (or one physical SIM + one eSIM) with different carriers or subscriptions.
2. In Emberbyte, navigate to **Plans > Add Plan** (or edit existing plan).
3. Inspect the SIM selector:
   - Check that "Allow phone access" permission flow lists both SIM subscription carriers/slots correctly.
   - Verify options for "Default data SIM" and specific SIM cards by name/carrier.
4. Switch the default mobile data SIM in Android System Settings (**Settings > Network & internet > SIMs**).
5. Generate traffic on SIM 1, then switch to SIM 2 and generate traffic.
6. Verify Emberbyte properly records and separates traffic attributed to each subscription ID.

- **Expected Result**: Emberbyte correctly discovers dual SIMs, displays provider labels, tracks per-SIM quota against SIM-bound plans, and avoids attributing secondary SIM traffic to primary SIM plans.
- [ ] **PASS / FAIL** — Dual-SIM / Multi-SIM plan binding and traffic segregation.

---

### Step 7: Background Service Longevity & Battery Consumption (1-Hour Test)

**Goal**: Verify that Emberbyte's foreground sampler service and WorkManager tasks operate with negligible battery overhead and survive system OEM battery managers.

1. Charge the test device to >= 80% and note battery percentage.
2. In Android Settings, ensure Emberbyte's battery usage is set to **Optimized** (or **Unrestricted** if recommended for the OEM).
3. Keep Emberbyte active with ongoing notification enabled.
4. Put the device into standby / screen off for 60 minutes with background cellular or Wi-Fi data active.
5. After 1 hour, wake the device:
   - Verify the live notification is still present in the shade.
   - Open Emberbyte and verify data sampled during the 1-hour interval is captured without gaps.
   - Check **Settings > Battery > Battery usage**: verify Emberbyte accounts for < 1% of total battery drain over the hour.

- **Expected Result**: Sampler service runs uninterrupted in the background. No ANRs, forced kills, or excessive wake-locks. Battery consumption remains negligible (< 1%/hour).
- [ ] **PASS / FAIL** — 1-hour background longevity and battery impact test.

---

### Step 8: System Font Scale (200%) Reflow & Accessibility Display Size Check

**Goal**: Verify UI responsiveness and layout integrity under extreme typography and display scaling.

1. In device settings, navigate to **Settings > Accessibility > Display size and text**.
2. Set **Font size** to the maximum value (200% / Largest).
3. Set **Display size** to Large.
4. Open Emberbyte and inspect all primary screens:
   - **Home Screen**: Check that stat cards and tiles reflow cleanly to a single-column layout without clipping numerical metrics.
   - **Apps Screen**: Verify package names and byte counters do not collide or overflow card boundaries.
   - **Plan Editor**: Verify input fields, unit pickers, and helper descriptions remain visible and usable without overlapping buttons.
   - **Floating Pill Navigation**: Check that bottom navigation bar labels switch gracefully or maintain legible icon hit targets without overlapping screen content.

- **Expected Result**: All text remains legible. Numbers, units, and buttons wrap or reflow appropriately without horizontal clipping, truncation of numbers, or unclickable overlapping controls.
- [ ] **PASS / FAIL** — 200% font scale reflow and display size adaptation.

---

### Step 9: AMOLED Black and Dark Theme WCAG AA Contrast Check

**Goal**: Verify visual readability, contrast ratios, and theme switching across light, dark, and pure AMOLED black modes.

1. In device settings, toggle between **Light theme** and **Dark theme**.
2. In Emberbyte, open **Settings > Appearance**:
   - Toggle **Dynamic colour** (on Android 12+) on and off.
   - Toggle **AMOLED black** on and off in Dark theme.
3. Verify visual appearance on an OLED/AMOLED panel:
   - Verify backgrounds are `#000000` when AMOLED black is enabled.
   - Verify card surfaces and dividers provide sufficient visual separation.
   - Verify primary text, secondary labels, and state indicators (Safe, Warning, Over-cap) meet WCAG AA contrast (minimum 4.5:1 for body text, 3:1 for large text/graphics).
   - Verify status bar and navigation bar insets blend seamlessly with theme backgrounds.

- **Expected Result**: Text and data visualisations remain sharp and readable across all color palettes. AMOLED black delivers true black pixels without glowing gray bars or illegible dim text.
- [ ] **PASS / FAIL** — AMOLED black and Dark theme contrast verification.

---

### Sign-Off Summary

- **Tester Name**: _______________________
- **Device Model**: _______________________
- **Android OS Version / Build**: _______________________
- **Date Tested**: _______________________
- **Overall Result**: `[ ] ALL PASS` / `[ ] ISSUES FOUND`
- **Notes / Observations**:
