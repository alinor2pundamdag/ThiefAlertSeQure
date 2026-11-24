#!/usr/bin/env python3
"""Enforce one owner for persisted protection-state construction and transitions."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java"
CONSTRUCTOR = re.compile(r"\bProtectionState\s*\(")
ALLOWED = {
    "ProtectionEngine.kt",  # Pure authoritative state-transition rules.
    "ProtectionState.kt",   # State model and persisted-state decoder.
    "ProtectionStore.kt",   # Fail-closed state used for corrupt persistence.
}
errors = []
checked = 0

for path in sorted(JAVA.rglob("*.kt")):
    if path.name in ALLOWED:
        continue
    checked += 1
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if CONSTRUCTOR.search(line):
            errors.append(
                f"{path.relative_to(ROOT)}:{number}: direct ProtectionState construction; "
                "route the transition through ProtectionEngine"
            )

receiver = (JAVA / "com/thiefalertsequre/app/PowerTransitionReceiver.kt").read_text(encoding="utf-8")
for required in (
    "PowerTransitionQueuePolicy.createExecutor()",
    "MAX_PENDING_EVENTS = 4",
):
    if required not in receiver:
        errors.append("PowerTransitionReceiver missing bounded FIFO queue contract: " + required)

bounded_executor = (JAVA / "com/thiefalertsequre/app/BoundedSerialExecutor.kt").read_text(encoding="utf-8")
for required in (
    "ArrayBlockingQueue<Runnable>(queueCapacity)",
    "executor.queue.offer(task, backpressureTimeoutMs, TimeUnit.MILLISECONDS)",
):
    if required not in bounded_executor:
        errors.append("BoundedSerialExecutor missing bounded queue contract: " + required)

a2_receiver = (JAVA / "com/thiefalertsequre/app/A2BluetoothAclReceiver.kt").read_text(encoding="utf-8")
for required in (
    "A2BluetoothReceiverQueuePolicy.createExecutor()",
    "MAX_PENDING_EVENTS = 16",
    "A2BluetoothReceiverQueue.executor.execute",
    "pendingResult.finish()",
):
    if required not in a2_receiver:
        errors.append("A2BluetoothAclReceiver missing bounded receiver contract: " + required)
if re.search(r"\\bThread\\s*\\{", a2_receiver) or ".start()" in a2_receiver:
    errors.append("A2BluetoothAclReceiver must not create a raw thread per broadcast")

if errors:
    print("\n".join("ERROR: " + item for item in errors))
    sys.exit(1)

print(
    f"Verified ProtectionEngine/ProtectionState/ProtectionStore own state construction "
    f"across {checked} other Kotlin source files."
)
