package controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import model.Items.Item;
import model.User.*;
import org.controlsfx.control.Notifications;

import java.text.DecimalFormat;
import java.util.Map;

// Thông báo realtime cho seller.
public class ControllerNotificationSeller {
    // Xử lý thao tác.
    public static void handleIncomingToastNotificationSeller(Object payload, Label j_textSoDu) {
        DecimalFormat df = new DecimalFormat("#,###");
        try {
            Map<String, Object> notifData = (Map<String, Object>) payload;

            double newPrice = 0;
            Object priceObj = notifData.get("newPrice");
            if (priceObj instanceof Number) {
                newPrice = ((Number) priceObj).doubleValue();
            }

            Item item = (Item) notifData.get("item");
            final double finalPrice = newPrice;

            Platform.runLater(() -> {

                Window currentWindow = (j_textSoDu != null && j_textSoDu.getScene() != null)
                        ? j_textSoDu.getScene().getWindow()
                        : null;

                HBox customToast = new HBox();
                customToast.setAlignment(Pos.CENTER_LEFT);
                customToast.setPrefWidth(300);
                customToast.setStyle("-fx-background-color: #FFFFFF;");

                StackPane iconBlock = new StackPane();
                iconBlock.setPrefSize(60, 70);
                iconBlock.setStyle("-fx-background-color: #1565C0;");

                Label icon = new Label("🔔");
                icon.setStyle("-fx-text-fill: white; -fx-font-size: 22px;");
                iconBlock.getChildren().add(icon);

                VBox textContainer = new VBox();
                textContainer.setSpacing(4);
                textContainer.setAlignment(Pos.CENTER_LEFT);
                textContainer.setPadding(new Insets(10, 10, 10, 15));
                HBox.setHgrow(textContainer, Priority.ALWAYS);

                Label titleLabel = new Label("SẢN PHẨM CÓ LƯỢT ĐẤU GIÁ MỚI!");
                titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #212121; -fx-font-family: 'Segoe UI', Arial;");

                Label messageLabel = new Label("Sản phẩm " + (item != null ? item.getName() : "") + " : " + df.format(finalPrice) + " VNĐ");
                messageLabel.setStyle("-fx-text-fill: #757575; -fx-font-size: 11px; -fx-font-family: 'Segoe UI', Arial;");
                messageLabel.setWrapText(true);
                messageLabel.setMaxWidth(200);

                textContainer.getChildren().addAll(titleLabel, messageLabel);
                customToast.getChildren().addAll(iconBlock, textContainer);

                Notifications notificationBuilder = Notifications.create()
                        .graphic(customToast)
                        .hideAfter(Duration.seconds(4))
                        .position(Pos.BOTTOM_RIGHT);

                if (currentWindow != null) {
                    notificationBuilder.owner(currentWindow);
                }

                customToast.setOnMouseClicked(event -> {
                    if (j_textSoDu != null) {
                        SceneHelper.changeScene(j_textSoDu, "/fxml/ProductListView.fxml");
                    }
                });

                customToast.sceneProperty().addListener((observable, oldScene, newScene) -> {
                    if (newScene != null) {
                        newScene.windowProperty().addListener((obsWin, oldWin, newWin) -> {
                            if (newWin != null) {
                                javafx.scene.Node notificationPopup = newScene.getRoot().lookup(".notification-popup");
                                if (notificationPopup != null) {
                                    notificationPopup.setStyle("-fx-padding: 0;");
                                }
                            }
                        });
                    }
                });

                notificationBuilder.show();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // Xử lý thao tác.
    public static void handleSuccessToastNotificationSeller(Object payload, Label j_textSoDu, User p) {
        DecimalFormat df = new DecimalFormat("#,###");
        try {
            Map<String, Object> notifData = (Map<String, Object>) payload;
            Item item = (Item) notifData.get("item");

            if (item != null && p != null) {
                double auctionPrice = item.getCurrentHighestPrice();
                p.setBalance(p.getBalance() + auctionPrice);
            }

            Platform.runLater(() -> {

                Window currentWindow = (j_textSoDu != null && j_textSoDu.getScene() != null)
                        ? j_textSoDu.getScene().getWindow()
                        : null;

                if (p != null && j_textSoDu != null) {
                    j_textSoDu.setText(df.format(p.getBalance()) + " VNĐ");
                }

                HBox customToast = new HBox();
                customToast.setAlignment(Pos.CENTER_LEFT);
                customToast.setPrefWidth(300);
                customToast.setStyle("-fx-background-color: #FFFFFF;");

                StackPane iconBlock = new StackPane();
                iconBlock.setPrefSize(60, 70);
                iconBlock.setStyle("-fx-background-color: #2E7D32;");

                Label icon = new Label("🎉");
                icon.setStyle("-fx-text-fill: white; -fx-font-size: 22px;");
                iconBlock.getChildren().add(icon);

                VBox textContainer = new VBox();
                textContainer.setSpacing(4);
                textContainer.setAlignment(Pos.CENTER_LEFT);
                textContainer.setPadding(new Insets(10, 10, 10, 15));
                HBox.setHgrow(textContainer, Priority.ALWAYS);

                Label titleLabel = new Label("SẢN PHẨM ĐẤU GIÁ THÀNH CÔNG!");
                titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #212121; -fx-font-family: 'Segoe UI', Arial;");

                Label messageLabel = new Label("Sản phẩm " + (item != null ? item.getName() : "") + " : " + df.format(item != null ? item.getCurrentHighestPrice() : 0) + " VNĐ");
                messageLabel.setStyle("-fx-text-fill: #757575; -fx-font-size: 11px; -fx-font-family: 'Segoe UI', Arial;");
                messageLabel.setWrapText(true);
                messageLabel.setMaxWidth(200);

                textContainer.getChildren().addAll(titleLabel, messageLabel);
                customToast.getChildren().addAll(iconBlock, textContainer);

                Notifications notificationBuilder = Notifications.create()
                        .graphic(customToast)
                        .hideAfter(Duration.seconds(4))
                        .position(Pos.BOTTOM_RIGHT);

                if (currentWindow != null) {
                    notificationBuilder.owner(currentWindow);
                }

                customToast.setOnMouseClicked(event -> {
                    if (j_textSoDu != null) {
                        SceneHelper.changeScene(j_textSoDu, "/fxml/ProductListView.fxml");
                    }
                });

                customToast.sceneProperty().addListener((observable, oldScene, newScene) -> {
                    if (newScene != null) {
                        newScene.windowProperty().addListener((obsWin, oldWin, newWin) -> {
                            if (newWin != null) {
                                javafx.scene.Node notificationPopup = newScene.getRoot().lookup(".notification-popup");
                                if (notificationPopup != null) {
                                    notificationPopup.setStyle("-fx-padding: 0;");
                                }
                            }
                        });
                    }
                });

                notificationBuilder.show();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
