package controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Tiện ích chuyển màn (scene navigation) cho JavaFX.
 *
 * <p>Quy ước: các file FXML được đặt trong resource path {@code /controller/}.
 */
public class SceneHelper {
    private static final String FXML_PATH = "Clients/src/main/resources/du_an_lon/";

    /**
     * Precondition: {@code node} đang thuộc về một {@link javafx.scene.Scene} đã được gắn vào {@link Stage};
     * {@code fxmlFile} là tên file FXML hợp lệ (ví dụ {@code View1.fxml}) tồn tại trong {@code /controller/}.
     * Postcondition: Root của scene hiện tại được thay bằng UI được load từ FXML; stage được resize và center lại.
     * NOTE: Method bắt {@link IOException} và chỉ log ra console; nếu load thất bại UI sẽ không đổi.
     * Method returns: nothing.
     * NOTE: Nếu {@code node.getScene()} hoặc {@code getWindow()} là null sẽ phát sinh {@link NullPointerException}.
     */
    public static void changeScene(Node node, String fxmlFile) {
        try {
            FXMLLoader loader = new FXMLLoader(SceneHelper.class.getResource("/controller/" + fxmlFile));
            Parent root = loader.load();

            Stage stage = (Stage) node.getScene().getWindow();
            stage.getScene().setRoot(root);
            stage.sizeToScene();
            stage.centerOnScreen();

        } catch (IOException e) {
            System.err.println("Lỗi: " + fxmlFile);
            e.printStackTrace();
        }
    }
}