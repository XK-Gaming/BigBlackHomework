package network;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientNetworkExecutorTest {

    // Test task mạng chạy trên executor nền.
    @Test
    void executeRunsTaskOnBackgroundExecutor() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        ClientNetworkExecutor.execute(latch::countDown);

        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }
}
