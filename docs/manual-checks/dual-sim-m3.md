# Dual-SIM Manual Check Protocol (Milestone M3)

This document outlines the manual verification steps for dual-SIM plan binding and usage tracking on a real dual-SIM Android device.

## Prerequisites
- A physical dual-SIM Android device running Android 10+ (minSdk 29+).
- Emberbyte installed (debug APK built from the `m3-plans-forecast-widgets` branch).

## Steps

1. **Install and Grant Permissions**
   - Install Emberbyte on the dual-SIM test device.
   - Open the app, navigate to the Plans tab, and create or open a plan editor.
   - When prompted or via the plan editor SIM selector, grant phone state / phone access permission so Emberbyte can read active subscriptions (`SubscriptionManager`).

2. **Create Per-SIM Plans**
   - Create Plan A bound to SIM 1 (select subscription ID corresponding to SIM 1).
   - Create Plan B bound to SIM 2 (select subscription ID corresponding to SIM 2).
   - Verify that in the plans list and editor, the displayed SIM names and slot indices match the system settings (Settings > Network & internet > SIMs).

3. **Verify Fallback Behavior (No Phone Permission)**
   - Clear app data or revoke phone access permission.
   - Open the plan editor and verify that it gracefully handles the missing permission by offering "Default data SIM" as a fallback option instead of crashing.

4. **Test SIM Switching and Growth Attribution**
   - In system settings, set the default mobile data SIM to **SIM 1**.
   - Generate mobile data traffic (e.g. browse websites or stream video) while connected via mobile data.
   - Return to Emberbyte and verify that **Plan A** grows in usage while Plan B remains unaffected.
   - Switch the default mobile data SIM in system settings to **SIM 2**.
   - Generate mobile data traffic via SIM 2.
   - Return to Emberbyte and verify that **Plan B** grows in usage while Plan A remains unaffected.

5. **Verify Estimated Badge**
   - If a single plan window or history spans across multiple active SIM cards or when subscription attribution is estimated, verify that the plan card displays the **"Estimated"** label as specified in the backend contract.
