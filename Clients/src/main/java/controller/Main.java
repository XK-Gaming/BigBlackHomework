package controller;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import network.AuctionClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * JavaFX {@link Application} chính của module client.
 *
 * <p>Trách nhiệm:
 * <ul>
 *   <li>Đọc cấu hình kết nối server từ {@code client.properties} (resource).</li>
 *   <li>Khởi tạo kết nối socket bằng {@link AuctionClient}.</li>
 *   <li>Load màn hình đầu tiên {@code View1.fxml}.</li>
 * </ul>
 */
public class Main extends Application {
    @Override
    /**
     * Precondition: JavaFX runtime đã khởi tạo và truyền vào {@code stage} hợp lệ.
     * Postcondition:
     * - Đã cố gắng đọc cấu hình server (nếu fail dùng mặc định).
     * - Đã cố gắng connect tới server (nếu fail vẫn tiếp tục mở UI).
     * - Stage được set scene và hiển thị màn hình login/trang đầu.
     * NOTE: Nếu server không kết nối được, UI vẫn mở nhưng các thao tác gửi command có thể fail.
     * Method returns: nothing.
     * @throws IOException NOTE: Có thể xảy ra khi load FXML {@code /controller/View1.fxml}.
     */
    public void start(Stage stage) throws IOException {
        String serverIp = "localhost";
        int serverPort = 8080;

        Properties props = new Properties();
        try (InputStream input = Main.class.getClassLoader().getResourceAsStream("client.properties")) {
            if (input != null) {
                props.load(input);
                serverIp = props.getProperty("server.ip", "localhost");
                serverPort = Integer.parseInt(props.getProperty("server.port", "8080"));
            }
        } catch (Exception e) {
            System.err.println("Không load được client.properties, dùng cấu hình mặc định");
        }

        try {
            AuctionClient client = AuctionClient.getInstance();
            client.connect(serverIp, serverPort);
        } catch (Exception e) {
            System.err.println("Không thể kết nối server: " + e.getMessage());
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/controller/View1.fxml"));
        Parent root = loader.load();
        
        stage.setTitle("Hệ thống đấu giá trực tuyến");
        stage.setScene(new Scene(root));
        stage.show();
    }
}