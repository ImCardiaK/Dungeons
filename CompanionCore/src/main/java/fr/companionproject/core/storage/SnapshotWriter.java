package fr.companionproject.core.storage;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Serial writer retaining at most one waiting snapshot in addition to the active write. */
public final class SnapshotWriter<T> implements AutoCloseable
{
    private final ExecutorService executor;
    private final Consumer<T> sink;
    private T pending;
    private CompletableFuture<Void> pendingResult;
    private CompletableFuture<Void> drained = CompletableFuture.completedFuture(null);
    private boolean running;
    private boolean closed;

    /** Creates a writer; the sink must operate only on independent snapshots. */
    public SnapshotWriter(final String name, final Consumer<T> sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
        executor = Executors.newSingleThreadExecutor(task -> new Thread(task, name));
    }

    /** Completes once this snapshot or a newer replacement has been written. */
    public synchronized CompletableFuture<Void> submit(final T snapshot) {
        if (closed)
            throw new IllegalStateException("Writer is closed");
        pending = Objects.requireNonNull(snapshot, "snapshot");
        if (pendingResult == null)
            pendingResult = new CompletableFuture<>();
        final CompletableFuture<Void> result = pendingResult;
        if (!running)
        {
            running = true;
            drained = new CompletableFuture<>();
            executor.execute(this::drain);
        }
        return result.copy();
    }

    private void drain() {
        while (true)
        {
            final T snapshot;
            final CompletableFuture<Void> result;
            synchronized (this)
            {
                if (pending == null)
                {
                    running = false;
                    drained.complete(null);
                    return;
                }
                snapshot = pending;
                result = pendingResult;
                pending = null;
                pendingResult = null;
            }
            try {
                sink.accept(snapshot);
                result.complete(null);
            } catch (final RuntimeException exception) {
                result.completeExceptionally(exception);
            }
        }
    }

    /** Rejects new submissions and waits for all accepted work. */
    @Override
    public void close() {
        final CompletableFuture<Void> completion;
        synchronized (this)
        {
            closed = true;
            completion = drained;
            executor.shutdown();
        }
        completion.join();
    }
}
