package com.musiclibrary.ui.menu;

import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.RepeatMode;
import com.musiclibrary.theme.AppTheme;
import com.musiclibrary.theme.ThemeManager;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/**
 * Modular application menu bar providing hierarchical menus (File, Edit, View,
 * Controls, Account, Help) and action dispatch callbacks.
 */
public class AppMenuBar extends JMenuBar {

    // File Menu
    private final JMenu fileMenu;
    private final JMenuItem addFileItem;
    private final JMenuItem addFolderItem;
    private final JMenuItem rescanLibraryItem;
    private final JMenuItem rescanItem;
    private final JMenuItem exitItem;

    // Edit Menu
    private final JMenu editMenu;
    private final JMenuItem editTagsItem;

    // View Menu
    private final JMenu viewMenu;
    private final JMenu themeSubMenu;
    private final JRadioButtonMenuItem darkThemeItem;
    private final JRadioButtonMenuItem lightThemeItem;
    private final JRadioButtonMenuItem systemThemeItem;
    private final JMenu languageSubMenu;
    private final JRadioButtonMenuItem enLanguageItem;
    private final JRadioButtonMenuItem jaLanguageItem;
    private final JRadioButtonMenuItem systemLanguageItem;
    private final JMenuItem miniPlayerItem;

    // Controls Menu
    private final JMenu controlsMenu;
    private final JMenuItem playItem;
    private final JMenuItem pauseItem;
    private final JMenuItem nextItem;
    private final JMenuItem prevItem;
    private final JCheckBoxMenuItem shuffleMenuItem;
    private final JMenu repeatSubMenu;
    private final JRadioButtonMenuItem repeatOffItem;
    private final JRadioButtonMenuItem repeatAllItem;
    private final JRadioButtonMenuItem repeatOneItem;

    // Account Menu
    private final JMenu accountMenu;
    private final JMenuItem switchAccountItem;

    // Help Menu
    private final JMenu helpMenu;

    // Action Callbacks
    private Runnable onAddFileAction;
    private Runnable onAddFolderAction;
    private Runnable onRescanLibraryAction;
    private Runnable onRescanMissingAction;
    private Runnable onExitAction = () -> System.exit(0);
    private Runnable onEditTagsAction;
    private Runnable onMiniPlayerAction;
    private Runnable onPlayAction;
    private Runnable onPauseAction;
    private Runnable onNextAction;
    private Runnable onPrevAction;
    private Consumer<Boolean> onShuffleAction;
    private Consumer<RepeatMode> onRepeatModeAction;
    private Runnable onSwitchAccountAction;

    public AppMenuBar() {
        // 1. File Menu
        fileMenu = new JMenu();
        addFileItem = new JMenuItem();
        addFileItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK));
        addFileItem.addActionListener(e -> {
            if (onAddFileAction != null) {
                onAddFileAction.run();
            }
        });

        addFolderItem = new JMenuItem();
        addFolderItem.addActionListener(e -> {
            if (onAddFolderAction != null) {
                onAddFolderAction.run();
            }
        });

        rescanLibraryItem = new JMenuItem();
        rescanLibraryItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK));
        rescanLibraryItem.addActionListener(e -> {
            if (onRescanLibraryAction != null) {
                onRescanLibraryAction.run();
            }
        });

        rescanItem = new JMenuItem();
        rescanItem.addActionListener(e -> {
            if (onRescanMissingAction != null) {
                onRescanMissingAction.run();
            }
        });

        exitItem = new JMenuItem();
        exitItem.addActionListener(e -> {
            if (onExitAction != null) {
                onExitAction.run();
            }
        });

        fileMenu.add(addFileItem);
        fileMenu.add(addFolderItem);
        fileMenu.add(rescanLibraryItem);
        fileMenu.addSeparator();
        fileMenu.add(rescanItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        add(fileMenu);

        // 2. Edit Menu
        editMenu = new JMenu();
        editTagsItem = new JMenuItem();
        editTagsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, KeyEvent.CTRL_DOWN_MASK));
        editTagsItem.addActionListener(e -> {
            if (onEditTagsAction != null) {
                onEditTagsAction.run();
            }
        });
        editMenu.add(editTagsItem);
        add(editMenu);

        // 3. View Menu
        viewMenu = new JMenu();

        themeSubMenu = new JMenu();
        ButtonGroup themeGroup = new ButtonGroup();
        darkThemeItem = new JRadioButtonMenuItem();
        darkThemeItem.addActionListener(e -> ThemeManager.getInstance().applyTheme(AppTheme.DARK));
        lightThemeItem = new JRadioButtonMenuItem();
        lightThemeItem.addActionListener(e -> ThemeManager.getInstance().applyTheme(AppTheme.LIGHT));
        systemThemeItem = new JRadioButtonMenuItem();
        systemThemeItem.addActionListener(e -> ThemeManager.getInstance().applyTheme(AppTheme.SYSTEM));

        themeGroup.add(darkThemeItem);
        themeGroup.add(lightThemeItem);
        themeGroup.add(systemThemeItem);
        themeSubMenu.add(darkThemeItem);
        themeSubMenu.add(lightThemeItem);
        themeSubMenu.add(systemThemeItem);

        languageSubMenu = new JMenu();
        ButtonGroup languageGroup = new ButtonGroup();
        enLanguageItem = new JRadioButtonMenuItem("English");
        enLanguageItem.addActionListener(e -> I18nManager.getInstance().setLocale(AppLocale.ENGLISH));
        jaLanguageItem = new JRadioButtonMenuItem("日本語");
        jaLanguageItem.addActionListener(e -> I18nManager.getInstance().setLocale(AppLocale.JAPANESE));
        systemLanguageItem = new JRadioButtonMenuItem("System Default");
        systemLanguageItem.addActionListener(e -> I18nManager.getInstance().setLocale(AppLocale.SYSTEM));

        languageGroup.add(enLanguageItem);
        languageGroup.add(jaLanguageItem);
        languageGroup.add(systemLanguageItem);
        languageSubMenu.add(enLanguageItem);
        languageSubMenu.add(jaLanguageItem);
        languageSubMenu.add(systemLanguageItem);

        miniPlayerItem = new JMenuItem();
        miniPlayerItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK));
        miniPlayerItem.addActionListener(e -> {
            if (onMiniPlayerAction != null) {
                onMiniPlayerAction.run();
            }
        });

        viewMenu.add(themeSubMenu);
        viewMenu.add(languageSubMenu);
        viewMenu.addSeparator();
        viewMenu.add(miniPlayerItem);
        add(viewMenu);

        // 4. Controls Menu
        controlsMenu = new JMenu();
        playItem = new JMenuItem();
        playItem.addActionListener(e -> {
            if (onPlayAction != null) {
                onPlayAction.run();
            }
        });

        pauseItem = new JMenuItem();
        pauseItem.addActionListener(e -> {
            if (onPauseAction != null) {
                onPauseAction.run();
            }
        });

        nextItem = new JMenuItem();
        nextItem.addActionListener(e -> {
            if (onNextAction != null) {
                onNextAction.run();
            }
        });

        prevItem = new JMenuItem();
        prevItem.addActionListener(e -> {
            if (onPrevAction != null) {
                onPrevAction.run();
            }
        });

        shuffleMenuItem = new JCheckBoxMenuItem();
        shuffleMenuItem.addActionListener(e -> {
            if (onShuffleAction != null) {
                onShuffleAction.accept(shuffleMenuItem.isSelected());
            }
        });

        repeatSubMenu = new JMenu();
        ButtonGroup repeatGroup = new ButtonGroup();
        repeatOffItem = new JRadioButtonMenuItem();
        repeatOffItem.addActionListener(e -> {
            if (onRepeatModeAction != null) {
                onRepeatModeAction.accept(RepeatMode.OFF);
            }
        });
        repeatAllItem = new JRadioButtonMenuItem();
        repeatAllItem.addActionListener(e -> {
            if (onRepeatModeAction != null) {
                onRepeatModeAction.accept(RepeatMode.ALL);
            }
        });
        repeatOneItem = new JRadioButtonMenuItem();
        repeatOneItem.addActionListener(e -> {
            if (onRepeatModeAction != null) {
                onRepeatModeAction.accept(RepeatMode.ONE);
            }
        });

        repeatGroup.add(repeatOffItem);
        repeatGroup.add(repeatAllItem);
        repeatGroup.add(repeatOneItem);
        repeatSubMenu.add(repeatOffItem);
        repeatSubMenu.add(repeatAllItem);
        repeatSubMenu.add(repeatOneItem);

        controlsMenu.add(playItem);
        controlsMenu.add(pauseItem);
        controlsMenu.addSeparator();
        controlsMenu.add(nextItem);
        controlsMenu.add(prevItem);
        controlsMenu.addSeparator();
        controlsMenu.add(shuffleMenuItem);
        controlsMenu.add(repeatSubMenu);
        add(controlsMenu);

        // 5. Account Menu
        accountMenu = new JMenu();
        switchAccountItem = new JMenuItem();
        switchAccountItem.addActionListener(e -> {
            if (onSwitchAccountAction != null) {
                onSwitchAccountAction.run();
            }
        });
        accountMenu.add(switchAccountItem);
        add(accountMenu);

        // 6. Help Menu
        helpMenu = new JMenu();
        add(helpMenu);

        // Initialize display texts & state
        updateLanguage();
        updateThemeSelection();
        updateLanguageSelection();
        updateRepeatMode(RepeatMode.OFF);
    }

    /**
     * Updates all localized texts across the menu items using I18nManager.
     */
    public void updateLanguage() {
        I18nManager i18n = I18nManager.getInstance();

        fileMenu.setText(i18n.get("menu.file"));
        addFileItem.setText(i18n.get("menu.file.add_file"));
        addFolderItem.setText(i18n.get("menu.file.add_folder"));
        rescanLibraryItem.setText(i18n.get("menu.file.rescan_library"));
        rescanItem.setText(i18n.get("menu.file.rescan_missing"));
        exitItem.setText(i18n.get("menu.file.exit"));

        editMenu.setText(i18n.get("menu.edit"));
        editTagsItem.setText(i18n.get("menu.edit.edit_tags"));

        viewMenu.setText(i18n.get("menu.view"));
        themeSubMenu.setText(i18n.get("menu.view.theme"));
        darkThemeItem.setText(i18n.get("menu.view.theme.dark"));
        lightThemeItem.setText(i18n.get("menu.view.theme.light"));
        systemThemeItem.setText(i18n.get("menu.view.theme.system"));
        languageSubMenu.setText(i18n.get("menu.view.language"));
        miniPlayerItem.setText(i18n.get("menu.view.mini_player"));

        controlsMenu.setText(i18n.get("menu.controls"));
        playItem.setText(i18n.get("menu.controls.play"));
        pauseItem.setText(i18n.get("menu.controls.pause"));
        nextItem.setText(i18n.get("menu.controls.next"));
        prevItem.setText(i18n.get("menu.controls.previous"));
        shuffleMenuItem.setText(i18n.get("menu.controls.shuffle"));
        repeatSubMenu.setText(i18n.get("menu.controls.repeat"));
        repeatOffItem.setText(i18n.get("menu.controls.repeat_off"));
        repeatAllItem.setText(i18n.get("menu.controls.repeat_all"));
        repeatOneItem.setText(i18n.get("menu.controls.repeat_one"));

        if (accountMenu != null) {
            accountMenu.setText(i18n.get("menu.account"));
        }
        if (switchAccountItem != null) {
            switchAccountItem.setText(i18n.get("menu.account.switch"));
        }

        helpMenu.setText(i18n.get("menu.help"));
    }

    /**
     * Updates theme radio button selection based on current ThemeManager setting.
     */
    public void updateThemeSelection() {
        updateThemeSelection(ThemeManager.getInstance().getCurrentTheme());
    }

    /**
     * Updates theme radio button selection according to the specified theme.
     *
     * @param theme active application theme
     */
    public void updateThemeSelection(AppTheme theme) {
        if (theme == AppTheme.DARK) {
            darkThemeItem.setSelected(true);
        } else if (theme == AppTheme.LIGHT) {
            lightThemeItem.setSelected(true);
        } else {
            systemThemeItem.setSelected(true);
        }
    }

    /**
     * Updates language radio button selection based on current I18nManager setting.
     */
    public void updateLanguageSelection() {
        updateLanguageSelection(I18nManager.getInstance().getCurrentLocale());
    }

    /**
     * Updates language radio button selection according to the specified locale.
     *
     * @param locale active application locale
     */
    public void updateLanguageSelection(AppLocale locale) {
        if (locale == AppLocale.ENGLISH) {
            enLanguageItem.setSelected(true);
        } else if (locale == AppLocale.JAPANESE) {
            jaLanguageItem.setSelected(true);
        } else {
            systemLanguageItem.setSelected(true);
        }
    }

    /**
     * Updates repeat mode radio button selection.
     *
     * @param mode active repeat mode
     */
    public void updateRepeatMode(RepeatMode mode) {
        if (mode == RepeatMode.ALL) {
            repeatAllItem.setSelected(true);
        } else if (mode == RepeatMode.ONE) {
            repeatOneItem.setSelected(true);
        } else {
            repeatOffItem.setSelected(true);
        }
    }

    /**
     * Updates shuffle checkbox selection.
     *
     * @param shuffle true if shuffle is enabled
     */
    public void setShuffleSelected(boolean shuffle) {
        shuffleMenuItem.setSelected(shuffle);
    }

    // --- Action Callback Setters ---

    public void setOnAddFileAction(Runnable onAddFileAction) {
        this.onAddFileAction = onAddFileAction;
    }

    public void setOnAddFolderAction(Runnable onAddFolderAction) {
        this.onAddFolderAction = onAddFolderAction;
    }

    public void setOnRescanLibraryAction(Runnable onRescanLibraryAction) {
        this.onRescanLibraryAction = onRescanLibraryAction;
    }

    public void setOnRescanMissingAction(Runnable onRescanMissingAction) {
        this.onRescanMissingAction = onRescanMissingAction;
    }

    public void setOnExitAction(Runnable onExitAction) {
        this.onExitAction = onExitAction;
    }

    public void setOnEditTagsAction(Runnable onEditTagsAction) {
        this.onEditTagsAction = onEditTagsAction;
    }

    public void setOnMiniPlayerAction(Runnable onMiniPlayerAction) {
        this.onMiniPlayerAction = onMiniPlayerAction;
    }

    public void setOnPlayAction(Runnable onPlayAction) {
        this.onPlayAction = onPlayAction;
    }

    public void setOnPauseAction(Runnable onPauseAction) {
        this.onPauseAction = onPauseAction;
    }

    public void setOnNextAction(Runnable onNextAction) {
        this.onNextAction = onNextAction;
    }

    public void setOnPrevAction(Runnable onPrevAction) {
        this.onPrevAction = onPrevAction;
    }

    public void setOnShuffleAction(Consumer<Boolean> onShuffleAction) {
        this.onShuffleAction = onShuffleAction;
    }

    public void setOnRepeatModeAction(Consumer<RepeatMode> onRepeatModeAction) {
        this.onRepeatModeAction = onRepeatModeAction;
    }

    public void setOnSwitchAccountAction(Runnable onSwitchAccountAction) {
        this.onSwitchAccountAction = onSwitchAccountAction;
    }

    // --- Component Getters ---

    public JMenu getFileMenu() {
        return fileMenu;
    }

    public JMenuItem getAddFileItem() {
        return addFileItem;
    }

    public JMenuItem getAddFolderItem() {
        return addFolderItem;
    }

    public JMenuItem getRescanLibraryItem() {
        return rescanLibraryItem;
    }

    public JMenuItem getRescanItem() {
        return rescanItem;
    }

    public JMenuItem getExitItem() {
        return exitItem;
    }

    public JMenu getEditMenu() {
        return editMenu;
    }

    public JMenuItem getEditTagsItem() {
        return editTagsItem;
    }

    public JMenu getViewMenu() {
        return viewMenu;
    }

    public JMenu getThemeSubMenu() {
        return themeSubMenu;
    }

    public JRadioButtonMenuItem getDarkThemeItem() {
        return darkThemeItem;
    }

    public JRadioButtonMenuItem getLightThemeItem() {
        return lightThemeItem;
    }

    public JRadioButtonMenuItem getSystemThemeItem() {
        return systemThemeItem;
    }

    public JMenu getLanguageSubMenu() {
        return languageSubMenu;
    }

    public JRadioButtonMenuItem getEnLanguageItem() {
        return enLanguageItem;
    }

    public JRadioButtonMenuItem getJaLanguageItem() {
        return jaLanguageItem;
    }

    public JRadioButtonMenuItem getSystemLanguageItem() {
        return systemLanguageItem;
    }

    public JMenuItem getMiniPlayerItem() {
        return miniPlayerItem;
    }

    public JMenu getControlsMenu() {
        return controlsMenu;
    }

    public JMenuItem getPlayItem() {
        return playItem;
    }

    public JMenuItem getPauseItem() {
        return pauseItem;
    }

    public JMenuItem getNextItem() {
        return nextItem;
    }

    public JMenuItem getPrevItem() {
        return prevItem;
    }

    public JCheckBoxMenuItem getShuffleMenuItem() {
        return shuffleMenuItem;
    }

    public JMenu getRepeatSubMenu() {
        return repeatSubMenu;
    }

    public JRadioButtonMenuItem getRepeatOffItem() {
        return repeatOffItem;
    }

    public JRadioButtonMenuItem getRepeatAllItem() {
        return repeatAllItem;
    }

    public JRadioButtonMenuItem getRepeatOneItem() {
        return repeatOneItem;
    }

    public JMenu getAccountMenu() {
        return accountMenu;
    }

    public JMenuItem getSwitchAccountItem() {
        return switchAccountItem;
    }

    public JMenu getHelpMenu() {
        return helpMenu;
    }
}
