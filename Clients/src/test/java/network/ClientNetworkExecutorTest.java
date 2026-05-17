package network;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ## JUnit: test executor network client co the chay task nen.
 */
class ClientNetworkExecutorTest {

    /**
     * ## Test bat dong bo: execute phai thuc thi task va dem CountDownLatch ve 0.
     */
    @Test
    void executeRunsTaskOnBackgroundExecutor() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        ClientNetworkExecutor.execute(latch::countDown);

        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }
}
