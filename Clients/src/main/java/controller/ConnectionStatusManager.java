package controller;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import network.AuctionClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
//hello
public class ConnectionStatusManager {
    private final Circle statusCircle;
    private final Label statusLabel;
    private Timeline timeline;
    private FadeTransition fadeTransition;

    private boolean isReconnecting = false;

    // CẬP NHẬT: Màu xanh nhẹ nhàng (Light Green), Đỏ chuẩn và Vàng cảnh báo kèm bóng đổ trắng
    private static final Color LIGHT_GREEN = Color.web("#2ECC71"); // Xanh lục tươi, nhẹ nhàng và sáng hơn
    private static final Color RED = Color.web("#E74C3C");         // Đỏ tươi rõ ràng
    private static final Color YELLOW = Color.web("#F1C40F");      // Vàng chanh sáng (Warning)

    // Khởi tạo hiệu ứng đổ bóng màu trắng (Glow effect)
    private final DropShadow whiteGlow = new DropShadow();

    public ConnectionStatusManager(Circle statusCircle, Label statusLabel) {
        this.statusCircle = statusCircle;
        this.statusLabel = statusLabel;

        // Cấu hình hiệu ứng đổ bóng trắng
        whiteGlow.setColor(Color.WHITE);
        whiteGlow.setRadius(8.0);      // Độ lan tỏa của bóng
        whiteGlow.setOffsetX(0.0);     // Không lệch trục X
        whiteGlow.setOffsetY(0.0);     // Không lệch trục Y để tạo hiệu ứng phát sáng đều xung quanh

        // Áp dụng bóng đổ cho cả vòng tròn và chữ
        this.statusCircle.setEffect(whiteGlow);
        if (this.statusLabel != null) {
            this.statusLabel.setEffect(whiteGlow);
        }

        setupBlinking();
    }

    private void setupBlinking() {
        fadeTransition = new FadeTransition(Duration.seconds(0.8), statusCircle);
        fadeTransition.setFromValue(1.0);
        fadeTransition.setToValue(0.4); // Tăng lên 0.4 để lúc nhấp nháy màu xanh nhẹ không bị mờ quá
        fadeTransition.setCycleCount(Animation.INDEFINITE);
        fadeTransition.setAutoReverse(true);
    }

    public void startMonitoring() {
        if (timeline != null) {
            timeline.stop();
        }

        timeline = new Timeline(new KeyFrame(Duration.seconds(3), event -> {
            Thread checkThread = new Thread(this::checkNetworkAndStatus);
            checkThread.setDaemon(true);
            checkThread.start();
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();

        Thread firstCheck = new Thread(this::checkNetworkAndStatus);
        firstCheck.setDaemon(true);
        firstCheck.start();
    }

    public void stopMonitoring() {
        if (timeline != null) {
            timeline.stop();
        }
        if (fadeTransition != null) {
            fadeTransition.stop();
        }
    }

    private void checkNetworkAndStatus() {
        boolean hasInternet = checkRealInternet();
        boolean isConnectedToServer = AuctionClient.getInstance().isConnected();

        Platform.runLater(() -> {
            if (!hasInternet) {
                // TRƯỜNG HỢP 1: MẤT INTERNET (MÀU ĐỎ)
                statusCircle.setFill(RED);
                if (statusLabel != null) {
                    statusLabel.setText("Disconnect Internet");
                    statusLabel.setTextFill(RED);
                }
                fadeTransition.setRate(2.5);
                if (fadeTransition.getStatus() != Animation.Status.RUNNING) fadeTransition.play();

                triggerReconnect();

            } else if (!isConnectedToServer) {
                // TRƯỜNG HỢP 2: DISCONNECT SERVER (MÀU VÀNG)
                statusCircle.setFill(YELLOW);
                if (statusLabel != null) {
                    statusLabel.setText("Disconnect Server");
                    statusLabel.setTextFill(YELLOW);
                }
                fadeTransition.setRate(1.5);
                if (fadeTransition.getStatus() != Animation.Status.RUNNING) fadeTransition.play();

                triggerReconnect();

            } else {
                // TRƯỜNG HỢP 3: ONLINE (MÀU XANH NHẸ)
                statusCircle.setFill(LIGHT_GREEN);
                if (statusLabel != null) {
                    statusLabel.setText("Online");
                    statusLabel.setTextFill(LIGHT_GREEN);
                }
                fadeTransition.setRate(0.8);
                if (fadeTransition.getStatus() != Animation.Status.RUNNING) fadeTransition.play();

                isReconnecting = false;
            }
        });
    }

    private boolean checkRealInternet() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("8.8.8.8", 53), 1500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void triggerReconnect() {
        if (isReconnecting) return;
        isReconnecting = true;

        Thread reconnectThread = new Thread(() -> {
            try {
                Thread.sleep(2000);
                // AuctionClient.getInstance().connect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                isReconnecting = false;
            }
        });
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }
}