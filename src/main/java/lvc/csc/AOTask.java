package lvc.csc;

import java.util.function.Supplier;

public class AOTask<R> {
    private Runnable r;
    private Supplier<R> s;
    private Future<R> future;

    AOTask(Supplier<R> s, Future<R> future) {
        this.r = null;
        this.s = s;
        this.future = future;
    }

    AOTask(Runnable r, Future future) {
        this.r = r;
        this.s = null;
        this.future = future;
    }

    public void runAndComplete() {
        // If this is a runnable...
        if (r != null && s == null) {
            r.run();
            if (future != null) {
                future.complete(null);
            }
        }
        // if it's a Supplier...
        else if (r == null && s != null) {
            R result = s.get();
            if (future != null) {
                future.complete(result);
            }
        }
    }

    // so a stolen task's future can change its ao
    public Future getFuture() {
        return this.future;
    }
}
