package du_an_lon;

import java.time.Instant;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.util.Duration;
import model.Items.Item;
import model.Items.ItemSession;
import model.User.Bidder;
import model.User.User;
import service.AppServices;
import service.AuctionService;
import service.ProfileService;
import service.dto.AuctionDetailView;

public class ControllerAuction {
    private final AuctionService auctionService = AppServices.auctionService();
    private final ProfileService profileService = AppServices.profileService();

    private Timeline countdownTimeline;

    @FXML
    private TextField j_setPrice;

    @FXML
    private Button j_apply;

    @FXML
    private Label j_leadingBidder;

    @FXML
    private AnchorPane Pane1;

    @FXML
    private Label j_LabelName;

    @FXML
    private Label j_days;

    @FXML
    private Label j_description;

    @FXML
    private ImageView j_image;

    @FXML
    private ImageView j_img;

    @FXML
    private Label j_name;

    @FXML
    private Button j_return;

    @FXML
    private Label j_textSoDu;

    @FXML
    private Label j_CurrentPrice;

    @FXML
    private Label j_notified;

    @FXML
    private Label j_mins;

    @FXML
    private Label j_hours;

    @FXML
    private Label j_secs;

    public void initialize() {
        User user = profileService.getCurrentUserOrNull();
        if (user != null) {
            j_LabelName.setText(user.getFullName());
        }
        j_textSoDu.setText("--");
        j_notified.setVisible(false);
        refreshScreen();
    }

    @FXML
    void On_apply(ActionEvent event) {
        j_notified.setVisible(false);
        try {
            User user = profileService.requireCurrentUser();
            if (!(user instanceof Bidder bidder)) {
                throw new IllegalStateException("Only bidder can place a bid.");
            }

            Item item = ItemSession.getLoggedInItem();
            auctionService.placeBid(item, bidder, j_setPrice.getText());
            j_setPrice.clear();
            refreshScreen();
            FxViewUtils.showSuccess(j_notified, "Dat gia thanh cong.");
        } catch (RuntimeException exception) {
            FxViewUtils.showError(j_notified, exception.getMessage());
        }
    }

    @FXML
    void On_MouseClickImg(javafx.scene.input.MouseEvent event) {
        SceneHelper.changeScene((javafx.scene.Node) event.getSource(), "View5.fxml");
    }

    @FXML
    void On_Return(ActionEvent event) {
        stopCountdown();
        ItemSession.cleanItemSession();
        SceneHelper.changeScene(j_return, profileService.homeView(profileService.requireCurrentUser()));
    }

    private void refreshScreen() {
        Item selectedItem = ItemSession.getLoggedInItem();
        if (selectedItem == null) {
            j_name.setText("No selected item");
            disableBidding(true);
            stopCountdown();
            updateTimerLabels(0);
            return;
        }

        AuctionDetailView detailView = auctionService.getAuctionDetailByItem(selectedItem);
        Item item = detailView.auction().getItem();
        j_name.setText(item.getName());
        j_description.setText(item.getDescription());
        j_CurrentPrice.setText(String.format("%,.0f VND", item.getCurrentHighestPrice()));
        if (detailView.auction().getLeadingBidder() == null) {
            j_leadingBidder.setText("Chua co");
        } else {
            j_leadingBidder.setText(detailView.auction().getLeadingBidder().getUsername());
        }
        FxViewUtils.loadImage(j_img, detailView.imagePath());
        disableBidding(detailView.auction().getStatus().name().matches("FINISHED|PAID|CANCELED|OPEN"));
        long remainingSeconds = java.time.Duration.between(Instant.now(), item.getEndTime()).getSeconds();
        if (remainingSeconds > 0) {
            startCountdown(item.getEndTime());
        } else {
            stopCountdown();
            updateTimerLabels(0);
        }
    }

    private void disableBidding(boolean disabled) {
        j_setPrice.setDisable(disabled);
        j_apply.setDisable(disabled);
    }

    private void startCountdown(Instant endTime) {
        stopCountdown();
        updateRemainingTime(endTime);
        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateRemainingTime(endTime)));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    private void updateRemainingTime(Instant endTime) {
        long remainingSeconds = java.time.Duration.between(Instant.now(), endTime).getSeconds();
        if (remainingSeconds <= 0) {
            updateTimerLabels(0);
            stopCountdown();
            return;
        }
        updateTimerLabels(remainingSeconds);
    }

    private void stopCountdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
    }

    private void updateTimerLabels(long totalSeconds) {
        long safeSeconds = Math.max(totalSeconds, 0);
        long d = safeSeconds / 86400;
        long h = (safeSeconds % 86400) / 3600;
        long m = (safeSeconds % 3600) / 60;
        long s = safeSeconds % 60;

        j_days.setText(String.format("%02d", d));
        j_hours.setText(String.format("%02d", h));
        j_mins.setText(String.format("%02d", m));
        j_secs.setText(String.format("%02d", s));
    }
}
