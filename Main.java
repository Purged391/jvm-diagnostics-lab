public class Main {
    public static void main(String[] args) throws InterruptedException {
        if (args.length > 0 && args[0].equalsIgnoreCase("dead-lock")) {
            Thread workerA = new Thread(RunnableFactory.deadLockA(), "deadlock-a");
            Thread workerB = new Thread(RunnableFactory.deadLockB(), "deadlock-b");
            System.out.println("PID: " + ProcessHandle.current().pid());

            workerA.start();
            workerB.start();

            workerA.join();
            workerB.join();
            return;
        }

        Runnable task = RunnableFactory.create(args);
        String workerName = switch (args[0].toLowerCase()) {
            case "cpu" -> "cpu-worker";
            case "cpu-sleep" -> "cpu-worker-sleep";
            case "allocation-worker" -> "allocation-worker";
            case "heavy-allocation-worker" -> "heavy-allocation-worker";
            case "lock-holder" -> "lock-holder";
            default -> args[0];
        };
        Thread worker = new Thread(task, workerName);
        System.out.println("PID: " + ProcessHandle.current().pid());
        worker.start();
        worker.join();

        System.out.println("Worker finished.");
    }
}
