package controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import dao.DAOAuction_Items;
import dao.DAOItems;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import model.Items.Item;
import model.User.User;
import model.User.UserSession;
import model.auction.AuctionStatus;
import network.ClientNetworkExecutor; // Đảm bảo executor luồng phụ hoạt động đồng bộ

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ControllerEditProduct {

    private File selectedFile = null;
    private String uploadedImageUrl = null;
    private Cloudinary cloudinary;

    private Item currentItem;
    private AuctionStatus currentStatus;
    private boolean itemHasBids;

    private final String[] categoryList = new String[]{"Mỹ thuật", "Điện tử", "Phương tiện giao thông"};
    private final Map<String, HBox> categoryPaneMap = new HashMap<>();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML private Label j_LabelName;
    @FXML private Label j_textSoDu;

    @FXML private TextField txtName;
    @FXML private DatePicker dpStartDate;
    @FXML private TextField txtTimeStart;
    @FXML private DatePicker dpEndDate;
    @FXML private TextField txtTimeEnd;
    @FXML private TextField txtStartPrice;
    @FXML private TextArea txtDescription;

    // Khối phục vụ luật Append Note (RUNNING + Has Bids)
    @FXML private VBox vboxAppendNote;
    @FXML private Label lblAppendNoteTitle;
    @FXML private TextArea txtAppendNote;

    // Trường mở rộng loại mặt hàng giống hệt ControllerSeller
    @FXML private ComboBox<String> j_ItemType;
    @FXML private HBox j_paneArt;
    @FXML private HBox j_paneElectronics;
    @FXML private HBox j_paneVehicle;
    @FXML private TextField j_year;
    @FXML private TextField j_manufacturer;
    @FXML private TextField j_model;
    @FXML private TextField j_artist;
    @FXML private TextField j_brand;

    @FXML private ImageView MyImgView;
    @FXML private Button btnUploadImage;
    @FXML private Button btnSave;
    @FXML private Label error_Label;

    @FXML
    public void initialize() {
        initCloudinary();

        // Đăng ký ánh xạ các pane mở rộng
        categoryPaneMap.put("Mỹ thuật", j_paneArt);
        categoryPaneMap.put("Điện tử", j_paneElectronics);
        categoryPaneMap.put("Phương tiện giao thông", j_paneVehicle);

        j_ItemType.getItems().setAll(categoryList);

        // 🌟 THÊM DÒNG NÀY: Lắng nghe sự kiện thay đổi của ComboBox ngay khi khởi tạo
        j_ItemType.getSelectionModel().selectedItemProperty().addListener((options, oldValue, newValue) -> {
            handle_Info(null);
        });

        User currentUser = UserSession.getLoggedInUser();
        if (currentUser != null) {
            j_LabelName.setText(currentUser.getName());
            j_textSoDu.setText("0 VNĐ");
        }

        // Ràng buộc giới hạn ngày cho DatePicker tránh Seller chọn sai thời gian
        setupDatePickerConstraints();
    }

    private void initCloudinary() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("client.properties")) {
            Properties prop = new Properties();
            if (input != null) {
                prop.load(input);
                cloudinary = new Cloudinary(ObjectUtils.asMap(
                        "cloud_name", prop.getProperty("cloudinary.cloud_name"),
                        "api_key", prop.getProperty("cloudinary.api_key"),
                        "api_secret", prop.getProperty("cloudinary.api_secret")
                ));
            } else {
                System.err.println("Warning: Properties not found. Using fallback hardcoded credentials.");
                cloudinary = new Cloudinary(ObjectUtils.asMap(
                        "cloud_name", "dpkehgjjp", "api_key", "168924452148875", "api_secret", "JUjgUwOOFx0UegeGWa3VySf-wW4"
                ));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Nhận dữ liệu truyền từ danh sách sản phẩm và thiết lập giao diện
     */
    public void initData(Item item, AuctionStatus status, boolean hasBids) {
        this.currentItem = item;
        this.currentStatus = status;
        this.itemHasBids = hasBids;
        this.uploadedImageUrl = item.getImg(); // Lưu lại link ảnh cũ mặc định

        // 1. Đổ dữ liệu chữ lên các Control
        txtName.setText(item.getName());
        txtDescription.setText(item.getDescription());
        txtStartPrice.setText(String.format("%.0f", item.getStartingPrice()));

        parseAndSetAuctionTime(item.getAuctionStartTime(), dpStartDate, txtTimeStart);
        parseAndSetAuctionTime(item.getAuctionEndTime(), dpEndDate, txtTimeEnd);

        // 2. Hiển thị ảnh cũ từ Cloudinary URL lên ImageView
        if (item.getImg() != null && !item.getImg().trim().isEmpty()) {
            try {
                MyImgView.setImage(new Image(item.getImg(), true)); // true để tải bất đồng bộ không gây lag UI
            } catch (Exception e) {
                System.err.println("Không thể nạp ảnh cũ từ URL: " + item.getImg());
            }
        }

        // 3. Xử lý đồng bộ Thể loại & Fields mở rộng
        if (item.getItemType() != null) {
            String uiCategoryType = convertModelTypeToUiType(item.getItemType().toString());
            j_ItemType.setValue(uiCategoryType);
            handle_Info(null); // Kích hoạt ẩn/hiện pane tương ứng

            // Đổ dữ liệu các thuộc tính bổ sung nếu có
            Map<String, String> props = item.getProperties();
            if (props != null) {
                j_brand.setText(props.getOrDefault("brand", ""));
                j_model.setText(props.getOrDefault("model", ""));
                j_manufacturer.setText(props.getOrDefault("manufacturer", ""));
                j_year.setText(props.getOrDefault("year", ""));
                j_artist.setText(props.getOrDefault("artist", ""));
            }
        }

        // 4. Reset trạng thái vùng đính kèm note
        vboxAppendNote.setVisible(false);
        vboxAppendNote.setManaged(false);

        // 5. Áp dụng các luật hạn chế sửa giao diện
        applyPermissionRules();
    }

    private void applyPermissionRules() {
        if (currentStatus == AuctionStatus.OPEN || currentStatus == AuctionStatus.CANCELLED) {
            return; // Toàn quyền chỉnh sửa
        }

        if (currentStatus == AuctionStatus.RUNNING) {
            // Khóa các trường cốt lõi trong mọi trạng thái RUNNING
            setCoreFieldsDisabled(true);

            if (!itemHasBids) {
                // CHƯA CÓ BID: Cho phép chỉnh sửa tiếp Mô tả & Đổi ảnh mới
                txtDescription.setDisable(false);
                btnUploadImage.setDisable(false);
            } else {
                // ĐÃ CÓ BID: Khóa toàn bộ form, chỉ kích hoạt khối Append Note
                txtDescription.setDisable(true);
                btnUploadImage.setDisable(true);

                vboxAppendNote.setVisible(true);
                vboxAppendNote.setManaged(true);
                txtAppendNote.setPromptText("Nhập nội dung thông tin bổ sung đính kèm tại đây...");
            }
        }
    }

    private void setCoreFieldsDisabled(boolean disabled) {
        txtName.setDisable(disabled);
        txtStartPrice.setDisable(disabled);
        j_ItemType.setDisable(disabled);
        dpStartDate.setDisable(disabled);
        txtTimeStart.setDisable(disabled);
        dpEndDate.setDisable(disabled);
        txtTimeEnd.setDisable(disabled);
        j_brand.setDisable(disabled);
        j_model.setDisable(disabled);
        j_manufacturer.setDisable(disabled);
        j_year.setDisable(disabled);
        j_artist.setDisable(disabled);
    }

    @FXML
    void On_Save(ActionEvent event) {
        // --- BƯỚC 1: KIỂM TRA CHUẨN VALIDATE BAN ĐẦU TRÊN UI ---
        if (txtName.getText().trim().isEmpty()) {
            showError("Vui lòng nhập Tên sản phẩm (*)");
            return;
        }
        if (txtStartPrice.getText().trim().isEmpty()) {
            showError("Vui lòng nhập Giá khởi điểm (*)");
            return;
        }

        btnSave.setDisable(true);
        error_Label.setTextFill(Color.BLACK);
        error_Label.setText("Đang xác thực thông tin và lưu dữ liệu...");
        error_Label.setVisible(true);

        // --- BƯỚC 2: CHẠY NỀN BẰNG EXECUTOR TRÁNH TREO GIAO DIỆN ---
        ClientNetworkExecutor.execute(() -> {
            try {
                int itemId = currentItem.getDatabaseId();

                // 🌟 PHÒNG CHỐNG ĐA LUỒNG: Đọc trạng thái real-time mới nhất tại DB ngay lúc bấm Lưu
                Item dbItem = DAOItems.getInstance().selectById(String.valueOf(itemId));
                var auctionItem = DAOAuction_Items.getInstance().selectByItemId(dbItem);

                AuctionStatus realStatus = (auctionItem != null) ? auctionItem.getStatus() : AuctionStatus.OPEN;
                boolean realHasBids = (auctionItem != null && auctionItem.getBidHistory() != null && !auctionItem.getBidHistory().isEmpty());

                // Chặn nếu phiên đấu giá đột ngột kết thúc trong lúc đang gõ
                if (realStatus == AuctionStatus.FINISHED || realStatus == AuctionStatus.PAID) {
                    showErrorInUIThread("Phiên đấu giá vừa kết thúc hoặc đã thanh toán. Không thể cập nhật!");
                    return;
                }

                // Chặn xung đột: Lúc mở form báo chưa có lượt đặt giá, lúc bấm lưu thì người ta đã đặt giá mất rồi
                if (currentStatus == AuctionStatus.RUNNING && !itemHasBids && realHasBids) {
                    showErrorInUIThread("Xung đột đa luồng: Đã có người vừa đặt giá! Hệ thống sẽ tải lại form.");
                    Platform.runLater(() -> initData(dbItem, realStatus, realHasBids));
                    return;
                }

                // Xử lý upload ảnh mới lên Cloudinary nếu có thay đổi file
                if (selectedFile != null) {
                    String newUrl = uploadToCloudinary(selectedFile);
                    if (newUrl != null) {
                        uploadedImageUrl = newUrl;
                    } else {
                        showErrorInUIThread("Lỗi tải hình ảnh mới lên hệ thống lưu trữ cloud!");
                        return;
                    }
                }

                // --- BƯỚC 3: TIẾN HÀNH ĐÓNG GÓI DỮ LIỆU ĐỂ UPDATE ---
                if (realStatus == AuctionStatus.RUNNING && realHasBids) {
                    // Áp dụng luật Append Note (Nối đuôi mô tả cũ, bảo vệ dữ liệu gốc)
                    String note = txtAppendNote.getText().trim();
                    if (note.isEmpty()) {
                        showErrorInUIThread("Vui lòng nhập nội dung thông tin bổ sung!");
                        return;
                    }

                    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                    String timestamp = dtf.format(LocalDateTime.now());
                    String appendedDescription = dbItem.getDescription() + "\n[* Cập nhật ngày " + timestamp + ": " + note + "]";

                    dbItem.setDescription(appendedDescription);
                    DAOItems.getInstance().Update(dbItem);
                } else {
                    // Chế độ sửa thông thường (OPEN, CANCELLED hoặc RUNNING chưa có bid)
                    dbItem.setName(txtName.getText().trim());
                    dbItem.setDescription(txtDescription.getText().trim());
                    dbItem.setImg(uploadedImageUrl);

                    /// Thuộc tính mở rộng
                    Map<String, String> extraFields = new HashMap<>();

                    // Ép các trường trống về null để JSON sinh ra đúng dạng "artist": null thay vì "artist": ""
                    extraFields.put("brand", j_brand.getText().trim().isEmpty() ? null : j_brand.getText().trim());
                    extraFields.put("model", j_model.getText().trim().isEmpty() ? null : j_model.getText().trim());
                    extraFields.put("manufacturer", j_manufacturer.getText().trim().isEmpty() ? null : j_manufacturer.getText().trim());
                    extraFields.put("year", j_year.getText().trim().isEmpty() ? null : j_year.getText().trim());
                    extraFields.put("artist", j_artist.getText().trim().isEmpty() ? null : j_artist.getText().trim());

                    dbItem.setProperties(extraFields);

                // Đồng bộ lại chuỗi ItemType từ UI vào Model trước khi lưu
                    dbItem.setItemType(model.Items.ItemType.fromString(j_ItemType.getValue()));

                    // Riêng OPEN / CANCELLED được quyền sửa đổi thêm Giá trị xuất phát & Thời gian
                    if (realStatus == AuctionStatus.OPEN || realStatus == AuctionStatus.CANCELLED) {
                        try {
                            double startingPrice = Double.parseDouble(txtStartPrice.getText().trim());
                            if (startingPrice <= 0) throw new NumberFormatException();
                            dbItem.setStartingPrice(startingPrice);
                        } catch (NumberFormatException e) {
                            showErrorInUIThread("Giá khởi điểm nhập vào phải là số dương hợp lệ!");
                            return;
                        }

                        try {
                            Instant startInstant = createInstant(dpStartDate, txtTimeStart);
                            Instant endInstant = createInstant(dpEndDate, txtTimeEnd);

                            if (startInstant.isBefore(Instant.now())) {
                                throw new IllegalArgumentException("Thời gian bắt đầu chỉnh sửa không được nhỏ hơn hiện tại!");
                            }
                            if (endInstant.isBefore(startInstant)) {
                                throw new IllegalArgumentException("Thời gian kết thúc phải sau mốc bắt đầu!");
                            }

                            dbItem.setAuctionStartTime(startInstant);
                            dbItem.setAuctionEndTime(endInstant);
                        } catch (DateTimeParseException e) {
                            showErrorInUIThread("Định dạng giờ nhập vào không đúng quy chuẩn (HH:mm:ss)!");
                            return;
                        } catch (IllegalArgumentException e) {
                            showErrorInUIThread(e.getMessage());
                            return;
                        }
                    }

                    // Gọi tầng dữ liệu ghi đè bản ghi mới xuống DB
                    DAOItems.getInstance().Update(dbItem);
                }

                // Cập nhật UI thông báo thành công và chuyển scene về danh sách
                Platform.runLater(() -> {
                    error_Label.setTextFill(Color.GREEN);
                    error_Label.setText("Cập nhật thông tin sản phẩm thành công!");

                    Alert alert = new Alert(Alert.AlertType.INFORMATION, "Cập nhật sản phẩm thành công!", ButtonType.OK);
                    alert.showAndWait();
                    On_Back(event);
                });

            } catch (Exception e) {
                e.printStackTrace();
                showErrorInUIThread("Hệ thống gặp lỗi trong quá trình đồng bộ lưu dữ liệu!");
            }
        });
    }

    public String uploadToCloudinary(File localFile) {
        try {
            Map uploadResult = cloudinary.uploader().upload(localFile, ObjectUtils.emptyMap());
            return (String) uploadResult.get("secure_url");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @FXML
    void handle_SelectImage(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        File file = fileChooser.showOpenDialog(((Node) event.getSource()).getScene().getWindow());
        if (file != null) {
            this.selectedFile = file;
            MyImgView.setImage(new Image(file.toURI().toString()));
        }
    }


    @FXML
    void On_Back(ActionEvent event) {
        SceneHelper.changeScene((Node) event.getSource(), "/fxml/ProductListView.fxml");
    }

    @FXML
    void On_LogOut(ActionEvent event) {
        UserSession.cleanUserSession();
        SceneHelper.changeScene((Node) event.getSource(), "/fxml/LoginView.fxml");
    }

    // --- CÁC PHƯƠNG THỨC TIỆN ÍCH TRỢ GIÚP ĐỒNG BỘ LOGIC ---
    private void parseAndSetAuctionTime(Instant instant, DatePicker datePicker, TextField timeField) {
        if (instant == null) return;
        LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        datePicker.setValue(ldt.toLocalDate());
        timeField.setText(ldt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    public Instant createInstant(DatePicker datePicker, TextField timeField) throws DateTimeParseException {
        LocalDate date = datePicker.getValue();
        String timeStr = timeField.getText().trim();
        if (timeStr.matches("^\\d{1,2}:\\d{2}$")) {
            timeStr += ":00";
        }
        return LocalDateTime.of(date, LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm:ss")))
                .atZone(ZoneId.systemDefault())
                .toInstant();
    }

    private void setupDatePickerConstraints() {
        dpStartDate.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(LocalDate.now()));
            }
        });
        dpEndDate.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                LocalDate minDate = (dpStartDate.getValue() != null) ? dpStartDate.getValue() : LocalDate.now();
                setDisable(empty || date.isBefore(minDate));
            }
        });
    }

    private String convertModelTypeToUiType(String modelType) {
        if (modelType == null) return categoryList[0];
        return switch (modelType) {
            case "ART", "Mỹ thuật" -> "Mỹ thuật";
            case "ELECTRONICS", "Điện tử" -> "Điện tử";
            case "VEHICLE", "Phương tiện giao thông" -> "Phương tiện giao thông";
            default -> categoryList[0];
        };
    }

    private void showError(String message) {
        error_Label.setTextFill(Color.RED);
        error_Label.setText(message);
        error_Label.setVisible(true);
    }

    private void showErrorInUIThread(String message) {
        Platform.runLater(() -> {
            showError(message);
            btnSave.setDisable(false);
        });
    }
    @FXML
    void handle_Info(ActionEvent event) {
        String selectedCategory = j_ItemType.getValue();

        categoryPaneMap.forEach((category, pane) -> {
            if (pane != null) {
                boolean isMatch = category.equals(selectedCategory);
                pane.setVisible(isMatch);
                pane.setManaged(isMatch); // Giúp giao diện co giãn, không để lại khoảng trống thụt vào
            }
        });

        // 🌟 THÊM LOGIC: Xóa sạch dữ liệu của các trường KHÔNG thuộc nhóm được chọn
        if (!"Mỹ thuật".equals(selectedCategory)) {
            j_artist.setText("");
        }
        if (!"Điện tử".equals(selectedCategory)) {
            j_brand.setText("");
            j_model.setText("");
        }
        if (!"Phương tiện giao thông".equals(selectedCategory)) {
            j_manufacturer.setText("");
            j_year.setText("");
        }
    }
}