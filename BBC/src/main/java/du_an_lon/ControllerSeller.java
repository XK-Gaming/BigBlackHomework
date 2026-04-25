package du_an_lon;

import java.io.File;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import model.Items.ItemSession;
import model.User.Seller;
import model.User.User;
import model.User.UserSession;
import service.AppServices;
import service.ProfileService;
import service.SellerService;
import service.dto.CreateListingRequest;

public class ControllerSeller {
    private final SellerService sellerService = AppServices.sellerService();
    private final ProfileService profileService = AppServices.profileService();

    private String selectedImagePath;

    @FXML
    private Button LogOut;

    @FXML
    private Label error_Label;

    @FXML
    private ComboBox<String> j_ItemType;

    @FXML
    private Label j_LabelName;

    @FXML
    private TextField j_StartingPrice;

    @FXML
    private TextArea j_description;

    @FXML
    private TextField j_name;

    @FXML
    private DatePicker j_DateEnd;

    @FXML
    private DatePicker j_DateStart;

    @FXML
    private TextField j_TimeEnd;

    @FXML
    private TextField j_TimeStart;

    @FXML
    private ImageView MyImgView;

    @FXML
    private AnchorPane j_paneArt;

    @FXML
    private AnchorPane j_paneElectronics;

    @FXML
    private AnchorPane j_paneVehicle;

    @FXML
    private TextField j_manufacturer;

    @FXML
    private TextField j_model;

    @FXML
    private TextField j_artist;

    @FXML
    private TextField j_brand;

    @FXML
    private TextField j_year;

    public void initialize() {
        j_ItemType.getItems().setAll(sellerService.getItemTypeLabels());
        j_ItemType.setValue(SellerService.ART_LABEL);

        User user = UserSession.getLoggedInUser();
        if (user != null) {
            j_LabelName.setText(user.getFullName());
        }
        handle_Info(null);
    }

    public void On_MouseClickImg(javafx.scene.input.MouseEvent mouseEvent) {
        SceneHelper.changeScene((Node) mouseEvent.getSource(), "View5.fxml");
    }

    @FXML
    void On_LogOut(ActionEvent event) {
        UserSession.cleanUserSession();
        ItemSession.cleanItemSession();
        SceneHelper.changeScene(LogOut, "View1.fxml");
    }

    public void handle_Items() {
        error_Label.setVisible(false);
        try {
            User user = profileService.requireCurrentUser();
            if (!(user instanceof Seller seller)) {
                throw new IllegalStateException("Only seller can create listings.");
            }

            sellerService.createListing(new CreateListingRequest(
                    seller,
                    j_name.getText(),
                    j_description.getText(),
                    j_StartingPrice.getText(),
                    j_DateStart.getValue(),
                    j_TimeStart.getText(),
                    j_DateEnd.getValue(),
                    j_TimeEnd.getText(),
                    j_ItemType.getValue(),
                    primaryAttribute(),
                    secondaryAttribute(),
                    selectedImagePath
            ));
            FxViewUtils.showSuccess(error_Label, "Dang san pham thanh cong.");
            clearForm();
        } catch (RuntimeException exception) {
            FxViewUtils.showError(error_Label, exception.getMessage());
        }
    }

    @FXML
    void handle_SelectImage(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file == null) {
            return;
        }

        selectedImagePath = file.getAbsolutePath();
        MyImgView.setImage(new Image(file.toURI().toString()));
    }

    @FXML
    void handle_Info(ActionEvent event) {
        resetPane();
        String selectedType = j_ItemType.getValue();
        if (SellerService.ART_LABEL.equals(selectedType)) {
            j_paneArt.setVisible(true);
        } else if (SellerService.ELECTRONICS_LABEL.equals(selectedType)) {
            j_paneElectronics.setVisible(true);
        } else if (SellerService.VEHICLE_LABEL.equals(selectedType)) {
            j_paneVehicle.setVisible(true);
        }
    }

    private void resetPane() {
        j_paneArt.setVisible(false);
        j_paneElectronics.setVisible(false);
        j_paneVehicle.setVisible(false);
    }

    private String primaryAttribute() {
        String selectedType = j_ItemType.getValue();
        if (SellerService.ART_LABEL.equals(selectedType)) {
            return j_artist.getText();
        }
        if (SellerService.ELECTRONICS_LABEL.equals(selectedType)) {
            return j_brand.getText();
        }
        return j_manufacturer.getText();
    }

    private String secondaryAttribute() {
        String selectedType = j_ItemType.getValue();
        if (SellerService.ELECTRONICS_LABEL.equals(selectedType)) {
            return j_model.getText();
        }
        if (SellerService.VEHICLE_LABEL.equals(selectedType)) {
            return j_year.getText();
        }
        return "";
    }

    private void clearForm() {
        j_name.clear();
        j_description.clear();
        j_StartingPrice.clear();
        j_artist.clear();
        j_brand.clear();
        j_model.clear();
        j_manufacturer.clear();
        j_year.clear();
        selectedImagePath = null;
        MyImgView.setImage(null);
    }
}
