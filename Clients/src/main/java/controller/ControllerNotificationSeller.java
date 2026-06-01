package controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import model.Items.Item;
import model.User.*;
import org.controlsfx.control.Notifications;

import java.text.DecimalFormat;
import java.util.Map;

public class ControllerNotificationSeller {

    public static void handleIncomingToastNotificationSeller(Object payload, Label j_textSoDu) {
        DecimalFormat df = new DecimalFormat("#,###");
        try {
            // 1. Giải mã gói tin từ Server an toàn
            Map<String, Object> notifData = (Map<String, Object>) payload;

            double newPrice = 0;
            Object priceObj = notifData.get("newPrice");
            if (priceObj instanceof Number) {
                newPrice = ((Number) priceObj).doubleValue();
            }

            Item item = (Item) notifData.get("item");
            final double finalPrice = newPrice;

            // 2. Đẩy việc hiển thị lên UI Thread của JavaFX
            Platform.runLater(() -> {

                // [THAY ĐỔI]: Tạo Layout chính ôm nội dung, bỏ viền và bóng đổ để hòa làm một với khung ngoài
                HBox customToast = new HBox();
                customToast.setAlignment(Pos.CENTER_LEFT);
                customToast.setPrefWidth(300); // Thu nhỏ lại một chút để vừa vặn với khung chứa của ControlsFX
                customToast.setStyle("-fx-background-color: #FFFFFF;"); // Chỉ cần nền trắng đơn giản

                // 3. Khối Icon bên trái (Nền màu xanh dương đậm)
                StackPane iconBlock = new StackPane();
                iconBlock.setPrefSize(60, 70);
                iconBlock.setStyle("-fx-background-color: #1565C0;"); // Khung ngoài sẽ tự bo góc nên ở đây để vuông

                Label icon = new Label("🔔");
                icon.setStyle("-fx-text-fill: white; -fx-font-size: 22px;");
                iconBlock.getChildren().add(icon);

                // 4. Phần chữ hiển thị (VBox) ở giữa
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

                // [THAY ĐỔI]: Chỉ thêm khối icon và khối chữ vào layout (Đã loại bỏ nút x tự chế)
                customToast.getChildren().addAll(iconBlock, textContainer);

                // 5. Khởi tạo ControlsFX và tận dụng hệ thống mặc định
                Notifications notificationBuilder = Notifications.create()
                        .graphic(customToast) // Nhúng nội dung custom vào
                        .hideAfter(Duration.seconds(4)) // Tự động ẩn sau 4 giây
                        .position(Pos.BOTTOM_RIGHT); // Xuất hiện góc dưới bên phải
                customToast.setOnMouseClicked(event -> {
                    SceneHelper.changeScene(j_textSoDu, "/fxml/ProductListView.fxml");
                });
                // [MẸO ĐẸP]: Xóa bỏ padding thừa của khung ngoài để khối màu xanh sát rạt ra rìa trái
                customToast.sceneProperty().addListener((observable, oldScene, newScene) -> {
                    if (newScene != null) {
                        newScene.windowProperty().addListener((obsWin, oldWin, newWin) -> {
                            if (newWin != null) {
                                javafx.scene.Node notificationPopup = newScene.getRoot().lookup(".notification-popup");
                                if (notificationPopup != null) {
                                    // Ép padding về 0 để phần màu xanh bám sát viền trái ngoài cùng
                                    notificationPopup.setStyle("-fx-padding: 0;");
                                }
                            }
                        });
                    }
                });

                // Hiển thị thông báo lên màn hình
                notificationBuilder.show();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void handleSuccessToastNotificationSeller(Object payload, Label j_textSoDu, User p) {
        DecimalFormat df = new DecimalFormat("#,###");
        try {
            // 1. Giải mã gói tin từ Server an toàn
            Map<String, Object> notifData = (Map<String, Object>) payload;
            Item item = (Item) notifData.get("item");

            if (item != null && p != null) {
                double auctionPrice = item.getCurrentHighestPrice();
                p.setBalance(p.getBalance() + auctionPrice);
            }

            // 2. Đẩy việc hiển thị lên UI Thread của JavaFX
            Platform.runLater(() -> {
                if (p != null && j_textSoDu != null) {
                    j_textSoDu.setText(df.format(p.getBalance()) + " VNĐ");
                }

                // [THAY ĐỔI]: Tạo Layout chính ôm nội dung, bỏ viền và bóng đổ để hòa làm một với khung ngoài
                HBox customToast = new HBox();
                customToast.setAlignment(Pos.CENTER_LEFT);
                customToast.setPrefWidth(300); // Thu nhỏ lại một chút để vừa vặn với khung chứa của ControlsFX
                customToast.setStyle("-fx-background-color: #FFFFFF;"); // Chỉ cần nền trắng đơn giản

                // 3. Khối Icon bên trái (Nền màu xanh dương đậm)
                StackPane iconBlock = new StackPane();
                iconBlock.setPrefSize(60, 70);
                iconBlock.setStyle("-fx-background-color: #1565C0;"); // Khung ngoài sẽ tự bo góc nên ở đây để vuông

                Label icon = new Label("🔔");
                icon.setStyle("-fx-text-fill: white; -fx-font-size: 22px;");
                iconBlock.getChildren().add(icon);

                // 4. Phần chữ hiển thị (VBox) ở giữa
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

                // [THAY ĐỔI]: Chỉ thêm khối icon và khối chữ vào layout (Đã loại bỏ nút x tự chế)
                customToast.getChildren().addAll(iconBlock, textContainer);

                // 5. Khởi tạo ControlsFX và tận dụng hệ thống mặc định
                Notifications notificationBuilder = Notifications.create()
                        .graphic(customToast) // Nhúng nội dung custom vào
                        .hideAfter(Duration.seconds(4)) // Tự động ẩn sau 4 giây
                        .position(Pos.BOTTOM_RIGHT); // Xuất hiện góc dưới bên phải

                customToast.setOnMouseClicked(event -> {
                    SceneHelper.changeScene(j_textSoDu, "/fxml/ProductListView.fxml");
                });

                // [MẸO ĐẸP]: Xóa bỏ padding thừa của khung ngoài để khối màu xanh sát rạt ra rìa trái
                customToast.sceneProperty().addListener((observable, oldScene, newScene) -> {
                    if (newScene != null) {
                        newScene.windowProperty().addListener((obsWin, oldWin, newWin) -> {
                            if (newWin != null) {
                                javafx.scene.Node notificationPopup = newScene.getRoot().lookup(".notification-popup");
                                if (notificationPopup != null) {
                                    // Ép padding về 0 để phần màu xanh bám sát viền trái ngoài cùng
                                    notificationPopup.setStyle("-fx-padding: 0;");
                                }
                            }
                        });
                    }
                });

                // Hiển thị thông báo lên màn hình
                notificationBuilder.show();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
