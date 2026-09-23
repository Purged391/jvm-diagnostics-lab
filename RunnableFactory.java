import java.util.ArrayList;
import java.util.List;

public final class RunnableFactory {

    // Keep the latest allocation observable while allowing earlier arrays to be collected.
    private static volatile byte[] latestAllocation;
    private static List<byte[]> retainedArrays;
    private static final Object lock = new Object();
    private static final Object lockA = new Object();
    private static final Object lockB = new Object();

    private RunnableFactory() {
    }

    public static Runnable create(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException(
                    "Usage: java Main <cpu|cpu-sleep|allocation-worker|heavy-allocation-worker|lock-holder|dead-lock>");
        }

        return switch (args[0].toLowerCase()) {
            case "cpu" -> cpuTask();
            case "cpu-sleep" -> cpuSleepTask();
            case "allocation-worker" -> allocationTask();
            case "heavy-allocation-worker" -> heavyAllocationTask();
            case "lock-holder" -> lockHolder();
            default -> throw new IllegalArgumentException(
                    "Unknown scenario: " + args[0]
                        + ". Use cpu, cpu-sleep, allocation-worker, heavy-allocation-worker, lock-holder or dead-lock.");
        };
    }

    private static Runnable allocationTask() {
        return () -> {
            System.out.println("Starting workload.");
            long start = System.nanoTime();
            long duration = 30_000_000_000L;
            long arrayCount = 1;
            while (System.nanoTime() - start < duration) {
                byte[] byteArray = new byte[64 * 1024];
                latestAllocation = byteArray;
                arrayCount += 1;
            }
            System.out.println("Iterations: " + (arrayCount - 1));
        };
    }

    // Opposite lock ordering intentionally makes a deadlock possible.
    // The sleep encourages interleaving; it does not guarantee scheduling order.
    public static Runnable deadLockA() {
        return () -> {
            synchronized (lockA) {
                System.out.println("deadlock-a acquired lockA");
                try {
                    Thread.sleep(2000);
                    synchronized (lockB) {
                        System.out.println("deadlock-a acquired lockB");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        };
    }
    public static Runnable deadLockB() {
        return () -> {
            synchronized (lockB) {
                System.out.println("deadlock-b acquired lockB");
                try {
                    Thread.sleep(2000);
                    synchronized (lockA) {
                        System.out.println("deadlock-b acquired lockA");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        };
    }

    private static Runnable lockHolder() {
        return () -> {
            synchronized (lock) {
                System.out.println("lock-holder acquired the monitor");
                try {
                    Thread worker = new Thread(lockWaiter(), "lock-waiter");
                    System.out.println("PID: " + ProcessHandle.current().pid());
                    // Start the contender only after this thread owns the monitor.
                    worker.start();
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        };
    }

    private static Runnable lockWaiter() {
        return () -> {
            System.out.println("lock-waiter is attempting to acquire the monitor");
            synchronized (lock) {
                System.out.println("lock-waiter acquired the monitor");
            }
        };
    }

    private static Runnable heavyAllocationTask() {
        return () -> {
            System.out.println("Starting workload.");
            // Retain 16 MiB of payload, then continue allocating temporary arrays.
            retainedArrays = new ArrayList<>();
            for (long i = 0; i < 256; i++) {
                retainedArrays.add(new byte[64 * 1024]);
            }
            long start = System.nanoTime();
            long duration = 30_000_000_000L;
            long arrayCount = 1;
            while (System.nanoTime() - start < duration) {
                byte[] byteArray = new byte[64 * 1024];
                latestAllocation = byteArray;
                arrayCount += 1;
            }
            System.out.println("Iterations: " + (arrayCount - 1));
        };
    }

    private static Runnable cpuTask() {
        return () -> {
            System.out.println("Starting workload.");
            long start = System.nanoTime();
            long duration = 60_000_000_000L;
            double acc = 0;
            long counter = 1;
            while (System.nanoTime() - start < duration) {
                acc += Math.sqrt(counter);
                counter += 1;
            }
            printResults(counter, acc);
        };
    }

    private static Runnable cpuSleepTask() {
        return () -> {
            System.out.println("Starting workload.");
            long start = System.nanoTime();
            long duration = 60_000_000_000L;
            double acc = 0;
            long counter = 1;
            while (System.nanoTime() - start < duration) {
                for (long localCounter = 0; localCounter < 100000; localCounter++) {
                    acc += Math.sqrt(counter);
                    counter += 1;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            printResults(counter, acc);
        };
    }

    private static void printResults(long counter, double acc) {
        System.out.println("Iterations: " + (counter - 1));
        System.out.println("Accumulator: " + acc);
    }
}