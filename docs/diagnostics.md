# Diagnostic guide

Run a scenario using `.vscode/launch.json`, then use a second PowerShell terminal in the repository root. Replace `<PID>` with the process ID printed by the application. The examples below are illustrative, not benchmark results.

## 1. CPU and sleeping threads

```powershell
New-Item -ItemType Directory -Force diagnostics | Out-Null
jcmd <PID> Thread.print > diagnostics/threads.txt
```

Compare each worker's `cpu` and `elapsed` values. CPU time near elapsed time indicates roughly one logical CPU's worth of work over that interval, not 100% of the whole machine. `RUNNABLE` alone does not prove high CPU use.

The CPU worker repeatedly computes square roots. The sleep variant performs batches of calculations separated by 100 ms sleeps. It should use less CPU but also does less work; this does not establish that sleeping makes the calculation more efficient.

After the JFR launch configuration finishes:

```powershell
jfr summary cpu.jfr
jfr print --events "jdk.ExecutionSample,jdk.NativeMethodSample" --stack-depth 20 cpu.jfr
jfr print --events "jdk.ThreadSleep" cpu-sleep.jfr
```

Inspect the sampled thread as well as the stack: an event from a JFR helper thread is not evidence about the worker. Sample counts are not method invocation counts. A sample attributed to the loop condition does not prove `System.nanoTime()` dominates CPU cost.

For `ThreadSleep`, `time` is the requested sleep and `duration` is the measured duration; neither is CPU consumption.

## 2. Allocation pressure and retained arrays

Use the allocation and retained-array launch configurations. Read `gc-allocation.log` and `gc-heavy-allocation.log`.

```text
Pause Young ... 57M->20M(64M) 0.203ms
```

This illustrative line means approximately 57 MiB occupied before collection, 20 MiB after, 64 MiB current heap capacity, and a 0.203 ms pause. The occupancy values describe the whole heap even for a Young GC. `0M` does not imply zero objects, and occupancy after a Young GC is not an exact measurement of all live objects.

The transient scenario keeps only the latest array in a static field. The retention scenario also keeps 256 arrays of 64 KiB: 16 MiB of payload, excluding headers and collection overhead. Stable retention is not automatically a memory leak.

```powershell
jfr print --events "jdk.ObjectAllocationSample" --stack-depth 20 allocation.jfr
```

Find `byte[]` samples on `allocation-worker`. `weight` represents sampled allocation volume; it is not the size of one array and does not measure retained memory.

## 3. Class histogram and heap dump

Run these while the retention scenario is still active:

```powershell
New-Item -ItemType Directory -Force diagnostics | Out-Null
jcmd <PID> GC.class_histogram > diagnostics/histogram.txt
$dumpPath = Join-Path $PWD.Path "diagnostics/retention.hprof"
jcmd <PID> GC.heap_dump "$dumpPath"
```

These inspections can pause or perturb the process. The default heap dump requests a Full GC. Use a new filename for another dump rather than overwriting a previous capture unintentionally.

In the histogram, `[B` means `byte[]`. Instance count is the number of array objects; total bytes include their own headers and alignment. Other code in the process also creates byte arrays.

Open the heap dump in a viewer and investigate:

```text
RunnableFactory static field
  -> retainedArrays (ArrayList)
     -> internal Object[]
        -> byte[] elements
```

The histogram cannot identify the owner of a particular array. Use reference inspection or a path to GC Roots to establish that relationship; confirm field names where the viewer provides them.

- **Shallow size:** space occupied by the object itself.
- **Retained size:** space that could become collectible if that object were no longer reachable, including objects whose reachability depends on it.
- **Dominator:** an object through which every path from GC Roots to another object passes.

Do not add a parent's retained size to its children's retained sizes: they overlap. A viewer's synthetic `SuperRoot` is not a Java object created by the application.

## 4. Monitor contention

The holder starts the waiter after entering `synchronized (lock)`, then sleeps while owning the monitor. `sleep()` does not release that monitor.

```powershell
jcmd <PID> Thread.print > diagnostics/contention-threads.txt
```

Expected evidence:

| Thread | State | Stack evidence |
| --- | --- | --- |
| `lock-holder` | `TIMED_WAITING` | Sleeping, with `locked <monitor-id>`. |
| `lock-waiter` | `BLOCKED` | `waiting to lock <same-monitor-id>`. |

After the application finishes normally:

```powershell
jfr print --events "jdk.JavaMonitorEnter" --stack-depth 20 contention.jfr
```

Look for `eventThread = lock-waiter`, `previousOwner = lock-holder`, and a duration near 30 seconds. This measures blocked monitor entry, not CPU time or time executing the synchronized body. The event completes when entry succeeds, so keep recording through the end of the wait.

Correction exercise: move the sleep outside the synchronized block if the protected operation permits it. Keep only the work that requires protection inside the block.

Do not call `join()` inside a monitor needed by the worker being joined: the caller can wait for a worker that cannot finish until the caller releases that monitor.

## 5. Intentional deadlock

Run `dead-lock`, wait a few seconds, and capture:

```powershell
jcmd <PID> Thread.print > diagnostics/deadlock-threads.txt
```

The intended cycle is:

| Thread | Owns | Needs |
| --- | --- | --- |
| `deadlock-a` | `lockA` | `lockB` |
| `deadlock-b` | `lockB` | `lockA` |

Both should be `BLOCKED`. Match their `locked` and `waiting to lock` object identifiers, and look for `Found one Java-level deadlock`. The main thread may be waiting in `join()` as a consequence without being part of this two-monitor cycle.

The dump diagnoses the deadlock; it does not resolve it. Stop this process manually after collecting the evidence. The sleeps only encourage the problematic interleaving: if no cycle occurs, rerun the scenario.

Correction exercise: make both workers acquire A before B. A worker waiting for A then holds no B, allowing the owner of A to finish. Verify that both workers and their `join()` calls complete. This prevents the reverse-order cycle, although contention can remain.

## References

- [JDK 23 jcmd command](https://docs.oracle.com/en/java/javase/23/docs/specs/man/jcmd.html)
- [JDK 23 jfr command](https://docs.oracle.com/en/java/javase/23/docs/specs/man/jfr.html)
- [Java Thread API](https://docs.oracle.com/en/java/javase/23/docs/api/java.base/java/lang/Thread.html)
- [Java Language Specification: threads and locks](https://docs.oracle.com/javase/specs/jls/se23/html/jls-17.html)
