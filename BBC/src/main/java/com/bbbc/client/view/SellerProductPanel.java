package com.bbbc.client.view;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.Instant;

public final class SellerProductPanel extends JPanel {
    private final JComboBox<String> typeBox = new JComboBox<>(new String[]{"ELECTRONICS", "ART", "VEHICLE"});
    private final JTextField nameField = new JTextField(18);
    private final JTextField descriptionField = new JTextField(18);
    private final JTextField startingPriceField = new JTextField("100", 18);
    private final JTextField startTimeField = new JTextField(Instant.now().minusSeconds(10).toString(), 18);
    private final JTextField endTimeField = new JTextField(Instant.now().plusSeconds(300).toString(), 18);
    private final JTextField attributeOneKey = new JTextField("brand", 18);
    private final JTextField attributeOneValue = new JTextField("Brand", 18);
    private final JTextField attributeTwoKey = new JTextField("model", 18);
    private final JTextField attributeTwoValue = new JTextField("Model", 18);
    private final JTextField deleteAuctionIdField = new JTextField(18);

    public SellerProductPanel(MainFrame frame) {
        setLayout(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(6, 6, 6, 6);
        constraints.fill = GridBagConstraints.HORIZONTAL;

        addRow(constraints, 0, "Item Type", typeBox);
        addRow(constraints, 1, "Name", nameField);
        addRow(constraints, 2, "Description", descriptionField);
        addRow(constraints, 3, "Starting Price", startingPriceField);
        addRow(constraints, 4, "Start Time", startTimeField);
        addRow(constraints, 5, "End Time", endTimeField);
        addRow(constraints, 6, "Attr 1 Key", attributeOneKey);
        addRow(constraints, 7, "Attr 1 Value", attributeOneValue);
        addRow(constraints, 8, "Attr 2 Key", attributeTwoKey);
        addRow(constraints, 9, "Attr 2 Value", attributeTwoValue);

        JButton createButton = new JButton("Create Auction");
        createButton.addActionListener(event -> frame.createAuction(
                String.valueOf(typeBox.getSelectedItem()),
                nameField.getText().trim(),
                descriptionField.getText().trim(),
                startingPriceField.getText().trim(),
                startTimeField.getText().trim(),
                endTimeField.getText().trim(),
                attributeOneKey.getText().trim(),
                attributeOneValue.getText().trim(),
                attributeTwoKey.getText().trim(),
                attributeTwoValue.getText().trim()
        ));
        constraints.gridx = 0;
        constraints.gridy = 10;
        add(createButton, constraints);

        addRow(constraints, 11, "Delete Auction ID", deleteAuctionIdField);
        JButton deleteButton = new JButton("Delete Auction");
        deleteButton.addActionListener(event -> frame.deleteAuction(deleteAuctionIdField.getText().trim()));
        constraints.gridx = 0;
        constraints.gridy = 12;
        add(deleteButton, constraints);

        JButton backButton = new JButton("Back");
        backButton.addActionListener(event -> frame.showAuctionList());
        constraints.gridx = 1;
        add(backButton, constraints);
    }

    private void addRow(GridBagConstraints constraints, int row, String label, java.awt.Component component) {
        constraints.gridx = 0;
        constraints.gridy = row;
        add(new JLabel(label), constraints);
        constraints.gridx = 1;
        add(component, constraints);
    }
}
