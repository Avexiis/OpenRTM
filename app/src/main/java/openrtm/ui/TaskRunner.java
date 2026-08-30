package openrtm.ui;

@FunctionalInterface
public interface TaskRunner {
    void run(String label, ThrowingRunnable action);

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
