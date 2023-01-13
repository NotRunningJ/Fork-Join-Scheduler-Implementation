package lvc.csc;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;


public class ActiveObject {
    private ConcurrentLinkedDeque<AOTask> jobs; 
    private Thread workerThread;
    private boolean shouldTerminate;
    private Scheduler scheduler;

    // We can handle two types of tasks: Tasks that return a result
    // (a Supplier from the java standard library) or one that returns 
    // nothing (a Runnable)

    //TODO made AOTask its own class so scheduler can access it on stealing


    /// initialize an empty queue and our background thread.
    public ActiveObject(Scheduler s) {
        this.scheduler = s;
        jobs = new ConcurrentLinkedDeque<>(); 
        workerThread = new Thread(this::worker); 
        shouldTerminate = false;
    }

    public void start() {
        workerThread.start();
    }

    /**
     * enqueue a task. This can execute any code
     */
   public Future<Void> enqueue(Runnable r) {
        // place r in the queue. Notify the background thread.
        synchronized (this) {
            Future<Void> future = new Future<>(this);
            jobs.addFirst(new AOTask<Void>(r, future));
            notifyAll();
            return future;
        }
    }

    public <R> Future<R> enqueue(Supplier<R> s) {
        synchronized (this) {
            Future<R> future = new Future<>(this);
            jobs.addFirst(new AOTask<R>(s, future)); 
            notifyAll();
            return future;
        }
    }

    // enqueue an already existing task onto this ao's deque
    public void enqueueStolenTask(AOTask t) {
        synchronized (this) {
            jobs.addFirst(t);
            notifyAll();
        }
    }


    /**
     * tell the worker thread to gracefully terminate. We have a choice to
     * make:
     *  + abort abruptly, killing whatever's in flight
     *  + finish the current job, then killing the thread, even if
     *  + more jobs are queued
     *  + finish all jobs on the queue at this moment, then terminate.
     *
     * We can play with all three.
     *
     * This version terminates the thread without processing the remaining job
     * (though if there is a job in process when terminate is called, it will
     * complete).
     */
    public void terminate() {
        synchronized (this) {
            shouldTerminate = true;
            notifyAll();
        }
    }

    // let someone wake us up because there may be work to steal
    public void wakeUp() {
        synchronized (this) {
            notifyAll();
        }
    }

    /**
     * Run a loop to process our queued jobs until someone terminates us.
     */
    public void worker() {
        // run a loop to process jobs on the queue. When the queue is empty,
        // sleep. When the queue has contents, pop them off and run.
        while (true) {
            AOTask r = null;
            synchronized (this) {
                while (jobs.isEmpty() && !shouldTerminate) {
                    try {
                        int attempts = 0;
                        boolean stolen = false;
                        // fine tune the sleeping parameters
                        int maxAttempts = 15;
                        double sleepyTime = 1.2;
                        int baseSleep = 3 + this.scheduler.random.nextInt(5); // randomness
                        while(!stolen && attempts < maxAttempts) {  
                            attempts++;
                            this.wait((long) (baseSleep + Math.pow(sleepyTime, attempts)));
                            stolen = scheduler.steal(this);
                        }
                        if (!stolen && !shouldTerminate) {
                            System.out.println(Thread.currentThread() + " is sleeping");
                            this.wait();
                            System.out.println(Thread.currentThread() + " waking up");
                        }
                    } catch (InterruptedException e) {
                    }
                }
                if (shouldTerminate) {
                    System.out.println(Thread.currentThread() + " is dying");
                    return;
                }
                r = jobs.pollFirst();

            }
            if (r != null) {
                r.runAndComplete();
            }
        }
    }

    // called from the scheduler to give a task to another worker
    // so under a technicality, not stealing.
    public AOTask steal() {
        AOTask t = jobs.pollLast();
        return t;
    }

    // similar to worker(). This is designed to be called from
    // future.get() -- we keep executing tasks on the queue until the
    // future is completed.
    public void workUntilCompleted(Future future) {
        while (!future.isComplete()) {
            AOTask r = null;
            synchronized (this) {
                // don't sleep, we crank on all cylinders here because the work is out
                // there somewhere..we try and steal until we have work to do
                while (jobs.isEmpty() && !shouldTerminate && !future.isComplete()) {
                    boolean stolen = false;
                    stolen = scheduler.steal(this);
                }
                if (shouldTerminate || future.isComplete()) {
                    return;
                }
                r = jobs.pollFirst();
                
            }
            if (r != null) {
                r.runAndComplete();
            }
        }
	}


    Thread currentThread() {
        if (workerThread != null && workerThread.isAlive()) {
            return workerThread;
        } else {
            return null;
        }
    }

    Scheduler getScheduler() {
        return scheduler;
    }
}
