package controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import model.User.User;
import model.User.UserSession;
import network.AuctionClient;
import network.Command;
import network.DataPacket;
import network.ServerListener;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class ControllerSetInf implements ServerListener {
    User p1 = UserSession.getLoggedInUser();
    private AuctionClient client = AuctionClient.getInstance();

    // Menu Components
    @FXML private ImageView j_image;
    @FXML private Button j_return;
    @FXML private Label j_LabelName;
    @FXML private Label show_userName;
    @FXML private Label show_Password;

    // Panes
    @FXML private AnchorPane Pane_ThongTinTaiKhoan;
    @FXML private AnchorPane Pane_DoiMatKhau;
    @FXML private AnchorPane Pane_ThanhToan;
    @FXML private AnchorPane Pane_CaiDat;

    // Menu Buttons
    @FXML private Button j_buttonThongTinDangNhap;
    @FXML private Button j_buttonThanhToan;
    @FXML private Button j_buttonDoiMatKhau;
    @FXML private Button j_buttonCaiDat;
    @FXML private Button j_buttonDangXuat;

    // FXML IDs: Cập nhật thông tin
    @FXML private TextField j_inputNewName;
    @FXML private TextField j_inputNewTel;
    @FXML private Label j_labelMessageName;

    // FXML IDs: Đổi mật khẩu
    @FXML private PasswordField j_inputOldPassword;
    @FXML private PasswordField j_inputNewPassword;
    @FXML private PasswordField j_inputConfirmPassword;
    @FXML private Label j_labelMessagePassword;

    // FXML IDs: Thanh toán
    @FXML private TextField j_inputMoney;
    @FXML private Label j_labelMessagePayment;

    public void initialize() {
        client.setListener(this);

        if (p1 != null) {
            // Set thông tin hiển thị ở Menu Trái
            show_userName.setText(p1.getUsername());
            show_Password.setText("********");
            j_LabelName.setText(p1.getName());
        }
    }

    @FXML
    void On_MouseClickImg(MouseEvent event) {
        // Có thể thêm tính năng upload avatar ở đây
    }

    @FXML
    void j_event_return(ActionEvent event) {
        SceneHelper.changeScene((Node) j_return, "View3.fxml");
    }

    // ----------------------------------------------------
    // ĐIỀU HƯỚNG MENU (CHUYỂN PANE)
    // ----------------------------------------------------

    private void hideAllPanes() {
        Pane_ThongTinTaiKhoan.setVisible(false);
        Pane_ThanhToan.setVisible(false);
        Pane_DoiMatKhau.setVisible(false);
        Pane_CaiDat.setVisible(false);
    }

    @FXML
    void j_OnbuttonThongTinDangNhap(ActionEvent event) {
        hideAllPanes();
        Pane_ThongTinTaiKhoan.setVisible(true);
    }

    @FXML
    void j_OnbuttonThanhToan(ActionEvent event) {
        hideAllPanes();
        Pane_ThanhToan.setVisible(true);
    }

    @FXML
    void j_OnbuttonDoiMatKhau(ActionEvent event) {
        hideAllPanes();
        Pane_DoiMatKhau.setVisible(true);
    }

    @FXML
    void j_OnbuttonCaiDat(ActionEvent event) {
        hideAllPanes();
        Pane_CaiDat.setVisible(true);
    }

    @FXML
    void j_OnbuttonDangXuat(ActionEvent event) {
        try {
            client.sendCommand(Command.LOGOUT, UserSession.getLoggedInUser().getUsername());
            UserSession.cleanUserSession();
            SceneHelper.changeScene((Node) j_buttonDangXuat, "View1.fxml");
        } catch (IOException e) {
            System.err.println("Lỗi khi đăng xuất: " + e.getMessage());
        }
    }

    // ----------------------------------------------------
    // XỬ LÝ NGHIỆP VỤ (CẬP NHẬT TT, ĐỔI MẬT KHẨU, NẠP TIỀN)
    // ----------------------------------------------------

    @FXML
    void j_OnUpdateInfo(ActionEvent event) {
        String newName = j_inputNewName.getText();
        String newTel = j_inputNewTel.getText();

        if (newName == null || newName.trim().isEmpty()) {
            j_labelMessageName.setTextFill(Color.RED);
            j_labelMessageName.setText("Tên không được để trống!");
            j_labelMessageName.setVisible(true);
            return;
        }

        try {
            // Gửi cập nhật Tên
            client.sendCommand(Command.UPDATE_USER, Map.of(
                    "username", p1.getUsername(),
                    "field", "name",
                    "value", newName
            ));

            // Gửi cập nhật SĐT (Nếu có nhập)
            if (newTel != null && !newTel.trim().isEmpty()) {
                client.sendCommand(Command.UPDATE_USER, Map.of(
                        "username", p1.getUsername(),
                        "field", "phone",
                        "value", newTel
                ));
            }

            j_labelMessageName.setTextFill(Color.BLUE);
            j_labelMessageName.setText("Đang gửi yêu cầu cập nhật...");
            j_labelMessageName.setVisible(true);

        } catch (IOException e) {
            j_labelMessageName.setTextFill(Color.RED);
            j_labelMessageName.setText("Lỗi kết nối Server!");
            j_labelMessageName.setVisible(true);
            System.err.println("Lỗi kết nối khi cập nhật thông tin: " + e.getMessage());
        }
    }

    @FXML
    void j_OnChangePassword(ActionEvent event) {
        String oldPassword = j_inputOldPassword.getText();
        String newPassword = j_inputNewPassword.getText();
        String confirmPassword = j_inputConfirmPassword.getText();

        if (oldPassword == null || oldPassword.isEmpty() ||
                newPassword == null || newPassword.isEmpty() ||
                confirmPassword == null || confirmPassword.isEmpty()) {
            j_labelMessagePassword.setTextFill(Color.RED);
            j_labelMessagePassword.setText("Vui lòng điền đầy đủ thông tin!");
            j_labelMessagePassword.setVisible(true);
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            j_labelMessagePassword.setTextFill(Color.RED);
            j_labelMessagePassword.setText("Mật khẩu mới không khớp!");
            j_labelMessagePassword.setVisible(true);
            return;
        }

        try {
            client.sendCommand(Command.CHANGE_PASSWORD, Map.of(
                    "username", p1.getUsername(),
                    "oldPassword", oldPassword,
                    "newPassword", newPassword
            ));
            j_labelMessagePassword.setTextFill(Color.BLUE);
            j_labelMessagePassword.setText("Đang xử lý...");
            j_labelMessagePassword.setVisible(true);
        } catch (IOException e) {
            j_labelMessagePassword.setTextFill(Color.RED);
            j_labelMessagePassword.setText("Lỗi kết nối Server!");
            j_labelMessagePassword.setVisible(true);
            System.err.println("Không thể gửi yêu cầu đổi mật khẩu: " + e.getMessage());
        }
    }

    // ----------------------------------------------------
    // XỬ LÝ NGHIỆP VỤ NẠP TIỀN
    // ----------------------------------------------------
    String moneyStr;
    @FXML
    void j_OnPayMent(ActionEvent event) {
        moneyStr = j_inputMoney.getText();

        if (moneyStr == null || moneyStr.trim().isEmpty()) {
            j_labelMessagePayment.setTextFill(Color.RED);
            j_labelMessagePayment.setText("Vui lòng nhập số tiền!");
            j_labelMessagePayment.setVisible(true);
            return;
        }

        try {
            double money = Double.parseDouble(moneyStr);
            if (money <= 0) {
                j_labelMessagePayment.setTextFill(Color.RED);
                j_labelMessagePayment.setText("Số tiền phải lớn hơn 0!");
                j_labelMessagePayment.setVisible(true);
                return;
            }

            j_labelMessagePayment.setTextFill(Color.BLUE);
            DecimalFormat df = new DecimalFormat("#,###");
            j_labelMessagePayment.setText("Đang xử lý giao dịch nạp " + df.format(money) + " VNĐ...");
            j_labelMessagePayment.setVisible(true);
            j_inputMoney.clear();

            client.sendCommand(Command.RECHARGE_AMOUNT,
                    Map.of("username", p1.getUsername(),
                    "amount", money
            ));


        } catch (NumberFormatException e) {
            j_labelMessagePayment.setTextFill(Color.RED);
            j_labelMessagePayment.setText("Vui lòng chỉ nhập số (VD: 100000)!");
            j_labelMessagePayment.setVisible(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ----------------------------------------------------
    // LẮNG NGHE PHẢN HỒI TỪ SERVER
    // ----------------------------------------------------

    @Override
    public void onServerResponse(DataPacket response) {
        Command command = response.getCommand();

        if (Command.UPDATE_USER_RESULT.equals(command) || "UPDATE_USER_RESULT".equals(command.toString())) {
            Map<String, Object> result = (Map<String, Object>) response.getPayload();
            boolean isSuccess = (boolean) result.get("success");
            String message = (String) result.get("message");

            Platform.runLater(() -> {
                j_labelMessageName.setTextFill(isSuccess ? Color.BLUE : Color.RED);
                j_labelMessageName.setText(message);
                j_labelMessageName.setVisible(true);

                if (isSuccess) {
                    User p1 = UserSession.getLoggedInUser();
                    p1.setName(j_inputNewName.getText());
                    j_LabelName.setText(j_inputNewName.getText());
                }
            });
        }
        if (Command.CHANGE_PASSWORD_RESULT.equals(command) || "CHANGE_PASSWORD_RESULT".equals(command.toString())) {
            Map<String, Object> result = (Map<String, Object>) response.getPayload();
            boolean isSuccess = (boolean) result.get("success");
            String message = (String) result.get("message");

            Platform.runLater(() -> {
                j_labelMessagePassword.setTextFill(isSuccess ? Color.BLUE : Color.RED);
                j_labelMessagePassword.setText(message);
                j_labelMessagePassword.setVisible(true);

                if (isSuccess) {
                    j_inputOldPassword.clear();
                    j_inputNewPassword.clear();
                    j_inputConfirmPassword.clear();
                }
            });
        }
        if(Command.RECHARGE_AMOUNT_RESULT.equals(command)){
            boolean isSuccess = (boolean) response.getPayload();
            Platform.runLater(() -> {
                if (isSuccess) {
                    j_labelMessagePayment.setTextFill(Color.GREEN);
                    j_labelMessagePayment.setText("Nạp tiền thành công!");
                    p1.setBalance(p1.getBalance() + Double.parseDouble(moneyStr));
                } else {
                    j_labelMessagePayment.setTextFill(Color.RED);
                    j_labelMessagePayment.setText("Nạp tiền thất bại");
                }
                j_labelMessagePayment.setVisible(true);
            });
        }
        // Thêm bắt sự kiện phản hồi giao dịch tiền ở đây (VD: Command.RECHARGE_RESULT)
    }
}