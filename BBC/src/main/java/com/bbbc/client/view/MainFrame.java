package com.bbbc.client.view;

import com.bbbc.client.controller.AuctionDetailController;
import com.bbbc.client.controller.AuctionListController;
import com.bbbc.client.controller.LoginController;
import com.bbbc.client.controller.SellerProductController;
import com.bbbc.client.model.AuctionClient;
import com.bbbc.client.model.ClientSession;
import com.bbbc.common.message.ApiMessage;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class MainFrame extends JFrame {
    private static final String LOGIN_CARD = "LOGIN";
    private static final String LIST_CARD = "LIST";
    private static final String DETAIL_CARD = "DETAIL";
    private static final String SELLER_CARD = "SELLER";

    private final CardLayout cardLayout = new CardLayout();
    private final AuctionClient auctionClient;
    private final LoginPanel loginPanel;
    private final AuctionListPanel auctionListPanel;
    private final AuctionDetailPanel auctionDetailPanel;
    private final SellerProductPanel sellerProductPanel;
    private final LoginController loginController;

    private ClientSession session;
    private AuctionListController auctionListController;
    private AuctionDetailController auctionDetailController;
    private SellerProductController sellerProductController;
    private String currentAuctionId;
    private final Consumer<ApiMessage> eventListener;

    public MainFrame(String host, int port) throws IOException {
        super("BBBC Auction Client");
        this.auctionClient = new AuctionClient(host, port);
        this.loginController = new LoginController(auctionClient);
        this.loginPanel = new LoginPanel(this);
        this.auctionListPanel = new AuctionListPanel(this);
        this.auctionDetailPanel = new AuctionDetailPanel(this);
        this.sellerProductPanel = new SellerProductPanel(this);

        setLayout(cardLayout);
        add(loginPanel, LOGIN_CARD);
        add(auctionListPanel, LIST_CARD);
        add(auctionDetailPanel, DETAIL_CARD);
        add(sellerProductPanel, SELLER_CARD);

        this.eventListener = message -> {
            if (!"AUCTION_UPDATED".equals(message.getAction())) {
                return;
            }
            Object auctionId = message.getPayload().get("auctionId");
            if (auctionId == null || !String.valueOf(auctionId).equals(currentAuctionId)) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                auctionDetailPanel.appendEvent(String.valueOf(message.getPayload().get("message")));
                reloadCurrentAuction();
            });
        };
        auctionClient.addEventListener(eventListener);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 700);
        setLocationRelativeTo(null);
        cardLayout.show(getContentPane(), LOGIN_CARD);
    }

    public void handleLogin(String username, String password) {
        try {
            session = loginController.login(username, password);
            auctionListController = new AuctionListController(auctionClient, session);
            auctionDetailController = new AuctionDetailController(auctionClient, session);
            sellerProductController = new SellerProductController(auctionClient, session);
            auctionListPanel.setSellerEnabled(session.isSellerOrAdmin());
            loadAuctions();
            cardLayout.show(getContentPane(), LIST_CARD);
        } catch (Exception exception) {
            showError(exception.getMessage());
        }
    }

    public void handleRegister(String username, String email, String password, String role) {
        try {
            loginController.register(username, email, password, role);
            JOptionPane.showMessageDialog(this, "Registration successful. You can login now.");
        } catch (Exception exception) {
            showError(exception.getMessage());
        }
    }

    public void loadAuctions() {
        try {
            auctionListPanel.setAuctions(auctionListController.loadAuctions());
        } catch (Exception exception) {
            showError(exception.getMessage());
        }
    }

    public void showAuctionDetail(String auctionId) {
        try {
            if (currentAuctionId != null) {
                auctionDetailController.unsubscribe(currentAuctionId);
            }
            currentAuctionId = auctionId;
            auctionDetailController.subscribe(auctionId);
            auctionDetailPanel.clearEvents();
            auctionDetailPanel.showAuction(auctionDetailController.loadAuction(auctionId));
            cardLayout.show(getContentPane(), DETAIL_CARD);
        } catch (Exception exception) {
            showError(exception.getMessage());
        }
    }

    public void showAuctionList() {
        try {
            if (currentAuctionId != null && auctionDetailController != null) {
                auctionDetailController.unsubscribe(currentAuctionId);
                currentAuctionId = null;
            }
        } catch (Exception ignored) {
        }
        loadAuctions();
        cardLayout.show(getContentPane(), LIST_CARD);
    }

    public void showSellerPanel() {
        cardLayout.show(getContentPane(), SELLER_CARD);
    }

    public void reloadCurrentAuction() {
        if (currentAuctionId == null) {
            return;
        }
        try {
            auctionDetailPanel.showAuction(auctionDetailController.loadAuction(currentAuctionId));
        } catch (Exception exception) {
            showError(exception.getMessage());
        }
    }

    public void placeBid(String amountText) {
        try {
            auctionDetailPanel.showAuction(auctionDetailController.placeBid(currentAuctionId, Double.parseDouble(amountText)));
        } catch (Exception exception) {
            showError(exception.getMessage());
        }
    }

    public void registerAutoBid(String maxBidText, String incrementText) {
        try {
            auctionDetailPanel.showAuction(auctionDetailController.registerAutoBid(
                    currentAuctionId,
                    Double.parseDouble(maxBidText),
                    Double.parseDouble(incrementText)
            ));
        } catch (Exception exception) {
            showError(exception.getMessage());
        }
    }

    public void createAuction(
            String itemType,
            String name,
            String description,
            String startingPriceText,
            String startTime,
            String endTime,
            String attrOneKey,
            String attrOneValue,
            String attrTwoKey,
            String attrTwoValue
    ) {
        try {
            Map<String, Object> attributes = new LinkedHashMap<>();
            if (!attrOneKey.isBlank() && !attrOneValue.isBlank()) {
                attributes.put(attrOneKey, attrOneValue);
            }
            if (!attrTwoKey.isBlank() && !attrTwoValue.isBlank()) {
                attributes.put(attrTwoKey, attrTwoValue);
            }
            sellerProductController.createAuction(
                    itemType,
                    name,
                    description,
                    Double.parseDouble(startingPriceText),
                    startTime,
                    endTime,
                    attributes
            );
            JOptionPane.showMessageDialog(this, "Auction created.");
            showAuctionList();
        } catch (Exception exception) {
            showError(exception.getMessage());
        }
    }

    public void deleteAuction(String auctionId) {
        try {
            sellerProductController.deleteAuction(auctionId);
            JOptionPane.showMessageDialog(this, "Auction deleted.");
            showAuctionList();
        } catch (Exception exception) {
            showError(exception.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
