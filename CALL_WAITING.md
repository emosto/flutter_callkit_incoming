# Voxbi call waiting bridge

This fork supports Voxbi's application-owned two-call arbiter. Do not use the
new revision with a host that does not acknowledge pending iOS call actions.

- `updateCallWaitingState`: `{calls: [{id, state}], audioInterrupted: bool,
  labels: {waiting, reject, holdAnswer, endAnswer}}`. Updates the native cue,
  Android Telecom state and localized waiting notification labels.
- `setCallWaitingHeld`: `{id, held}` synchronizes confirmed SIP hold with native
  state; it must not initiate another SIP transaction.
- `completeCallWaitingAction`: `{actionId, success}` completes the pending iOS
  answer/hold action. Events and active-call recovery expose `actionId`.
- The global setting is the Flutter legacy preferences bool
  `flutter.callWaitingEnabled`, default true.

Disabled/full iOS arrivals are reported to CallKit before being ended; Android
arrivals are silently suppressed. No backend decline request is sent. Native
logs explicitly say the operation is suppressed. The Dart host handles user
rejection through its injectable log-only adapter.

The native layer mirrors state and preserves the existing audio-route owner.
Android uses the voice-call stream for its low-volume waiting tone. On iOS,
CallKit supplies the initial waiting indication; native reminders start after
10 seconds to avoid a duplicate initial cue. Device validation is required.

Tests: Android `:flutter_callkit_incoming:testDebugUnitTest`; iOS admission
coverage is in the Voxbi host RunnerTests target.
