package com.bbbc.client.view;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public final class LoginPanel extends JPanel {
    private final JTextField usernameField = new JTextField("bidder1", 18);
    private final JTextField emailField = new JTextField("user@bbbc.local", 18);
    private final JPasswordField passwordField = new JPasswordField("bidder123", 18);
    private final JComboBox<String> roleBox = new JComboBox<>(new String[]{"BIDDER", "SELLER", "ADMIN"});

    public LoginPanel(MainFrame frame) {
        setLayout(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(8, 8, 8, 8);
        constraints.fill = GridBagConstraints.HORIZONTAL;

        addRow(constraints, 0, "Username", usernameField);
        addRow(constraints, 1, "Email", emailField);
        addRow(constraints, 2, "Password", passwordField);
        addRow(constraints, 3, "Role", roleBox);

        JButton loginButton = new JButton("Login");
        loginButton.addActionListener(event ->
                frame.handleLogin(usernameField.getText().trim(), new String(passwordField.getPassword())));
        constraints.gridx = 0;
        constraints.gridy = 4;
        add(loginButton, constraints);

        JButton registerButton = new JButton("Register");
        registerButton.addActionListener(event ->
                frame.handleRegister(
                        usernameField.getText().trim(),
                        emailField.getText().trim(),
                        new String(passwordField.getPassword()),
                        String.valueOf(roleBox.getSelectedItem())
                ));
        constraints.gridx = 1;
        add(registerButton, constraints);
    }

    private void addRow(GridBagConstraints constraints, int row, String label, java.awt.Component component) {
        constraints.gridx = 0;
        constraints.gridy = row;
        add(new JLabel(label), constraints);
        constraints.gridx = 1;
        add(component, constraints);
    }
}
