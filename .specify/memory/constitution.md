
<!--
Sync Impact Report
Version change: 1.0.0 → 1.1.0
Modified principles: Simplicity → Simplicity & Precision, User Experience & Notifications → User Experience & Material You
Added: Material You design, native layouts, device/emulator launch requirements
Templates requiring updates: ✅ plan-template.md, ✅ spec-template.md, ✅ tasks-template.md
Follow-up TODOs: TODO(RATIFICATION_DATE): Original ratification date unknown, set when known.

# CleanSMS Constitution

## Core Principles

### I. Native Android (NON-NEGOTIABLE)
The application MUST be implemented as a native Android app using Kotlin. No cross-platform or hybrid frameworks are permitted. All code must target the Android SDK directly.
Rationale: Ensures best performance, compatibility, and user experience for Android users.

### II. SMS Core Functionality
The app MUST reliably send and receive SMS messages. All message delivery and receipt must be handled using Android's official SMS APIs. Failure to deliver or receive messages is considered a critical defect.
Rationale: SMS is the core purpose of the application.

### III. Performance & Reliability
The app MUST be responsive and performant, with message send/receive actions completing within 1 second under normal conditions. Crashes, message loss, or UI freezes are not permitted.
Rationale: Users expect instant, reliable communication.


### IV. Simplicity & Precision
The user interface and codebase MUST remain simple, minimal, and precise. Avoid unnecessary features, bloat, or complexity. All code and documentation MUST be succinct and serve a clear purpose. Every UI element and code module must have a justified, user-facing reason for existence.
Rationale: Simplicity and precision ensure maintainability and a sleek, focused user experience.


### V. User Experience & Material You
The app MUST use the Material You design language and native Android layouts. All UI must be visually consistent with modern Android standards. The app MUST notify users promptly when a new SMS arrives, using Android's notification system. Notifications must be clear, actionable, and respect user privacy settings.
Rationale: Modern, native design and timely notifications are essential for user trust and utility.
### VI. Device & Emulator Launch
The app MUST run on a connected test device. If no device is detected, it MUST launch in an Android emulator. This requirement applies to both development and automated test workflows.
Rationale: Ensures reliable, repeatable testing and development.



## Platform, Security & Design Constraints

- The app MUST use only officially supported Android APIs for SMS, notifications, and UI.
- All user data (messages, contacts) MUST be handled securely and never shared without explicit user consent.
- The app MUST comply with Google Play policies, Android security best practices, and Material You design guidelines.


## Development Workflow & Quality Gates

- All code changes MUST be reviewed before merging.
- Automated and manual tests MUST verify SMS send/receive, notification, and UI flows.
- Releases MUST pass all tests and be validated on at least two Android OS versions.


## Governance

- This constitution supersedes all other project practices.
- Amendments require documentation, review, and explicit approval by the project maintainer.
- All PRs and reviews MUST verify compliance with these principles.
- Versioning follows semantic versioning: MAJOR for breaking/removal, MINOR for new/expanded principles, PATCH for clarifications.
- TODO(RATIFICATION_DATE): Original ratification date unknown, set when known.

**Version**: 1.1.0 | **Ratified**: TODO(RATIFICATION_DATE): Original ratification date unknown | **Last Amended**: 2025-10-24
