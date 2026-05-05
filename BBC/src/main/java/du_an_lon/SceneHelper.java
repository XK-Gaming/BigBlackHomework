package du_an_lon;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Điều khiển chuyển Scene
 */
public class SceneHelper {
    public static void changeScene(Node node, String fxmlFile) {
        try {
            // 1. Tải file FXML
            // Lưu ý: fxmlFile nên bắt đầu bằng "/" nếu nằm ở root resources
            FXMLLoader loader = new FXMLLoader(SceneHelper.class.getResource(fxmlFile));
            Parent root = loader.load();

            // 2. Lấy Stage từ Node hiện tại
            Stage stage = (Stage) node.getScene().getWindow();

            // 3. Thay đổi root của Scene hiện tại (giúp chuyển trang mượt mà)
            stage.getScene().setRoot(root);

            // Tùy chọn: Tự động căn giữa cửa sổ sau khi đổi nội dung
            // stage.sizeToScene();
            // stage.centerOnScreen();
            stage.sizeToScene();
            stage.centerOnScreen();

        } catch (IOException e) {
            System.err.println("Lỗi: Không tìm thấy file FXML tại " + fxmlFile);
            e.printStackTrace();
        }
    }
}