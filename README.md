# JVM Diagnostics Lab

A small, dependency-free Java learning lab for investigating CPU usage, allocation pressure, retained objects, monitor contention, and deadlocks. Each scenario creates a controlled workload that can be inspected with JDK diagnostic tools.

The goal is to connect a symptom to evidence and an explanation, rather than treat a tool's output as a diagnosis on its own. This is an educational lab, not a benchmark or a production monitoring agent.

## Requirements

- JDK 21 or later; developed and exercised with Oracle HotSpot JDK 23.0.2 on Windows.
- `java`, `javac`, `jcmd`, and `jfr` available on your terminal's PATH.
- VS Code with the Extension Pack for Java for the launch configurations.
- A heap viewer, such as VisualVM, Eclipse MAT, or HeapLens, for exploring `.hprof` files.

## Run in VS Code

Open this repository as a folder, let the Java extension load the project, and select a configuration under **Run and Debug**. Use **Run Without Debugging** (`Ctrl+F5`) for observations without debugger suspension.

The shared [launch configurations](.vscode/launch.json) use the workspace as their working directory and contain no absolute machine paths.

| Configuration | Scenario argument | Behavior |
| --- | --- | --- |
| CPU workload (JFR) | `cpu` | Runs calculations for approximately 60 seconds; saves `cpu.jfr` on exit. |
| CPU with sleep (JFR) | `cpu-sleep` | Alternates calculations and 100 ms sleeps for approximately 60 seconds; saves `cpu-sleep.jfr`. |
| Allocation worker (GC) | `allocation-worker` | Allocates temporary 64 KiB byte arrays for 30 seconds with a 64 MiB heap; records GC logs and 20 seconds of JFR. |
| Retained arrays + allocation (GC) | `heavy-allocation-worker` | Retains 256 arrays (16 MiB of payload), then allocates temporary arrays for 30 seconds with a 64 MiB heap. |
| Lock holder (JFR contention) | `lock-holder` | Holds a monitor while sleeping for 30 seconds; another thread blocks; saves `contention.jfr` on exit. |
| Intentional deadlock (stop manually) | `dead-lock` | Acquires two monitors in opposite orders; intended to remain blocked until stopped. |
| Main (choose scenario) | Interactive selection | Runs a chosen scenario without the special heap, logging, or recording options above. |

Each run prints its PID. Use that PID from a second terminal for `jcmd` commands. For the intentional deadlock, capture the dump and then stop the process with `Ctrl+C` or VS Code's Stop action. The sleeps encourage the deadlock but do not guarantee scheduling order; rerun if both workers finish.

Allocation scenarios deliberately create substantial GC activity and can produce large recordings. All generated captures stay local and are ignored by Git.

## Command-line alternative

Run from the repository root in PowerShell:

```powershell
New-Item -ItemType Directory -Force .build | Out-Null
javac -d .build Main.java RunnableFactory.java
java -cp .build Main cpu
```

For allocation experiments, specify heap limits before the main class:

```powershell
java -Xms64m -Xmx64m "-Xlog:gc:file=gc-allocation.log:uptime,level,tags" -cp .build Main allocation-worker
```

## Investigation workflow

See [the diagnostic guide](docs/diagnostics.md) for commands, expected evidence, limitations, and the deadlock correction exercise.

| Question | Evidence |
| --- | --- |
| Which thread uses CPU? | Thread CPU time versus elapsed time, then JFR execution samples. |
| Where are objects allocated? | `jdk.ObjectAllocationSample` stacks. |
| How does collection change occupancy? | GC log before/after values and pause durations. |
| Which object types occupy the heap? | Class histogram. |
| Why does an object remain reachable? | Heap dump reference paths and dominator tree. |
| Who owns the monitor a thread needs? | Matching monitor identifiers in a thread dump. |
| How long did monitor contention last? | `jdk.JavaMonitorEnter`. |
| Is there a circular lock dependency? | Deadlock section and participating stacks in a thread dump. |

## Publication and privacy

The repository publishes source, portable launch configurations, and documentation. Raw `.jfr`, `.hprof`, GC logs, and local text captures are excluded. They can contain runtime configuration, paths, or data from the Java process. Heap dumps describe that process's heap, not the entire computer's memory.

Put new raw text captures in `diagnostics/`. Only publish reviewed, anonymized excerpts in `docs/`; do not commit full captures just to reproduce an analysis. Identifiers, timings, line numbers, and object counts vary between runs.

Before committing, review `git status --short`, then inspect the files staged for publication with `git diff --cached`. Ignore rules do not remove files already tracked or erase previous commits.

## Scope and limitations

- No external Java dependencies or build system are required.
- A low-CPU thread can still delay the application by holding a monitor.
- Allocation volume is not the same as live or retained memory.
- Sampling events are not a complete trace of every operation.
- The deadlock example intentionally preserves the faulty lock order for investigation; the guide explains the corrected order.
- The workload durations are approximate, and measurements are specific to the environment.

## License

[MIT](LICENSE).
