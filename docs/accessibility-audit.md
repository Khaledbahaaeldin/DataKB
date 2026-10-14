# Emberbyte Accessibility Audit (`docs/accessibility-audit.md`)

Scope: Milestone 6 Polish (Contract Section 12.2)  
Device: Emulator `emulator-5554` (Density: 440 dpi, Resolution: 1080x2340)

| Screen / Dialog | 1. Roles & Names | 2. Touch Targets (>=48dp) | 3. Chart Summaries | 4. Non-Color State | 5. Font Scaling & Reflow | 6. Reduce Motion | 7. RTL & ForceLtr | 8. Contrast (WCAG AA) | 9. Focus Order | 10. Notifications |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Onboarding** | OK (onboarding_action_buttons_have_non_empty_accessible_names) | OK (onboarding_touch_targets_are_at_least_48dp) | N/A | OK | OK (Scales up to 200%) | OK | OK (ForceLtr numbers) | OK | OK | N/A |
| **Home** | OK (home_number_exposes_one_node_with_used_and_remaining_spelled_out_units, home_icon_buttons_have_content_descriptions) | OK (home_touch_targets_are_at_least_48dp) | OK (home_chart_exposes_summary_node_with_highest_day) | OK | OK (at_font_scale_2_home_tiles_reflow_to_one_column_and_no_number_is_clipped) | OK | OK | OK | OK | N/A |
| **Apps** | OK (apps_icon_and_filter_buttons_have_content_descriptions) | OK (apps_touch_targets_are_at_least_48dp) | N/A | OK | OK | OK | OK | OK | OK | N/A |
| **App detail (incl. budget editor)** | OK (app_detail_icon_buttons_have_content_descriptions, budget_editor_limit_field_has_label_and_error_linked_via_semantics) | OK (app_detail_touch_targets_are_at_least_48dp, budget_editor_dialog_touch_targets_are_at_least_48dp) | OK (app_detail_chart_exposes_summary_node) | OK | OK | OK | OK | OK | OK | N/A |
| **History** | OK (history_buttons_and_filter_chips_have_accessible_names) | OK (history_touch_targets_are_at_least_48dp) | OK (history_chart_has_summary_node_and_accessibility_click_selects_bar) | OK | OK | OK | OK | OK | OK | N/A |
| **Plans** | OK (plans_action_buttons_have_non_empty_accessible_names) | OK (plans_screen_touch_targets_are_at_least_48dp_with_plans, plans_screen_touch_targets_are_at_least_48dp_empty_state) | N/A | OK | OK | OK | OK | OK | OK | N/A |
| **Plan editor** | OK (plan_editor_action_buttons_have_accessible_names, form_fields_have_labels_tied_to_the_fields_and_errors_linked_via_semantics) | OK (plan_editor_touch_targets_are_at_least_48dp) | N/A | OK | OK | OK | OK | OK | OK (discard_dialog_moves_focus_in_on_open_and_returns_focus_on_keep_editing) | N/A |
| **Settings (incl. Data dialogs)** | OK (switches_in_settings_are_merged_with_their_labels_into_one_clickable_node_with_role_switch) | OK (settings_touch_targets_are_at_least_48dp, data_dialogs_touch_targets_are_at_least_48dp, csv_range_dialog_touch_targets_are_at_least_48dp) | N/A | OK | OK (create_backup_dialog_at_font_scale_2_keeps_create_and_cancel_reachable_and_clickable) | OK | OK | OK | OK (settings_create_backup_dialog_moves_focus_in_and_returns_focus_to_trigger_on_dismiss, settings_restore_backup_dialog_moves_focus_in_and_returns_focus_to_trigger_on_dismiss) | N/A |
| **Notification texts** | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | OK (live_notification_text_has_no_lone_symbols_in_accessibility_description_and_spells_out_units, alert_text_has_no_lone_symbols_and_numbers_carry_unit_words_in_accessibility_description) |
| **Open-source licences** | OK (heading semantics, selectable text container) | OK (back button and text buttons >=48dp) | N/A | OK | OK (Reflows cleanly up to font scale 2.0) | OK | OK (Mirrored header and RTL text layout) | OK (Tested contrast in static light, dark, and AMOLED black) | OK | N/A |

### Rule 10: Notification Text Platform Limitation (QA D17)

Contract section 12.2 Rule 10 requires spoken notification announcements to avoid raw symbols alone (e.g. `↓ 4.20 MB/s`) and spell out full sentences.
- **Platform Limitation:** On Android, standard platform notification views render `android.text` directly into the system shade and status bar. The platform does not allow attaching an independent `contentDescription` specifically to the standard `android.text` TextView without resorting to custom RemoteViews (which breaks system status-bar chip integration, Material You theming, and Android 16 live updates).
- **Implementation:** The spoken accessibility sentence is supplied via `NotificationCompat.Builder.setTicker(...)` (`contentDescription`), which TalkBack announces in full when the notification alerts or posts. The visible `android.text` retains the concise format (`↓ 4.20 MB/s` or `↓ 0 B/s`) for sighted viewing.

## Device Matrix Verification (`Violet_API_36` / `emulator-5554`)

Verified on Android 16 (API 36) emulator at physical resolution 1080x2340. Screenshots are archived in `.superpowers/sdd/2026-10-14-emberbyte-m6-polish/shots/`:

1. `matrix_home_font20.png`: Home screen at 200% font scale (`font_scale 2.0`). Bento tiles reflow from two columns to a single column; large usage figures and unit labels wrap cleanly with zero clipping.
2. `matrix_apps_font20.png`: Apps screen at 200% font scale. Time range chip row scrolls horizontally without truncation; per-app usage rows wrap text and data figures cleanly.
3. `matrix_app_detail_font20.png`: App Detail screen at 200% font scale. Header, mobile/Wi-Fi usage split, and budget action buttons remain legible and completely unobstructed.
4. `matrix_history_font20.png`: History screen at 200% font scale. Day/Week/Month chips scale; usage tiles reflow to single column; bar chart retains distinct interactive columns.
5. `matrix_plans_font20.png`: Plans screen at 200% font scale. Empty state card text reflows; "Add plan" button scales and maintains comfortable touch targets.
6. `matrix_plan_editor_font20.png`: Plan Editor screen at 200% font scale. Cap input, unit filter chips, renewal date selector, and save button reflow without off-screen clipping.
7. `matrix_settings_font20.png`: Settings screen at 200% font scale. Section titles, toggle switches, descriptive subtitles, and action rows reflow cleanly.
8. `matrix_dialog_backup.png`: Encrypted backup passphrase dialog at 200% font scale. Header, password field, and confirmation buttons remain fully within viewport bounds.
9. `matrix_dialog_budget_font20.png`: App budget limit dialog at 200% font scale. Limit input field and Cancel/Set actions fit comfortably on screen.
10. `matrix_density_largest.png`: Largest display size setting (`wm density 540`). System UI and Emberbyte layout scale appropriately without edge clipping.
11. `matrix_density_smallest.png`: Smallest display size setting (`wm density 360`). Content expands smoothly to fill the wider viewport canvas.
12. `matrix_home_light.png`: Static Light mode on Home screen. Verified WCAG AA contrast (>= 4.5:1 for body, >= 3.0:1 for large text/indicators) across all tiles and surfaces.
13. `matrix_home_rtl.png`: Home screen in RTL locale (`ar-XB`). Navigation pill mirrors tabs (Home on right, Plans on left); numbers and units preserve correct LTR order via `ForceLtr`.
14. `matrix_apps_rtl.png`: Apps screen in RTL locale (`ar-XB`). Range filter chips flow right-to-left (Today on right), search bar and menu align to the start edge.
15. `matrix_licenses_font20_rtl.png`: Open-source licences screen at 200% font scale in RTL locale (`ar-XB`). ScreenHeader title wraps cleanly across two lines with back button on the right; NOTICE heading and complete GPL-3.0-or-later notice reflow smoothly in selectable text with 120dp bottom clearance over the floating navigation pill.

