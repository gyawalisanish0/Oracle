# Spec — ADPF performance hints for on-device generation (future)

**Status:** planned / not scheduled for a specific release. Parked after the
adaptive-thread + core-pinning work (v1.05). Implement behind a disable flag and
measure before shipping.

## Goal
Tell Android "this is a sustained, latency-sensitive CPU workload" so the scheduler
raises **frequency and core placement** for the generation threads via `uclamp`.
Biggest payoff on mid/budget chips (e.g. Snapdragon 685) whose governors are
conservative and let cores idle-down between token steps. It is the only sanctioned
app-side lever to make the cores work harder — clock-setting is root/kernel only and
wouldn't help anyway (little cores can't reach big-core clocks, and IPC differs).

## Why native (not Kotlin `PerformanceHintManager`)
Hint sessions operate on **Linux thread IDs (TIDs)**. The threads that actually
compute are the **ggml threadpool workers** (native pthreads), not any Java thread.
So this lives in `llama-android.cpp`, using the NDK `android/performance_hint.h` API:
`APerformanceHint_getManager`, `_createSession(manager, tids[], n, targetNanos)`,
`_reportActualWorkDuration(session, ns)`, `_updateTargetWorkDuration`, `_closeSession`.

## Design

### 1. Which TIDs to hint (the crux)
- **v1 (fast, partial):** hint the decode thread — `gettid()` of the thread calling
  `llama_decode` (it is ggml worker 0 and does a full compute share). One core
  boosted; trivial; gives a first measurement.
- **v2 (full, recommended):** hint **all** worker TIDs. Two ways to obtain them:
  - *No-patch:* snapshot `/proc/self/task` immediately before and after
    `ggml_threadpool_new`; the new TIDs are the workers (creation runs on our
    dedicated native thread, so the diff is clean).
  - *Patched:* add a tiny accessor to the vendored ggml threadpool that records each
    worker's `gettid()` and returns them (we already patch ggml-vulkan, so this is
    in-bounds and more robust).
  - Try the `/proc/self/task` diff first; fall back to the ggml patch if flaky.

### 2. Session lifecycle
- Create the session in `new_context`, right after `ggml_threadpool_new` + the
  affinity attach, over the worker TIDs, with an initial target (see #4).
- Store it in a `g_hint_session` global (mirrors `g_threadpool`).
- Close it in `free_context` (before freeing the threadpool); set null.
- On model reload the threadpool is recreated → TIDs change → recreate the session
  (or `APerformanceHint_setThreads` on API 34+).

### 3. Reporting loop
- A "work unit" = one token decode. In `completion_loop`, time the per-token
  `llama_decode` with a monotonic clock, then
  `APerformanceHint_reportActualWorkDuration(session, elapsedNs)`.
- Overhead: one `clock_gettime` pair + one call per token — negligible vs a decode.

### 4. Target duration
- Target = the per-token deadline the system tries to beat. Seed with a baseline
  (median of the first few tokens), then set target ≈ **85–90%** of baseline so the
  system is nudged to ramp frequency; `updateTargetWorkDuration` if the running
  average drifts. Too aggressive wastes battery/heat; too loose does nothing — derive
  it from measured pace, never hardcode.

### 5. API gating (minSdk 26; ADPF is API 31)
- `dlopen("libandroid.so")` + `dlsym` the `APerformanceHint_*` symbols, gated by
  `android_get_device_api_level() >= 31`. No link-time dependency; clean no-op on
  older devices and on OEMs where `getManager`/`createSession` returns null.

## Files
- `llama/src/main/cpp/llama-android.cpp` — dlsym shim, `g_hint_session`, create in
  `new_context`, report in `completion_loop`, close in `free_context`, plus the TID
  capture helper.
- *(optional v2-patched)* a small vendored ggml accessor for worker TIDs.
- Kotlin: none required (fully native); optionally a runtime flag to disable.

## Risks / caveats
- **It's a hint, not a guarantee.** OEM support varies; some devices return a null
  manager or ignore sessions → must degrade to a no-op.
- **Power/thermal trade-off.** Boosting raises frequency → more heat/battery; ADPF
  balances it, but an over-aggressive target can cause thermal throttling. Keep the
  target heuristic conservative.
- **TID validity.** Hinting stale TIDs after a reload is wasted/errors → tie the
  session strictly to the threadpool lifecycle.
- **Interaction with affinity.** ADPF (frequency) and pinning (placement) are
  complementary, but pinning to little cores caps the achievable boost — another
  argument for big-core-biased pinning.

## Verification
- **Logcat:** session created vs null (per device); chosen target.
- **Benchmark:** in-app tok/s, hint on vs off, on a real device (Pixel = good ADPF
  support; the 685 = the real target). This is the arbiter.
- **Thermal/battery sanity:** a long generation shouldn't throttle worse than baseline.
- **API < 31 device:** confirm the no-op path.

## Rollout
Separate experiment, behind a disable flag, measured before shipping. v1
(decode-thread only) first for a quick signal; v2 (all workers) if v1 shows promise.
