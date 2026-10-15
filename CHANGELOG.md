# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-10-14

### Added
- **Live Transfer Speed & Sampler**: Real-time foreground service sampling network rates, with a live download speed notification and Android 16+ Promoted Ongoing remaining data indicator.
- **Traffic Totals & Per-App History**: Breakdown of cellular mobile data and Wi-Fi network usage across 2-hour and daily intervals via `NetworkStatsManager`.
- **Flexible Data Plans**: Support for custom billing cycles (monthly or every N days), SIM slot binding, add-on quota packs, free data windows (e.g. night data), and usage rollover.
- **Personalized Quota Forecast**: Predictive calculation of cap run-out dates based on a learned hourly consumption curve over the last 28 days.
- **Threshold & Budget Alerts**: Configurable notifications when reaching 50%, 80%, 90%, and 100% of plan allowances, plus per-app mobile data budgets.
- **Spike Detector**: Automated detection and alerting for anomalous mobile data consumption compared to a 14-day median baseline.
- **"What If" Scenario Planner**: Projection of quota run-out dates under hypothetical additional daily consumption.
- **Streamed Data Export**: Memory-efficient streaming export of historical usage metrics and plans to CSV and JSON formats.
- **Encrypted Local Backups**: Full backup and restore of plans, budgets, and hourly usage history using AES-256-GCM encryption with Argon2id password-based key derivation (`.emberbyte`).
- **Home-Screen Widgets**: Glance-based widgets displaying remaining plan balance, cycle status, and usage progress.
- **Material 3 Expressive UI**: Dynamic color theming, pure black AMOLED dark mode, fluid motion transitions, and full accessibility support (TalkBack, font scale 2.0).
- **100% On-Device Privacy**: Operates completely offline without accounts, cloud sync, or `INTERNET` permission.
