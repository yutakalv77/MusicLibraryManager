package com.musiclibrary.ui.dialog;

import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Account;
import com.musiclibrary.repository.AccountRepository;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Modal dialog for managing and switching user accounts/profiles.
 */
public class AccountManagerDialog extends JDialog {

    private final AccountRepository accountRepository;
    private final String currentAccountId;
    private final Consumer<Account> onAccountSelected;

    private final DefaultListModel<Account> listModel;
    private final JList<Account> accountList;
    private final JButton switchButton;
    private final JButton deleteButton;

    public AccountManagerDialog(Frame owner, AccountRepository accountRepository, String currentAccountId, Consumer<Account> onAccountSelected) {
        super(owner, I18nManager.getText("dialog.account.title", "Account Manager"), true);
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
        this.currentAccountId = currentAccountId != null ? currentAccountId : Account.DEFAULT_ACCOUNT_ID;
        this.onAccountSelected = onAccountSelected;

        setSize(420, 320);
        setMinimumSize(new Dimension(360, 260));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        JPanel contentPanel = new JPanel(new BorderLayout(8, 8));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Header Label
        JLabel headerLabel = new JLabel(I18nManager.getText("dialog.account.title", "Accounts"));
        Font hf = headerLabel.getFont();
        if (hf != null) {
            headerLabel.setFont(hf.deriveFont(Font.BOLD, 14f));
        }
        contentPanel.add(headerLabel, BorderLayout.NORTH);

        // Account List
        listModel = new DefaultListModel<>();
        accountList = new JList<>(listModel);
        accountList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        accountList.setCellRenderer(new AccountCellRenderer());

        JScrollPane scrollPane = new JScrollPane(accountList);
        contentPanel.add(scrollPane, BorderLayout.CENTER);

        // Right-side Action Panel
        JPanel actionPanel = new JPanel();
        actionPanel.setLayout(new BoxLayout(actionPanel, BoxLayout.Y_AXIS));

        switchButton = new JButton(I18nManager.getText("dialog.account.switch_btn", "Switch"));
        switchButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, switchButton.getPreferredSize().height));
        switchButton.addActionListener(e -> handleSwitch());

        JButton newButton = new JButton(I18nManager.getText("dialog.account.new", "New Account..."));
        newButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, newButton.getPreferredSize().height));
        newButton.addActionListener(e -> handleNewAccount());

        deleteButton = new JButton(I18nManager.getText("dialog.account.delete_btn", "Delete"));
        deleteButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, deleteButton.getPreferredSize().height));
        deleteButton.addActionListener(e -> handleDeleteAccount());

        actionPanel.add(switchButton);
        actionPanel.add(Box.createVerticalStrut(8));
        actionPanel.add(newButton);
        actionPanel.add(Box.createVerticalStrut(8));
        actionPanel.add(deleteButton);
        actionPanel.add(Box.createVerticalGlue());

        contentPanel.add(actionPanel, BorderLayout.EAST);
        add(contentPanel, BorderLayout.CENTER);

        // Bottom Panel
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton(I18nManager.getText("tageditor.btn.cancel", "Close"));
        closeButton.addActionListener(e -> dispose());
        bottomPanel.add(closeButton);
        add(bottomPanel, BorderLayout.SOUTH);

        // Selection Listener
        accountList.addListSelectionListener(e -> updateButtonStates());

        loadAccounts();
    }

    private void loadAccounts() {
        listModel.clear();
        List<Account> accounts = accountRepository.findAll();
        Account selectedAcc = null;
        for (Account a : accounts) {
            listModel.addElement(a);
            if (a.getId().equals(currentAccountId)) {
                selectedAcc = a;
            }
        }
        if (selectedAcc != null) {
            accountList.setSelectedValue(selectedAcc, true);
        } else if (!listModel.isEmpty()) {
            accountList.setSelectedIndex(0);
        }
        updateButtonStates();
    }

    private void updateButtonStates() {
        Account selected = accountList.getSelectedValue();
        boolean hasSelection = selected != null;
        switchButton.setEnabled(hasSelection && !selected.getId().equals(currentAccountId));
        deleteButton.setEnabled(hasSelection && !selected.getId().equals(Account.DEFAULT_ACCOUNT_ID)
                && !selected.getId().equals(currentAccountId));
    }

    private void handleSwitch() {
        Account selected = accountList.getSelectedValue();
        if (selected != null && onAccountSelected != null) {
            onAccountSelected.accept(selected);
            dispose();
        }
    }

    private void handleNewAccount() {
        String name = JOptionPane.showInputDialog(
                this,
                I18nManager.getText("dialog.account.name", "Account Name:"),
                I18nManager.getText("dialog.account.new", "New Account"),
                JOptionPane.PLAIN_MESSAGE
        );

        if (name != null && !name.trim().isEmpty()) {
            Account account = new Account(UUID.randomUUID().toString(), name.trim());
            accountRepository.save(account);
            loadAccounts();
            accountList.setSelectedValue(account, true);
        }
    }

    private void handleDeleteAccount() {
        Account selected = accountList.getSelectedValue();
        if (selected == null) {
            return;
        }

        String messageTemplate = I18nManager.getText("dialog.account.delete_confirm",
                "Are you sure you want to delete account \"{0}\"? All its playlist and user data will be permanently deleted.");
        String message = MessageFormat.format(messageTemplate, selected.getName());

        int confirm = JOptionPane.showConfirmDialog(
                this,
                message,
                I18nManager.getText("dialog.account.delete_btn", "Delete"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            accountRepository.deleteById(selected.getId());
            loadAccounts();
        }
    }

    private class AccountCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Account acc) {
                boolean isActive = acc.getId().equals(currentAccountId);
                if (isActive) {
                    setText(acc.getName() + I18nManager.getText("dialog.account.active_suffix", " (Active)"));
                    setFont(getFont().deriveFont(Font.BOLD));
                } else {
                    setText(acc.getName());
                    setFont(getFont().deriveFont(Font.PLAIN));
                }
            }
            setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            return this;
        }
    }

    public JList<Account> getAccountList() {
        return accountList;
    }

    public JButton getSwitchButton() {
        return switchButton;
    }

    public JButton getDeleteButton() {
        return deleteButton;
    }
}
