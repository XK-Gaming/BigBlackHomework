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

// Theo dõi trạng thái kết nối.
public class ConnectionStatusManager {
    private final Circle statusCircle;
    private final Label statusLabel;
    private Timeline timeline;
    private FadeTransition fadeTransition;

    private boolean isReconnecting = false;

    private static final Color LIGHT_GREEN = Color.web("#2ECC71");
    private static final Color RED = Color.web("#E74C3C");
    private static final Color YELLOW = Color.web("#F1C40F");

    private final DropShadow whiteGlow = new DropShadow();

    public ConnectionStatusManager(Circle statusCircle, Label statusLabel) {
        this.statusCircle = statusCircle;
        this.statusLabel = statusLabel;

        whiteGlow.setColor(Color.WHITE);
        whiteGlow.setRadius(8.0);
        whiteGlow.setOffsetX(0.0);
        whiteGlow.setOffsetY(0.0);

        this.statusCircle.setEffect(whiteGlow);
        if (this.statusLabel != null) {
            this.statusLabel.setEffect(whiteGlow);
        }

        setupBlinking();
    }
    // Tạo hiệu ứng trạng thái mạng.
    private void setupBlinking() {
        fadeTransition = new FadeTransition(Duration.seconds(0.8), statusCircle);
        fadeTransition.setFromValue(1.0);
        fadeTransition.setToValue(0.4);
        fadeTransition.setCycleCount(Animation.INDEFINITE);
        fadeTransition.setAutoReverse(true);
    }
    // Bắt đầu theo dõi kết nối.
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
    // Dừng theo dõi kết nối.
    public void stopMonitoring() {
        if (timeline != null) {
            timeline.stop();
        }
        if (fadeTransition != null) {
            fadeTransition.stop();
        }
    }
    // Kiểm tra internet và server.
    private void checkNetworkAndStatus() {
        boolean hasInternet = checkRealInternet();
        boolean isConnectedToServer = AuctionClient.getInstance().isConnected();

        Platform.runLater(() -> {
            if (!hasInternet) {

                statusCircle.setFill(RED);
                if (statusLabel != null) {
                    statusLabel.setText("Disconnect Internet");
                    statusLabel.setTextFill(RED);
                }
                fadeTransition.setRate(2.5);
                if (fadeTransition.getStatus() != Animation.Status.RUNNING) fadeTransition.play();

                triggerReconnect();

            } else if (!isConnectedToServer) {

                statusCircle.setFill(YELLOW);
                if (statusLabel != null) {
                    statusLabel.setText("Disconnect Server");
                    statusLabel.setTextFill(YELLOW);
                }
                fadeTransition.setRate(1.5);
                if (fadeTransition.getStatus() != Animation.Status.RUNNING) fadeTransition.play();

                triggerReconnect();

            } else {

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
    // Kiểm tra internet thật.
    private boolean checkRealInternet() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("8.8.8.8", 53), 1500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    // Thử kết nối lại.
    private void triggerReconnect() {
        if (isReconnecting) return;
        isReconnecting = true;

        Thread reconnectThread = new Thread(() -> {
            try {
                Thread.sleep(2000);

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
