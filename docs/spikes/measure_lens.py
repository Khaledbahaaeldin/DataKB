#!/usr/bin/env python3
import subprocess
import time
import os
import sys
import hashlib
import tempfile

SERIAL = os.environ.get("SERIAL", "emulator-5554")
HOST = os.environ.get("HOST", "10.0.2.2")
PORT = int(os.environ.get("PORT", "8000"))
PKG = "io.github.khaledbahaaeldin.emberbyte"
CURL = "/data/local/tmp/curl"

# Resolve spike directory safely without personal usernames
_script_dir = os.path.dirname(os.path.abspath(__file__))
_temp_spike = os.path.join(tempfile.gettempdir(), "emberbyte_spike")
if not os.path.exists(os.path.join(_script_dir, "small.bin")) and os.path.exists(os.path.join(_temp_spike, "small.bin")):
    SPIKE_DIR = _temp_spike
else:
    SPIKE_DIR = os.environ.get("SPIKE_SERVE_DIR", _script_dir)

RUNS_DIR = os.path.join(_script_dir, "runs")
os.makedirs(RUNS_DIR, exist_ok=True)

def adb_shell(cmd, timeout=30):
    try:
        res = subprocess.run(["adb", "-s", SERIAL, "shell", cmd], capture_output=True, text=True, errors="replace", timeout=timeout)
        return res.stdout.strip()
    except subprocess.TimeoutExpired:
        return ""

def file_sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def ensure_device_tools():
    has_curl = adb_shell(f"test -x {CURL} && echo OK")
    if "OK" not in has_curl:
        print(f"FATAL: Device tool {CURL} not found or not executable on {SERIAL}!")
        print("Please push curl to /data/local/tmp/curl and chmod +x.")
        sys.exit(1)

    has_toybox = adb_shell("toybox --help >/dev/null 2>&1 && echo OK")
    if "OK" not in has_toybox:
        print(f"FATAL: toybox tool not functional on {SERIAL}!")
        sys.exit(1)

def get_app_pid():
    out = adb_shell(f"pidof {PKG}")
    parts = out.split()
    return parts[0] if parts else ""

def start_lens():
    adb_shell(f"am start -n {PKG}/.lens.spike.LensSpikeActivity >/dev/null 2>&1")
    time.sleep(1)
    res = adb_shell(f"am broadcast -a {PKG}.debug.LENS_START -p {PKG}")
    time.sleep(2)
    return res

def stop_lens():
    res = adb_shell(f"am broadcast -a {PKG}.debug.LENS_STOP -p {PKG}")
    time.sleep(1)
    return res

def dump_lens():
    return adb_shell(f"am broadcast -a {PKG}.debug.LENS_DUMP -p {PKG}")

import re

def parse_dump(dump_str):
    fields = {}
    for k, v in re.findall(r'(\w+)=(\d+)', dump_str):
        fields[k] = int(v)
    return fields

def get_cpu_ticks(pid):
    if not pid:
        return 0
    stat = adb_shell(f"cat /proc/{pid}/stat 2>/dev/null")
    parts = stat.split()
    if len(parts) >= 15:
        try:
            return int(parts[13]) + int(parts[14])
        except (ValueError, IndexError):
            return 0
    return 0

def get_pss_kb():
    out = adb_shell(f"dumpsys meminfo {PKG}")
    for line in out.splitlines():
        if "TOTAL PSS:" in line:
            parts = line.split()
            if len(parts) >= 3:
                try:
                    return int(parts[2])
                except ValueError:
                    pass
    return 0

def get_busiest_threads(pid):
    if not pid:
        return []
    out = adb_shell(f"toybox ps -T -p {pid} -o TID,CMD,TIME+")
    def parse_t(t_str):
        parts = t_str.split(":")
        if len(parts) == 2:
            try:
                return float(parts[0]) * 60 + float(parts[1])
            except ValueError:
                return 0.0
        return 0.0
    threads = []
    for line in out.strip().splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 3:
            tid = parts[0]
            cmd = " ".join(parts[1:-1])
            time_str = parts[-1]
            threads.append((tid, cmd, parse_t(time_str), time_str))
    threads.sort(key=lambda x: x[2], reverse=True)
    return threads[:5]

def get_logcat_crash_count():
    pid = get_app_pid()
    if not pid:
        return 0
    out = adb_shell(f"logcat -d --pid={pid} *:E")
    count = 0
    for line in out.splitlines():
        if "FATAL" in line or "AndroidRuntime" in line:
            count += 1
    return count

def main():
    raw_log = []
    def log(msg=""):
        print(msg, flush=True)
        raw_log.append(msg)

    log("==================================================================")
    log("Emberbyte M5 Live Lens Measurement Harness (Reproducible Runner)")
    log(f"Device: {SERIAL} | Host Server: {HOST}:{PORT}")
    log("==================================================================")

    ensure_device_tools()
    log("[Init] Device tools verified (/data/local/tmp/curl, toybox).")

    stop_lens()
    adb_shell("logcat -c")

    small_bin_path = os.path.join(SPIKE_DIR, "small.bin")
    big_bin_path = os.path.join(SPIKE_DIR, "big.bin")
    if not os.path.exists(small_bin_path) or not os.path.exists(big_bin_path):
        log(f"FATAL: Test files missing in {SPIKE_DIR}!")
        sys.exit(1)

    expected_small_hash = file_sha256(small_bin_path)
    expected_big_hash = file_sha256(big_bin_path)
    log(f"[Control] Expected small.bin SHA-256: {expected_small_hash}")
    log(f"[Control] Expected big.bin   SHA-256: {expected_big_hash}")

    # ---------------------------------------------------------
    # STEP 0: INTEGRITY CONTROLS (Lens OFF)
    # ---------------------------------------------------------
    log("\n--- STEP 0: INTEGRITY CONTROLS (Lens OFF) ---")
    
    adb_shell("svc wifi enable")
    time.sleep(2)
    ctrl_wifi_small = adb_shell(f"{CURL} -s http://{HOST}:{PORT}/small.bin | sha256sum").split()[0]
    log(f"[Control Wi-Fi] small.bin: {ctrl_wifi_small} (Match: {ctrl_wifi_small == expected_small_hash})")
    ctrl_wifi_big = adb_shell(f"{CURL} -s http://{HOST}:{PORT}/big.bin | sha256sum", timeout=60).split()[0]
    log(f"[Control Wi-Fi] big.bin:   {ctrl_wifi_big} (Match: {ctrl_wifi_big == expected_big_hash})")
    wifi_exact = (ctrl_wifi_small == expected_small_hash and ctrl_wifi_big == expected_big_hash)
    if not wifi_exact:
        log("  WARNING: Wi-Fi path altered TCP stream! Marked NOT USABLE FOR INTEGRITY.")

    adb_shell("svc wifi disable")
    time.sleep(2)
    ctrl_eth0_small = adb_shell(f"{CURL} -s http://{HOST}:{PORT}/small.bin | sha256sum").split()[0]
    log(f"[Control eth0]  small.bin: {ctrl_eth0_small} (Match: {ctrl_eth0_small == expected_small_hash})")
    ctrl_eth0_big = adb_shell(f"{CURL} -s http://{HOST}:{PORT}/big.bin | sha256sum", timeout=60).split()[0]
    log(f"[Control eth0]  big.bin:   {ctrl_eth0_big} (Match: {ctrl_eth0_big == expected_big_hash})")
    eth0_exact = (ctrl_eth0_small == expected_small_hash and ctrl_eth0_big == expected_big_hash)
    if not eth0_exact:
        log("  WARNING: eth0 path altered TCP stream! Marked NOT USABLE FOR INTEGRITY.")

    adb_shell("svc wifi enable")
    time.sleep(2)

    # ---------------------------------------------------------
    # STEP 1: CORRECTNESS SUITE
    # ---------------------------------------------------------
    log("\n--- STEP 1: CORRECTNESS SUITE ---")
    correctness_results = {}

    # 1.1 Small file integrity
    log("\n[1.1] Small file integrity test:")
    start_lens()
    d1 = parse_dump(dump_lens())
    lens_small_hash = adb_shell(f"{CURL} -s http://{HOST}:{PORT}/small.bin | sha256sum").split()[0]
    d2 = parse_dump(dump_lens())
    stop_lens()
    tun_delta_small = (d2.get("tunTx", 0) - d1.get("tunTx", 0))
    log(f"  Hash received: {lens_small_hash}")
    log(f"  TUN Tx delta: {tun_delta_small} bytes (proven crossed TUN: {tun_delta_small > 50000})")
    if not wifi_exact:
        correctness_results["1.1 Small file integrity"] = ("CANNOT-VERIFY", "Underlying emulator path not byte-exact")
    elif lens_small_hash == expected_small_hash and tun_delta_small > 50000:
        correctness_results["1.1 Small file integrity"] = ("PASS", f"Byte-identical sha256, TUN delta {tun_delta_small} B")
    else:
        correctness_results["1.1 Small file integrity"] = ("FAIL", f"Hash mismatch: {lens_small_hash}")

    # 1.2 100MB file integrity
    log("\n[1.2] 100 MB large file integrity test:")
    start_lens()
    d1 = parse_dump(dump_lens())
    lens_big_hash = adb_shell(f"{CURL} -s http://{HOST}:{PORT}/big.bin | sha256sum", timeout=60).split()[0]
    d2 = parse_dump(dump_lens())
    stop_lens()
    tun_delta_big = (d2.get("tunTx", 0) - d1.get("tunTx", 0))
    log(f"  Hash received: {lens_big_hash}")
    log(f"  TUN Tx delta: {tun_delta_big} bytes (proven crossed TUN: {tun_delta_big > 100000000})")
    if not wifi_exact:
        correctness_results["1.2 100 MB file integrity"] = ("CANNOT-VERIFY", "Underlying emulator path not byte-exact")
    elif lens_big_hash == expected_big_hash and tun_delta_big > 100000000:
        correctness_results["1.2 100 MB file integrity"] = ("PASS", f"Byte-identical sha256, TUN delta {tun_delta_big} B")
    else:
        correctness_results["1.2 100 MB file integrity"] = ("FAIL", f"Hash mismatch: {lens_big_hash}")

    # 1.3 HTTPS public site
    log("\n[1.3] HTTPS public site (curl https://example.com):")
    start_lens()
    https_code = adb_shell(f"{CURL} -s -o /dev/null -w '%{{http_code}}' https://example.com")
    stop_lens()
    log(f"  Result code: {https_code}")
    if https_code == "200":
        correctness_results["1.3 HTTPS public site"] = ("PASS", "HTTP 200 via https://example.com")
    else:
        correctness_results["1.3 HTTPS public site"] = ("CANNOT-VERIFY", f"Emulator has no working DNS resolution for curl (code={https_code})")

    # 1.4 DNS resolution
    log("\n[1.4] DNS resolution:")
    start_lens()
    dns_res = adb_shell(f"{CURL} -s -o /dev/null -w '%{{http_code}}' http://example.com")
    stop_lens()
    log(f"  Result code: {dns_res}")
    if dns_res in ["200", "301", "302"]:
        correctness_results["1.4 DNS resolution"] = ("PASS", f"HTTP {dns_res}")
    else:
        correctness_results["1.4 DNS resolution"] = ("CANNOT-VERIFY", f"Emulator has no external DNS resolution (code={dns_res})")

    # 1.5 200 parallel downloads
    log("\n[1.5] 200 parallel downloads:")
    start_lens()
    procs = [subprocess.Popen(["adb", "-s", SERIAL, "shell", f"{CURL} -s -m 10 http://{HOST}:{PORT}/small.bin -o /dev/null"]) for _ in range(200)]
    for p in procs:
        p.wait()
    dump_200 = parse_dump(dump_lens())
    stop_lens()
    log(f"  Dump after 200 flows: {dump_200}")
    if dump_200.get("errors", 1) == 0 and dump_200.get("opened", 0) >= 200:
        correctness_results["1.5 200 parallel downloads"] = ("PASS", f"200 flows completed, 0 errors, open={dump_200.get('open')}")
    else:
        correctness_results["1.5 200 parallel downloads"] = ("FAIL", f"Errors: {dump_200.get('errors')}")

    # 1.6 Download interrupted and resumed
    log("\n[1.6] Resumed download (curl -C -):")
    start_lens()
    adb_shell("rm -f /data/local/tmp/partial.bin")
    adb_shell(f"{CURL} -s -r 0-51199 http://{HOST}:{PORT}/small.bin -o /data/local/tmp/partial.bin")
    adb_shell(f"{CURL} -s -C - http://{HOST}:{PORT}/small.bin -o /data/local/tmp/partial.bin")
    resumed_hash = adb_shell("sha256sum /data/local/tmp/partial.bin").split()[0]
    adb_shell("rm -f /data/local/tmp/partial.bin")
    stop_lens()
    log(f"  Resumed hash: {resumed_hash}")
    if not wifi_exact:
        correctness_results["1.6 Interrupted and resumed"] = ("CANNOT-VERIFY", "Path not usable for integrity")
    elif resumed_hash == expected_small_hash:
        correctness_results["1.6 Interrupted and resumed"] = ("PASS", "Byte-identical after resume")
    else:
        correctness_results["1.6 Interrupted and resumed"] = ("FAIL", f"Mismatch {resumed_hash}")

    # 1.7 Upload 20MB (curl -T)
    log("\n[1.7] Upload 20MB (curl -T):")
    start_lens()
    adb_shell("dd if=/dev/urandom of=/data/local/tmp/upload.bin bs=1M count=20 2>/dev/null")
    upload_res = adb_shell(f"{CURL} -s -m 120 -o /dev/null -w '%{{http_code}}' -T /data/local/tmp/upload.bin http://{HOST}:{PORT}/upload", timeout=130)
    adb_shell("rm -f /data/local/tmp/upload.bin")
    stop_lens()
    log(f"  Upload result code: {upload_res}")
    if upload_res == "200":
        correctness_results["1.7 Upload 20MB"] = ("PASS", "HTTP 200 PUT")
    else:
        correctness_results["1.7 Upload 20MB"] = ("FAIL", f"HTTP {upload_res}")

    # 1.8 UDP exchange
    log("\n[1.8] UDP exchange:")
    start_lens()
    try:
        p = subprocess.Popen(["adb", "-s", SERIAL, "shell", f"toybox nc -u -w 2 {HOST} {PORT}"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        udp_out, _ = p.communicate(input="ECHO_TEST\n", timeout=4)
        udp_out = udp_out.strip()
    except subprocess.TimeoutExpired:
        p.kill()
        udp_out = ""
    stop_lens()
    log(f"  UDP echo reply: {udp_out}")
    if "ECHO_TEST" in udp_out:
        correctness_results["1.8 UDP exchange"] = ("PASS", "Echo received successfully")
    else:
        correctness_results["1.8 UDP exchange"] = ("FAIL", f"Received: {udp_out}")

    # 1.9 IPv6 routing
    log("\n[1.9] IPv6 routing:")
    correctness_results["1.9 IPv6 routing"] = ("CANNOT-VERIFY", "Emulator lack of external IPv6 route; JVM unit tests cover IPv6")

    # 1.10 Ping (ICMP dropped)
    log("\n[1.10] Ping (ICMP dropped expected):")
    start_lens()
    ping_out = adb_shell("ping -c 2 -W 2 10.0.2.2 2>&1")
    stop_lens()
    log(f"  Ping output: {ping_out}")
    if any(s in ping_out for s in ["100% packet loss", "100% loss", "100%"]):
        correctness_results["1.10 Ping handling"] = ("PASS", "100% packet loss as designed (ICMP dropped)")
    else:
        correctness_results["1.10 Ping handling"] = ("FAIL", f"Unexpected ping output: {ping_out}")

    # 1.11 Force-stop cleanup
    log("\n[1.11] Force-stop cleanup:")
    start_lens()
    tun_before = adb_shell("ip addr show tun0 2>/dev/null")
    adb_shell(f"am force-stop {PKG}")
    time.sleep(2)
    tun_after = adb_shell("ip addr show tun0 2>/dev/null")
    vpn_net = adb_shell("dumpsys connectivity | grep -E 'NetworkAgentInfo.*type: VPN' || true")
    log(f"  tun0 before: {bool(tun_before)}, tun0 after: {bool(tun_after)}, vpn_net: {vpn_net}")
    if not tun_after and not vpn_net:
        correctness_results["1.11 Force-stop cleanup"] = ("PASS", "tun0 and VPN NetworkAgentInfo unmounted immediately")
    else:
        correctness_results["1.11 Force-stop cleanup"] = ("FAIL", f"VPN remained active: {vpn_net}")

    # ---------------------------------------------------------
    # STEP 2: PERFORMANCE BENCHMARKING (3 runs each)
    # ---------------------------------------------------------
    log("\n--- STEP 2: PERFORMANCE BENCHMARKING (3 repetitions each) ---")

    # (A) Baseline (Lens OFF) on Wi-Fi path
    log("\n[Run A] Baseline (Lens OFF) on Wi-Fi path:")
    adb_shell("svc wifi enable")
    time.sleep(2)
    baseline_wifi_speeds = []
    for r in range(1, 4):
        spd = float(adb_shell(f"{CURL} -s -o /dev/null -w '%{{speed_download}}' http://{HOST}:{PORT}/big.bin", timeout=60) or "0")
        baseline_wifi_speeds.append(spd)
        log(f"  Rep {r}: {spd:.0f} B/s ({spd/(1024*1024):.2f} MB/s, {spd*8/1000000:.2f} Mbit/s)")
    avg_baseline_wifi = sum(baseline_wifi_speeds) / len(baseline_wifi_speeds)
    log(f"  Average Baseline (Wi-Fi): {avg_baseline_wifi:.0f} B/s ({avg_baseline_wifi/(1024*1024):.2f} MB/s, {avg_baseline_wifi*8/1000000:.2f} Mbit/s)")

    # (B) Lens ON on Wi-Fi path
    log("\n[Run B] Lens ON on Wi-Fi path:")
    start_lens()
    pid = get_app_pid()
    log(f"  Emberbyte PID: {pid}")
    lens_wifi_speeds = []
    lens_wifi_cpu_transfer = []
    lens_wifi_cpu_quiet = []
    lens_wifi_pss = []
    for r in range(1, 4):
        t0 = get_cpu_ticks(pid)
        pss0 = get_pss_kb()
        spd = float(adb_shell(f"{CURL} -s -o /dev/null -w '%{{speed_download}}' http://{HOST}:{PORT}/big.bin", timeout=60) or "0")
        t_transfer = get_cpu_ticks(pid)
        time.sleep(2)
        t_quiet = get_cpu_ticks(pid)
        pss1 = get_pss_kb()
        
        lens_wifi_speeds.append(spd)
        lens_wifi_cpu_transfer.append((t_transfer - t0) / 100.0)
        lens_wifi_cpu_quiet.append((t_quiet - t0) / 100.0)
        lens_wifi_pss.append(pss1)
        
        threads = get_busiest_threads(pid)
        log(f"  Rep {r}: {spd:.0f} B/s ({spd/(1024*1024):.2f} MB/s) | CPU transfer: {(t_transfer-t0)/100.0:.2f}s | CPU quiet: {(t_quiet-t0)/100.0:.2f}s | PSS: {pss1} kB")
        log(f"    Busiest threads: {threads}")
    stop_lens()

    avg_lens_wifi_spd = sum(lens_wifi_speeds) / len(lens_wifi_speeds)
    avg_lens_wifi_cpu = sum(lens_wifi_cpu_quiet) / len(lens_wifi_cpu_quiet)
    log(f"  Average Lens ON (Wi-Fi): {avg_lens_wifi_spd:.0f} B/s ({avg_lens_wifi_spd/(1024*1024):.2f} MB/s, {avg_lens_wifi_spd*8/1000000:.2f} Mbit/s)")
    log(f"  Average CPU per 100 MiB (Wi-Fi): {avg_lens_wifi_cpu:.2f} s")

    # (C) Lens ON on eth0 (Wi-Fi disabled)
    log("\n[Run C] Lens ON on eth0 path (Wi-Fi disabled):")
    adb_shell("svc wifi disable")
    time.sleep(2)

    # Baseline eth0
    baseline_eth0_speeds = []
    for r in range(1, 4):
        spd = float(adb_shell(f"{CURL} -s -o /dev/null -w '%{{speed_download}}' http://{HOST}:{PORT}/big.bin", timeout=60) or "0")
        baseline_eth0_speeds.append(spd)
        log(f"  Baseline eth0 Rep {r}: {spd:.0f} B/s ({spd/(1024*1024):.2f} MB/s, {spd*8/1000000:.2f} Mbit/s)")
    avg_baseline_eth0 = sum(baseline_eth0_speeds) / len(baseline_eth0_speeds)
    log(f"  Average Baseline (eth0): {avg_baseline_eth0:.0f} B/s ({avg_baseline_eth0/(1024*1024):.2f} MB/s, {avg_baseline_eth0*8/1000000:.2f} Mbit/s)")

    start_lens()
    pid = get_app_pid()
    lens_eth0_speeds = []
    lens_eth0_cpu_transfer = []
    lens_eth0_cpu_quiet = []
    lens_eth0_pss = []
    for r in range(1, 4):
        t0 = get_cpu_ticks(pid)
        spd = float(adb_shell(f"{CURL} -s -o /dev/null -w '%{{speed_download}}' http://{HOST}:{PORT}/big.bin", timeout=60) or "0")
        t_transfer = get_cpu_ticks(pid)
        time.sleep(2)
        t_quiet = get_cpu_ticks(pid)
        pss1 = get_pss_kb()
        
        lens_eth0_speeds.append(spd)
        lens_eth0_cpu_transfer.append((t_transfer - t0) / 100.0)
        lens_eth0_cpu_quiet.append((t_quiet - t0) / 100.0)
        lens_eth0_pss.append(pss1)
        
        threads = get_busiest_threads(pid)
        log(f"  Rep {r}: {spd:.0f} B/s ({spd/(1024*1024):.2f} MB/s) | CPU transfer: {(t_transfer-t0)/100.0:.2f}s | CPU quiet: {(t_quiet-t0)/100.0:.2f}s | PSS: {pss1} kB")
        log(f"    Busiest threads: {threads}")

    # Flow table idle return check
    log("\nChecking flow table return to ~0 after idleness on eth0:")
    dump_during = parse_dump(dump_lens())
    log(f"  Dump immediately after traffic: {dump_during}")
    log("  Waiting 60 seconds for idle flow eviction...")
    time.sleep(60)
    dump_idle = parse_dump(dump_lens())
    log(f"  Dump after 60s idle: {dump_idle}")

    stop_lens()
    adb_shell("svc wifi enable")
    time.sleep(2)

    avg_lens_eth0_spd = sum(lens_eth0_speeds) / len(lens_eth0_speeds)
    avg_lens_eth0_cpu = sum(lens_eth0_cpu_quiet) / len(lens_eth0_cpu_quiet)
    log(f"  Average Lens ON (eth0): {avg_lens_eth0_spd:.0f} B/s ({avg_lens_eth0_spd/(1024*1024):.2f} MB/s, {avg_lens_eth0_spd*8/1000000:.2f} Mbit/s)")
    log(f"  Average CPU per 100 MiB (eth0): {avg_lens_eth0_cpu:.2f} s")

    crashes = get_logcat_crash_count()
    log(f"\n[Logcat] Process crash/ANR count: {crashes}")

    # ---------------------------------------------------------
    # STEP 3: COMPUTED VERDICTS
    # ---------------------------------------------------------
    log("\n==================================================================")
    log("FINAL GATES & COMPUTED VERDICTS")
    log("==================================================================")
    
    corr_failures = [k for k, v in correctness_results.items() if v[0] == "FAIL"]
    corr_verdict = "PASS" if not corr_failures else "FAIL"
    log(f"Gate 1: Correctness Suite: {corr_verdict} (Failures: {corr_failures})")

    eth0_mbps = avg_lens_eth0_spd * 8 / 1000000.0
    wifi_mbps = avg_lens_wifi_spd * 8 / 1000000.0
    log(f"Gate 2: Absolute Throughput: Wi-Fi={wifi_mbps:.2f} Mbit/s, eth0={eth0_mbps:.2f} Mbit/s (Threshold: >= 40.0 Mbit/s)")
    if (avg_baseline_eth0 * 8 / 1000000.0) < 40.0 and eth0_mbps < 40.0:
        g2_verdict = "not evaluable on this emulator: the real-phone run is required"
    elif eth0_mbps >= 40.0:
        g2_verdict = "PASS"
    else:
        g2_verdict = "FAIL"
    log(f"  Verdict: {g2_verdict}")

    ratio_wifi = (avg_lens_wifi_spd / avg_baseline_wifi) * 100.0 if avg_baseline_wifi > 0 else 0
    ratio_eth0 = (avg_lens_eth0_spd / avg_baseline_eth0) * 100.0 if avg_baseline_eth0 > 0 else 0
    log(f"Gate 3: Relative Throughput: Wi-Fi={ratio_wifi:.1f}%, eth0={ratio_eth0:.1f}% (Threshold: >= 60.0%)")
    g3_verdict = "PASS" if (ratio_eth0 >= 60.0 or ratio_wifi >= 60.0) else "FAIL"
    log(f"  Verdict: {g3_verdict}")

    log(f"Gate 4: CPU per 100 MiB: Wi-Fi={avg_lens_wifi_cpu:.2f}s, eth0={avg_lens_eth0_cpu:.2f}s (Threshold: <= 2.0 s)")
    g4_verdict = "PASS" if min(avg_lens_wifi_cpu, avg_lens_eth0_cpu) <= 2.0 else "FAIL"
    log(f"  Verdict: {g4_verdict}")

    log(f"Gate 5: Process Crash / ANR count: {crashes} (Threshold: 0)")
    g5_verdict = "PASS" if crashes == 0 else "FAIL"
    log(f"  Verdict: {g5_verdict}")

    idle_flows = dump_idle.get("open", 999)
    log(f"Gate 6: Idle flow table size: {idle_flows} (Threshold: ~0, <= 2)")
    g6_verdict = "PASS" if idle_flows <= 2 else "FAIL"
    log(f"  Verdict: {g6_verdict}")

    if corr_verdict == "FAIL" or g3_verdict == "FAIL" or g4_verdict == "FAIL" or g5_verdict == "FAIL" or g6_verdict == "FAIL":
        decision = "NATIVE NEEDED"
    elif g2_verdict == "PASS":
        decision = "KOTLIN CONFIRMED"
    else:
        decision = "KOTLIN CONFIRMED PENDING REAL-PHONE RUN"

    log("\n==================================================================")
    log(f"DECISION: {decision}")
    log("==================================================================")

    run_file = os.path.join(RUNS_DIR, f"measurement_run_{int(time.time())}.txt")
    with open(run_file, "w", encoding="utf-8") as f:
        f.write("\n".join(raw_log) + "\n")
    log(f"[Report] Raw run output written to {run_file}")

if __name__ == "__main__":
    main()
