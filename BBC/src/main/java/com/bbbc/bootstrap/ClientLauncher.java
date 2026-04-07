package com.bbbc.bootstrap;

import com.bbbc.client.view.MainFrame;

import javax.swing.SwingUtilities;

public final class ClientLauncher {
    private ClientLauncher() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                MainFrame frame = new MainFrame("127.0.0.1", 5555);
                frame.setVisible(true);
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to start client", exception);
            }
        });
    }
}
