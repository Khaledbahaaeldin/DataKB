# Emberbyte Accessibility Audit (`docs/accessibility-audit.md`)

Scope: Milestone 6 Polish (Contract Section 12.2)  
Device: Emulator `emulator-5554` (Density: 440 dpi, Resolution: 1080x2340)

| Screen / Dialog | 1. Roles & Names | 2. Touch Targets (>=48dp) | 3. Chart Summaries | 4. Non-Color State | 5. Font Scaling & Reflow | 6. Reduce Motion | 7. RTL & ForceLtr | 8. Contrast (WCAG AA) | 9. Focus Order | 10. Notifications |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Onboarding** | FIX (Missing content descriptions on action buttons) | FIX (Some buttons <48dp) | N/A | OK | OK (Scales up to 200%) | OK | OK (ForceLtr numbers) | OK | OK | N/A |
| **Home** | OK (home_number_exposes_one_node_with_used_and_remaining_spelled_out_units, home_icon_buttons_have_content_descriptions) | OK (home_touch_targets_are_at_least_48dp) | OK (UsageBarRowAccessibilityTest) | OK | OK (at_font_scale_2_home_tiles_reflow_to_one_column_and_no_number_is_clipped) | OK | OK | OK | OK | N/A |
| **Apps** | OK (apps_icon_buttons_and_menu_items_have_content_descriptions) | OK (apps_touch_targets_are_at_least_48dp) | N/A | OK | OK | OK | OK | OK | OK | N/A |
| **App detail (incl. budget editor)** | OK (app_detail_and_budget_dialog_icon_buttons_have_content_descriptions) | OK (app_detail_and_budget_dialog_touch_targets_are_at_least_48dp) | OK (app_detail_usage_bar_row_has_summary_node) | OK | OK | OK | OK | OK | OK | N/A |
| **History** | OK (history_icon_buttons_have_content_descriptions) | OK (history_touch_targets_are_at_least_48dp) | OK (history_bar_chart_has_summary_and_clickable_bars) | OK | OK | OK | OK | OK | OK | N/A |
| **Plans** | FIX (Action buttons missing descriptions) | FIX (<48dp targets) | N/A | OK | OK | OK | OK | OK | OK | N/A |
| **Plan editor** | FIX (Back/save buttons missing names) | FIX (<48dp targets) | N/A | OK | OK | OK | OK | OK | OK | N/A |
| **Settings (incl. Data dialogs)** | FIX (Dialog buttons/icon buttons missing descriptions) | FIX (<48dp targets) | N/A | OK | OK | OK | OK | OK | OK | N/A |
| **Notification texts** | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | FIX (Complete sentences, no raw symbols) |
