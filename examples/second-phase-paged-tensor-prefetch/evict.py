#!/usr/bin/env python3
# Evicts proton's mapped paged-attribute pages (swapfile mappings) with process_madvise(MADV_PAGEOUT).
# posix_fadvise(DONTNEED) / vmtouch -e / drop_caches can't do this: they skip pages mapped by a process.
# Needs CAP_SYS_NICE + ptrace access to proton (run via `docker exec --privileged -u root`).
import ctypes, os, subprocess, sys

SYS_pidfd_open, SYS_process_madvise, MADV_PAGEOUT = 434, 440, 21
libc = ctypes.CDLL(None, use_errno=True)

class iovec(ctypes.Structure):
    _fields_ = [("base", ctypes.c_void_p), ("len", ctypes.c_size_t)]

def proton_pid():
    return int(subprocess.check_output(["pgrep", "-f", "sbin/vespa-proton-bin "]).split()[0])

def swap_ranges(pid):
    ranges, files = [], set()
    with open(f"/proc/{pid}/maps") as f:
        for line in f:
            parts = line.split()
            if len(parts) >= 6 and parts[5].endswith("/swapfile"):
                lo, hi = (int(x, 16) for x in parts[0].split("-"))
                ranges.append((lo, hi - lo))
                files.add(parts[5])
    return ranges, sorted(files)

def evict(pid):
    ranges, files = swap_ranges(pid)
    fd = libc.syscall(SYS_pidfd_open, pid, 0)
    if fd < 0:
        raise OSError(ctypes.get_errno(), "pidfd_open")
    total = 0
    try:
        for i in range(0, len(ranges), 1024):
            batch = ranges[i:i + 1024]
            iov = (iovec * len(batch))(*[iovec(lo, size) for lo, size in batch])
            res = libc.syscall(SYS_process_madvise, fd, iov, len(batch), MADV_PAGEOUT, 0)
            if res < 0:
                raise OSError(ctypes.get_errno(), "process_madvise(MADV_PAGEOUT)")
            total += res
    finally:
        os.close(fd)
    # Pages no longer mapped (or skipped by pageout) can now be dropped from the page cache.
    for path in files:
        ffd = os.open(path, os.O_RDONLY)
        try:
            os.posix_fadvise(ffd, 0, 0, os.POSIX_FADV_DONTNEED)
        finally:
            os.close(ffd)
    return total, files

if __name__ == "__main__":
    total, files = evict(proton_pid())
    print(f"advised {total >> 20} MiB in {len(files)} swapfile(s)")
    for f in files:
        print(subprocess.run(["fincore", "-b", f], capture_output=True, text=True).stdout.strip())
