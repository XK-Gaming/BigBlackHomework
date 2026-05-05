package du_an_lon;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import network.AuctionClient;

import java.io.File;
import java.io.IOException;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        try {
            AuctionClient client = AuctionClient.getInstance();
            client.connect("192.168.0.106", 8080);
        } catch (Exception e) {
            System.err.println("Không thể kết nối server: " + e.getMessage());
        }

        String basePath = System.getProperty("user.dir");
        File fxmlFile = new File(basePath + "/Clients/src/main/resources/du_an_lon/View1.fxml");
        
        if (!fxmlFile.exists()) {
            fxmlFile = new File(basePath + "/target/classes/du_an_lon/View1.fxml");
        }

        FXMLLoader loader = new FXMLLoader(fxmlFile.toURI().toURL());
        Parent root = loader.load();
        
        stage.setTitle("Hệ thống đấu giá trực tuyến");
        stage.setScene(new Scene(root));
        stage.show();
    }
}