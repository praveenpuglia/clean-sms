#!/usr/bin/env bash
# End-to-end smoke test on an emulator: real modem-delivered SMS through the system broadcast.
# Covers what instrumented tests can't: SMS_DELIVER dispatch, goAsync, cold-start delivery.
# Usage: scripts/e2e-smoke.sh   (requires a running emulator; installs the debug build)
set -euo pipefail
P=com.praveenpuglia.cleansms
fail() { echo "FAIL: $*"; exit 1; }

./gradlew -q installDebug
adb shell cmd role add-role-holder android.app.role.SMS $P 0
adb shell pm grant $P android.permission.POST_NOTIFICATIONS
adb shell am force-stop $P # deliver into a cold process

SENDER="555$(( RANDOM % 9000 + 1000 ))"
adb emu sms send "$SENDER" "Smoke code 482913 is your OTP" >/dev/null
for _ in $(seq 1 20); do
  adb shell content query --uri content://sms/inbox --projection read --where "address=\'$SENDER\'" | grep -q "read=0" && break
  sleep 0.5
done
adb shell content query --uri content://sms/inbox --projection read --where "address=\'$SENDER\'" | grep -q "read=0" \
  || fail "incoming SMS was not stored unread"
adb shell dumpsys notification --noredact | grep "pkg=$P" | grep -q "channel=otp_sms" \
  || fail "OTP notification was not posted"
adb logcat -d -b crash | grep -q "$P" && fail "crash logged for $P"

adb shell content delete --uri content://sms --where "address=\'$SENDER\'" >/dev/null
echo "PASS: e2e smoke"
