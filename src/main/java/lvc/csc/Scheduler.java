package lvc.csc;

import java.util.Random;
import java.util.function.Supplier;

public class Scheduler {
    private ActiveObject[] workers; //NOT public
    private boolean initialized; // schedule can be called before all the workers are up and running

    public Random random = new Random(); // random to be used by workers for stealing

    //steal in here??


    public Scheduler(int num) {
        workers = new ActiveObject[num];
        for (int i=0; i<num; ++i) {
            workers[i] = new ActiveObject(this);
            workers[i].start();
        }
        initialized = true;

    }

    // we can schedule a runnable that takes no arguments and
    // returns nothing. The future here just blocks until the task
    // is done.
    public Future<Void> schedule(Runnable r) {
        int idx = random.nextInt(workers.length); // randomly chose the worker
        var fut = workers[idx].enqueue(r);
        while(!initialized) {
            // do nothing but wait for all workers to be initialized
        }
        for(int i = 0; i < workers.length; i++) {
            if(i != idx) {
                workers[i].wakeUp();
            }
        }
        return fut;
    }

    // forking a task to be tossed on the same ao's deque for a Runnable
    public Future<Void> fork(Runnable r) {
        var th = Thread.currentThread();
        // find the worker who called fork
        if(isAWorker(th)) { // find it
            for(int i = 0; i < workers.length; i++) {
                if(th == workers[i].currentThread()) { // we found the worker
                    return workers[i].enqueue(r);
                }
            }
            // all of this should be unreachable..
            return schedule(r);
        } else {
            return schedule(r);
        }
    }

    // we can also schedule a function that takes no parameters
    // and returns something. 
    public <R> Future<R> schedule(Supplier<R> r) {
        int idx = random.nextInt(workers.length); // randomly chose the worker
        var fut = workers[idx].enqueue(r);
        // wake up any workers who fell asleep so they can steal work
        while(!initialized) {
            // do nothing but wait for all workers to be initialized
        }
        for(int i = 0; i < workers.length; i++) {
            if(i != idx) {
                workers[i].wakeUp();
            }
        }
        return fut;
    }

    // forking a task to be tossed on the same ao's deque for a Supplier
    public <R> Future<R> fork(Supplier<R> r) {
        var th = Thread.currentThread();
        // find the worker who called fork
        if(isAWorker(th)) { // find it
            for(int i = 0; i < workers.length; i++) {
                if(th == workers[i].currentThread()) { // we found the worker
                    return workers[i].enqueue(r);
                }
            }
            // all of this should be unreachable.
            return schedule(r);
        } else {
            return schedule(r);
        }
    }

    public void terminate() {
        for (ActiveObject ao : workers)
            ao.terminate();
    }

    boolean isAWorker(Thread th) {
        for (var w : workers) {
            if (th == w.currentThread()) {
                return true;
            }
        }
        return false;
    }

    void workUntilCompleted(Future fut) {
        // find the thread we are running on. It must be
        // an active object thread.
        var th = Thread.currentThread();
        for (var w : workers) {
            if (th == w.currentThread()) {
                w.workUntilCompleted(fut);
                return;
            }
        }
    }

    // find work for the worker to steal
    public boolean steal(ActiveObject worker) {
        int attempts = 0;
        // try and steal 2x the number of workers
        while(!initialized) {
            // do nothing but wait for all workers to be initialized
        }
        while(attempts < workers.length*2) {
            attempts++;
            int idx = random.nextInt(workers.length);
            AOTask t = workers[idx].steal();
            if(t != null) { // there is work to steal
                // change the future's ao 
                var future = t.getFuture();
                future.changeAO(worker);
                worker.enqueueStolenTask(t);
                return true;
            }
        }
        return false;
    }
}
