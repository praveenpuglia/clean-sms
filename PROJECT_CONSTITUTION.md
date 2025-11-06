# Clean SMS Project Constitution

## 1. Purpose & Scope
Clean SMS is a privacy‑respectful, focused Android SMS client providing a clean inbox, OTP surfacing, and streamlined message composition. This document defines the enduring architectural principles, UI/UX rules, coding standards, and constraints that govern all future changes.

## 2. Platform & Tech Stack
- Target Platform: Native Android app (Kotlin).
- Minimum SDK: (Set according to `build.gradle.kts`; raise only with justification.)
- Language: Kotlin only for production code; Java allowed solely for interop or legacy system APIs if unavoidable.
- UI Toolkit: Android Views + Material Components (Material Design 3).
- Dependency Injection: (If added in future) Keep lightweight; avoid large frameworks unless compelling value.
- Threading: Prefer coroutines + structured concurrency; avoid ad hoc thread spawning.

## 3. Material Design 3 Compliance
All UI surfaces MUST comply with Material Design 3 (M3):
- Follow official guidelines: https://github.com/material-components/material-components-android/tree/master/docs/components
- Use Material Components Views where available (`MaterialToolbar`, `MaterialCardView`, `TextInputLayout`, `Chip`, `FloatingActionButton`, etc.).
- Typography: Use Material3 text appearances (`TextAppearance.Material3.*`) or theme tokens rather than hardcoded sizes.
- Shapes: Adopt M3 Shape tokens; avoid arbitrary corner radii.
- Elevation & Shadow: Use elevation only where semantically meaningful (e.g., top app bar, modal surfaces). Avoid stacking multiple elevated containers.
- States & Ripple: Use Material default ripple/state layers; do not implement custom press animations unless consistent with M3 guidance.

## 4. Dynamic Color & Theming
- Dynamic Color: App theme must derive color roles from `MaterialColors` / dynamic color APIs when available (Android 12+). Fall back to static M3 palette for earlier versions.
- Theme Layering: Single source of truth in `Theme` definition (XML + possibly Compose later). No inline color literals for semantic roles—always reference theme attributes (`?attr/colorSurface`, `?attr/colorPrimary`, etc.).
- Dark Mode: Provide parity in dark theme using dynamic color roles; verify contrast (WCAG AA wherever practical).
- Iconography & Contrast: Icons must respect `colorOnSurfaceVariant` or `colorOnSurface` depending on emphasis.

## 5. UX / Interaction Principles
- Simplicity: Minimize friction—actions should be discoverable without multi-step onboarding.
- Non-Intrusive OTP surfacing: OTP detection must be high precision; avoid aggressive heuristics that produce false positives.
- Compose Flow: Recipient selection & message composition must avoid modal interruptions or auto-navigation unless explicitly initiated by user.
- Feedback: Every send action must provide immediate user feedback (toast/snackbar) and visually reflect message state where possible.
- Accessibility: Maintain minimum touch target of 48dp; support TalkBack (content descriptions for iconic buttons); color contrast compliance.

## 6. Architectural Principles
- Separation of Concerns: UI activities/fragments handle rendering & user interaction; data access (ContentResolver queries) encapsulated in repository-style helpers when complexity grows.
- No implicit navigation side-effects (e.g., sending navigating automatically to thread unless user requested).
- Defensive Queries: Always null/empty-check cursor columns; close cursors in `use {}` blocks.
- Lazy Loading: Load contacts/index opportunistically; avoid blocking initial render.
- Performance: OTP & thread enrichment must not block UI (run in background thread / coroutine).

## 7. Data & Permissions
- Read minimal data required: Do not prefetch full SMS bodies where metadata suffices.
- Runtime Permissions: Request only when needed; educate user if denying impacts core features.
- Default SMS Role: Features requiring provider write (e.g., inserting sent messages) must degrade gracefully when role not granted.

## 8. Code Style & Quality
- Kotlin Style: Idiomatic Kotlin (data classes, extension functions sparingly, avoid long parameter lists).
- Null Safety: Prefer non-null fields only when invariant guaranteed; otherwise use nullable types + explicit handling.
- Logging: Use structured log tags. Avoid leaking PII (phone numbers) beyond necessary debug context; strip/shorten numbers where feasible.
- Error Handling: Catch specific exceptions; no broad `catch (Exception)` unless wrapping/annotating & rethrowing or failing gracefully.
- Comments: Provide rationale over restating what code does.

## 9. UI Components Standards
- Toolbars: Always `MaterialToolbar` with consistent height (`?attr/actionBarSize`) and dynamic color background.
- Lists: Use `RecyclerView` with stable IDs when item identity matters; diffing (ListAdapter / DiffUtil) for dynamic sets.
- Chips: Use Material3 Input/Assist chips for recipients; support removal via close icon; display avatar if available (circular).
- Text Fields: Always `TextInputLayout` + `TextInputEditText` for form-like input; avoid plain `EditText` unless justified (performance or unstyled ephemeral input).
- Cards: Use `MaterialCardView` for grouping related interactive elements inside backgrounds distinct from the parent surface.

## 10. Message Composition Rules
- Recipient Entry: Supports multiple recipients. Backspace on empty input removes last chip.
- Raw Number Handling: Accept onset numbers not in contacts; treat them uniformly as recipients.
- No Auto Navigation: Sending does not open any thread view automatically; user remains in context or returns to previous list based on explicit decision.
- Visual Consistency: Chips wrap vertically without overlapping input; spacing adheres to 4–8dp multiples.

## 11. Testing & Verification
- Manual Verification: Validate dynamic color extraction on Android 12+ devices/emulators and fallback palette on older versions.
- Edge Cases: Empty inbox, large contact count, long phone numbers, dual SIM presence, denied permissions, not default SMS app.
- Regression Safety: Changes to OTP detection logic gated behind precision checks; maintain test harness stub for typical OTP patterns.

## 12. Performance & Resource Constraints
- Avoid excessive allocations when loading large contact lists—consider incremental loading or caching.
- Limit on-device queries frequency; debounce or batch where appropriate.
- Memory: Recycle bitmaps for contact photos when large; prefer content URIs rather than storing raw base64 data.

## 13. Privacy & Security
- No external network calls for SMS/content enrichment without explicit user opt-in.
- Do not transmit messages or OTPs off-device.
- Avoid logging full SMS bodies or OTP values; use placeholders for debugging.

## 14. Extensibility Guidelines
- New feature proposals must state: user value, adherence to constitution, required permissions, and failure behavior when permission absent.
- Introduce Jetpack Compose only if parity with existing M3 theming + dynamic color achievable; start with isolated surfaces.
- Provide migration path documentation if refactoring major components.

## 15. Dependency Management
- Keep dependencies minimal; prefer AndroidX + Material libraries.
- Version updates: Bump Material Components consciously—verify any breaking change in shape tokens or color roles.
- No experimental libraries unless evaluated for stability and maintenance burden.

## 16. Internationalization & Formatting
- Phone number normalization leverages `PhoneNumberUtil` where feasible; fallback to digit filtering.
- Strings externalized in `strings.xml`; avoid concatenating dynamic user data with static phrases without formatting placeholders.

## 17. Release & Build Constraints
- Build must produce a lint-clean (or justified suppressed) artifact.
- Warnings related to deprecated APIs (e.g., `SmsManager.getDefault()`) tracked and replaced with modern equivalents as soon as stable alternatives confirm compatibility.
- Proguard/R8 configuration ensures no removal of reflection-required SMS/contacts APIs.

## 18. Accessibility & Inclusivity
- Provide content descriptions for all icon-only buttons (Back, Send, FAB, Delete).
- Ensure minimum contrast on dynamic color-determined backgrounds (automated spot checks during QA).
- Support large font scaling (chips wrap properly; text fields expand vertically).

## 19. Governance & Change Control
- All changes must state which constitution sections they impact (reference section numbers in PR description).
- Breaking deviations require explicit amendment of this constitution with rationale (version this document; maintain change log).

## 20. Future Considerations
- Potential Compose migration path; ensure theme parity first.
- Encryption of stored messages (if later extended beyond system provider).
- Automated snapshot tests for critical UI flows (recipient chips, OTP list rendering).

---
_Last updated: 2025-11-06_

Amendment Procedure: Propose changes via PR titled `Constitution: Amendment <short description>` referencing affected sections. Approval requires review confirming user impact and consistency with project principles.
