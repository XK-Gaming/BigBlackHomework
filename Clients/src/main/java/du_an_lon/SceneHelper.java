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
            FXMLLoader loader = new FXMLLoader(SceneHelper.class.getResource("/du_an_lon/" + fxmlFile));
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