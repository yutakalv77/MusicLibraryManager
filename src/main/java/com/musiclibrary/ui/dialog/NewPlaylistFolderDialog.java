package com.musiclibrary.ui.dialog;

import com.musiclibrary.i18n.I18nManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.function.Consumer;

/**
 * Modal dialog for creating a new playlist folder.
 */
public class NewPlaylistFolderDialog extends JDialog {

    private final JTextField folderNameField;
    private final JButton cancelButton;
    private final JButton okButton;
    private final Consumer<String> onFolderCreated;

    public NewPlaylistFolderDialog(Frame owner, Consumer<String> onFolderCreated) {
        super(owner, I18nManager.getText("dialog.folder.title", "New Playlist Folder"), true);
        this.onFolderCreated = onFolderCreated;

        setSize(360, 160);
        setResizable(false);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(14, 16, 10, 16));

        JLabel label = new JLabel(I18nManager.getText("dialog.folder.name", "Folder Name:"));
        label.setAlignmentX(LEFT_ALIGNMENT);
        contentPanel.add(label);
        contentPanel.add(Box.createVerticalStrut(6));

        folderNameField = new JTextField();
        folderNameField.setAlignmentX(LEFT_ALIGNMENT);
        folderNameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        folderNameField.addActionListener(e -> handleConfirm());
        contentPanel.add(folderNameField);

        add(contentPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        cancelButton = new JButton(I18nManager.getText("tageditor.btn.cancel", "Cancel"));
        cancelButton.addActionListener(e -> dispose());

        okButton = new JButton(I18nManager.getText("tageditor.btn.save", "OK"));
        okButton.addActionListener(e -> handleConfirm());

        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);
        add(buttonPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(okButton);
    }

    private void handleConfirm() {
        String name = folderNameField.getText();
        if (name != null && !name.trim().isEmpty()) {
            if (onFolderCreated != null) {
                onFolderCreated.accept(name.trim());
            }
            dispose();
        }
    }

    public JTextField getFolderNameField() {
        return folderNameField;
    }

    public JButton getCancelButton() {
        return cancelButton;
    }

    public JButton getOkButton() {
        return okButton;
    }
}
