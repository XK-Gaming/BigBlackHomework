package network;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bộ thực thi luồng nền (thread pool) dùng chung cho các thao tác I/O ngắn từ UI.
 *
 * <p>Mục tiêu:
 * <ul>
 *   <li>Tránh tạo {@link Thread} mới mỗi lần bấm nút (giảm overhead).</li>
 *   <li>Giữ UI mượt: không chạy network/blocking I/O trên JavaFX Application Thread.</li>
 * </ul>
 */
public final class ClientNetworkExecutor {

    /**
     * Thread pool cố định (2 luồng) cho các task I/O client.
     * NOTE: Các luồng được đặt là daemon để tự dừng khi app thoát.
     */
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

    /**
     * Precondition: Không có.
     * Postcondition: Không có (ngăn khởi tạo đối tượng).
     * NOTE: Utility class.
     */
    private ClientNetworkExecutor() {}

    /**
     * Precondition: {@code task} khác {@code null} và không nên thực hiện thao tác cập nhật UI trực tiếp (vì chạy trên luồng nền).
     * Postcondition: {@code task} được đưa vào hàng đợi và sẽ được chạy bởi thread pool.
     * NOTE: Nếu cần cập nhật UI JavaFX sau khi chạy xong, hãy dùng {@code Platform.runLater}.
     * Method returns: nothing.
     * NOTE: {@link java.util.concurrent.RejectedExecutionException} có thể xảy ra nếu executor bị shutdown (hiện tại code không shutdown).
     */
    public static void execute(Runnable task) {
        INSTANCE.execute(task);
    }
}
