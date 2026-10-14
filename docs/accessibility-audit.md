# Emberbyte Accessibility Audit (`docs/accessibility-audit.md`)

Scope: Milestone 6 Polish (Contract Section 12.2)  
Device: Emulator `emulator-5554` (Density: 440 dpi, Resolution: 1080x2340)

| Screen / Dialog | 1. Roles & Names | 2. Touch Targets (>=48dp) | 3. Chart Summaries | 4. Non-Color State | 5. Font Scaling & Reflow | 6. Reduce Motion | 7. RTL & ForceLtr | 8. Contrast (WCAG AA) | 9. Focus Order | 10. Notifications |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Onboarding** | OK (onboarding_action_buttons_have_non_empty_accessible_names) | OK (onboarding_touch_targets_are_at_least_48dp) | N/A | OK | OK (Scales up to 200%) | OK | OK (ForceLtr numbers) | OK | OK | N/A |
| **Home** | OK (home_number_exposes_one_node_with_used_and_remaining_spelled_out_units, home_icon_buttons_have_content_descriptions) | OK (home_touch_targets_are_at_least_48dp) | OK (UsageBarRowAccessibilityTest) | OK | OK (at_font_scale_2_home_tiles_reflow_to_one_column_and_no_number_is_clipped) | OK | OK | OK | OK | N/A |
| **Apps** | OK (apps_icon_buttons_and_menu_items_have_content_descriptions) | OK (apps_touch_targets_are_at_least_48dp) | N/A | OK | OK | OK | OK | OK | OK | N/A |
| **App detail (incl. budget editor)** | OK (app_detail_and_budget_dialog_icon_buttons_have_content_descriptions, budget_editor_limit_field_has_label_and_error_linked_via_semantics_and_live_region) | OK (app_detail_and_budget_dialog_touch_targets_are_at_least_48dp) | OK (app_detail_usage_bar_row_has_summary_node) | OK | OK | OK | OK | OK | OK | N/A |
| **History** | OK (history_icon_buttons_have_content_descriptions) | OK (history_touch_targets_are_at_least_48dp) | OK (history_bar_chart_has_summary_and_clickable_bars) | OK | OK | OK | OK | OK | OK | N/A |
| **Plans** | OK (plans_action_buttons_have_non_empty_accessible_names) | OK (plans_screen_touch_targets_are_at_least_48dp_with_plans, plans_screen_touch_targets_are_at_least_48dp_empty_state) | N/A | OK | OK | OK | OK | OK | OK | N/A |
| **Plan editor** | OK (plan_editor_action_buttons_have_accessible_names, form_fields_have_labels_tied_to_the_fields_and_errors_linked_via_semantics) | OK (plan_editor_touch_targets_are_at_least_48dp) | N/A | OK | OK | OK | OK | OK | OK (discard_dialog_moves_focus_in_on_open_and_returns_focus_on_keep_editing) | N/A |
| **Settings (incl. Data dialogs)** | OK (switches_in_settings_are_merged_with_their_labels_into_one_clickable_node_with_role_switch) | OK (settings_touch_targets_are_at_least_48dp, data_dialogs_touch_targets_are_at_least_48dp, csv_range_dialog_touch_targets_are_at_least_48dp) | N/A | OK | OK | OK | OK | OK | OK (passphrase_dialog_moves_focus_in_on_open_and_returns_focus_on_dismiss, restore_dialog_moves_focus_in_on_open_and_returns_focus_on_dismiss) | N/A |
| **Notification texts** | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | OK (alert_and_insight_notifications_expose_full_sentences_with_spelled_out_units_and_no_lone_symbols) |
