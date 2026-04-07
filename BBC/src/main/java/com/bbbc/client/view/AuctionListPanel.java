package com.bbbc.client.view;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AuctionListPanel extends JPanel {
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Auction ID", "Item", "Type", "Current Price", "State", "Ends At"},
            0
    );
    private final JTable table = new JTable(tableModel);
    private final List<String> auctionIds = new ArrayList<>();
    private final JButton sellerButton = new JButton("Seller Management");

    public AuctionListPanel(MainFrame frame) {
        setLayout(new BorderLayout(12, 12));
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel actions = new JPanel();
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(event -> frame.loadAuctions());
        actions.add(refreshButton);

        JButton detailButton = new JButton("Open Detail");
        detailButton.addActionListener(event -> {
            String auctionId = getSelectedAuctionId();
            if (auctionId != null) {
                frame.showAuctionDetail(auctionId);
            }
        });
        actions.add(detailButton);

        sellerButton.addActionListener(event -> frame.showSellerPanel());
        actions.add(sellerButton);
        add(actions, BorderLayout.SOUTH);
    }

    public void setAuctions(List<Map<String, Object>> auctions) {
        tableModel.setRowCount(0);
        auctionIds.clear();
        for (Map<String, Object> auction : auctions) {
            auctionIds.add(String.valueOf(auction.get("auctionId")));
            tableModel.addRow(new Object[]{
                    auction.get("auctionId"),
                    auction.get("itemName"),
                    auction.get("itemType"),
                    auction.get("currentPrice"),
                    auction.get("state"),
                    auction.get("endsAt")
            });
        }
    }

    public void setSellerEnabled(boolean enabled) {
        sellerButton.setEnabled(enabled);
    }

    private String getSelectedAuctionId() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= auctionIds.size()) {
            return null;
        }
        return auctionIds.get(row);
    }
}
