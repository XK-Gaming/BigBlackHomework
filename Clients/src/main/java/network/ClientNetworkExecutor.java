package network;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Luồng nền dùng chung cho thao tác gửi lệnh / I/O ngắn từ UI, tránh tạo {@link Thread} mới mỗi lần bấm nút.
 */
public final class ClientNetworkExecutor {

    private static final ExecutorService INSTANCE = Executors.newFixedThreadPool(
            2,
            new ThreadFactory() {
                private final AtomicInteger seq = new AtomicInteger();

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "auction-client-io-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            });

    private ClientNetworkExecutor() {}

    public static void execute(Runnable task) {
        INSTANCE.execute(task);
    }
}
