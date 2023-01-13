package lvc.csc;

import java.util.concurrent.atomic.AtomicInteger;


// We're going to try and replace locking with atomics. The plan is typical
// of such designs: We think about the states that the future object can be in,
// how the object can transition between those states, and what can be done to
// a future in the various states. 
//
// A future is born in state NEW. It can transition from state NEW to 
// state COMPLETE. It then stays in state COMPLETE for ever.
// In state NEW: you can call get() or complete(). A call to complete() will
// set the result and transition to state COMPLETE. A call to get() will block
// until the state becomes COMPLETE.
// in state COMPLETE, it is illegal to call complete(). A call to get() will 
// immediately return the result.
public class Future<R> {
    private static int rootId = 0;
    private static final boolean DEBUG = false;

    // Our future can be in one of three states. NEW (future created,
    // computation not yet done), COMPLETED (computation done, result set),
    // and COMPLETING (transitioning from NEW to COMPLETED)
    private static final int NEW = 0;
    private static final int COMPLETING = 1;
    private static final int COMPLETED = 2;

    // This id is only used for debugging purposes, so we can tell which future
    // is doing what.
    private int id;

    // an atomic that we store our current state in.
    private AtomicInteger state;

    // the result this future is managing.
    private R result;

    // The worker we are attached to
    private ActiveObject ao;
    private Scheduler scheduler;

    public Future(ActiveObject ao) {
        this.id = rootId++;
        this.state = new AtomicInteger(NEW);
        this.result = null;
        this.ao = ao;
        this.scheduler = ao.getScheduler();
        diag("Constructor", "");
    }

    // a stolen task needs a new AO on its future
    public void changeAO(ActiveObject ao) {
        this.ao = ao;
        this.scheduler = ao.getScheduler();
    }

    public R join() {
        diag("get", "");
        // the idea: if we haven't reached state COMPLETED yet, we block.
        // Once the state is COMPLETED, we drop through to the return.
        // If we need to block, though, we have to make sure that, if a task 
        // called get(), the worker thread that was running the task isn't
        // blocked.
        if (state.get() < COMPLETED) {
            diag("get", "future not complete. Waiting");
            if (scheduler.isAWorker(Thread.currentThread())) {
                diag("get", "waiting in a scheduler thread");
                // we were called on the AO that's handling this task.
                scheduler.workUntilCompleted(this);
            } else {
                diag("get2", "waiting in a different thread");
                // another thread will complete the task. Wait for it.
                synchronized (this) {
                    while (state.get() < COMPLETED) {
                        try {
                            this.wait();
                        } 
                        catch (InterruptedException e) {
                        }
                    }
                }
            }


        }
        diag("get", "future completed. Returning result.");
        return result;
    }

    public void complete(R b) {
        diag("complete", "called with: " + b);
        // the new idea: First move to COMPLETING so that we have 
        // space to set the result
        if (state.compareAndSet(NEW, COMPLETING)) {
            diag("complete", "in COMPLETING. Setting result");
            this.result = b;
            // Now move to COMPLETED, so that get() will progress.
            if (state.compareAndSet(COMPLETING, COMPLETED)) {
                diag("complete", "Result COMPLETED. Notifying any waiters");
                // we need to wake up anyone waiting for this to happen.
                synchronized (this) {
                    this.notify();
                }
            }
            else {
                diag("complete", "state = " + state.get());
                throw new IllegalStateException("Transitioning to COMPLETED from incorrect state.");
            }
        }
        else {
            diag("complete", "state = " + state.get());
            throw new IllegalStateException("Called complete on future in non-NEW state.");
        }
    }

    public boolean isComplete() {
        return state.get() == COMPLETED;
    }

    // diagnostics -- a cheezy logging tool. 
    // Change DEBUG to true to enable output
    private void diag(String mName, String msg) {
        if (DEBUG) {
            System.out.println(id + ": future::" + mName +
                                    ", state=" + state.get());

            if (msg != null && !msg.equals("")) {
                System.out.println("\t-->" + msg);
            }
        }
    }
}

