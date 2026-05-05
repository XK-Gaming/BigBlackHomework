package du_an_lon;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

public class SceneHelper {
    private static final String FXML_PATH = "Clients/src/main/resources/du_an_lon/";

    public static void changeScene(Node node, String fxmlFile) {
        try {
            String basePath = System.getProperty("user.dir");
            File fxml = new File(basePath + "/" + FXML_PATH + fxmlFile);
            
            if (!fxml.exists()) {
                // Thử đường dẫn khác
                fxml = new File(basePath + "/target/classes/du_an_lon/" + fxmlFile);
            }
            
            if (!fxml.exists()) {
                System.err.println("Lỗi: Không tìm thấy file FXML: " + fxml.getAbsolutePath());
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxml.toURI().toURL());
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