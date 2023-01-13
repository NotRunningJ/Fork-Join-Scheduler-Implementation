import lvc.csc.Future;
import lvc.csc.Scheduler;

public final class App {

    public static void simple_test() {
        Future<String> fut = new Future<>(null);
        Thread th = new Thread(() -> {
            System.out.println("worker thread sleeping");
            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException ignore) {

            }
            fut.complete("A message from the worker thread");
            System.out.println("worker thread saying bye");
        });

        th.start();
        System.out.println("main thread sleeping");
        try {
            Thread.sleep(2000);
        }
        catch (InterruptedException ignore) {
        }
         
        System.out.println("future says: " + fut.join());
    }

    public static void test_scheduler() {
        Scheduler s = new Scheduler(8);
        Future[] futs = new Future[36];

        for (int i = 0; i < futs.length; ++i) {
            final int idx = i;
            futs[i] = s.schedule(() -> {
                System.out.println("task " + idx + " is starting");
                try {
                    Thread.sleep(idx * 100);
                } catch (InterruptedException ignore) {
                }
                System.out.println("task " + idx + " is exiting");
            });
        }

        System.out.println("let's wait for the tasks");
        for (var f : futs) {
            f.join();
        }
        System.out.println("done joining tasks");
        s.terminate();
    }
    
    public static double dotp(double[] a, double[] b, int s, int e) {
        double sum = 0.0;
        for (int i = s; i < e; ++i) {
            double intensity = 1_000_000_000;
            while(intensity > 1) {
                intensity = Math.sqrt(intensity); // computational intensity
            }
            sum += a[i] * b[i];
        }
        return sum;
    }

    public static double dotpfj(Scheduler sch, double[] a, double[] b, int s, int e) {
        if (e - s < 1000) {
            return dotp(a, b, s, e);
        }

        int mid = s + (e - s) / 2;
        var f = sch.fork(() -> dotpfj(sch, a, b, s, mid));
        double r = dotpfj(sch, a, b, mid, e);
        double l = f.join();
        return r + l;
    }

    public static void test_scheduler2() {
        Scheduler s = new Scheduler(8);
        double[] a = new double[8_000_000];
        double[] b = new double[8_000_000];
        for (int i = 0; i < a.length; ++i) {
            a[i] = 1.0;
            b[i] = 2.0;
        }

        int start = 0;
        int end = a.length;


        var startTime = System.currentTimeMillis();
        var f = s.schedule(() -> dotpfj(s, a, b, start, end));

        double val = f.join();
        System.out.println(val);

        var endTime = System.currentTimeMillis();
        System.out.println("took " + (endTime-startTime) + "ms");


        // all the threads will go to sleep
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            System.out.println("no time to sleep");
        }


        // kick off another task -> all threads should wake up eager to work
        startTime = System.currentTimeMillis();
        var f2 = s.schedule(() -> dotpfj(s, a, b, start, end));

        double val2 = f2.join();
        System.out.println(val2);
        endTime = System.currentTimeMillis();
        System.out.println("took " + (endTime-startTime) + "ms");

        s.terminate();
    }

    public static void main(String[] args) {
        // test_scheduler();

        // var start = System.currentTimeMillis();
        test_scheduler2();
        // var end = System.currentTimeMillis();
        // System.out.println("took " + (end-start) + "ms");
        
    }
}
