package com.musiclibrary.ui.menu;

import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.RepeatMode;
import com.musiclibrary.theme.AppTheme;
import com.musiclibrary.theme.ThemeManager;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AppMenuBarTest {

    @BeforeEach
    void setUp() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        ThemeManager.getInstance().applyTheme(AppTheme.DARK);
    }

    @Test
    @DisplayName("AppMenuBar builds correct menu hierarchy and components")
    void testMenuHierarchy() {
        AppMenuBar menuBar = new AppMenuBar();

        assertThat(menuBar.getMenuCount()).isEqualTo(6);

        // 0: File Menu
        JMenu fileMenu = menuBar.getFileMenu();
        assertThat(fileMenu).isNotNull();
        assertThat(fileMenu.getText()).isEqualTo("File");
        assertThat(fileMenu.getItemCount()).isEqualTo(7); // Add File, Add Folder, Rescan Library, Sep, Rescan Missing, Sep, Exit
        assertThat(menuBar.getAddFileItem()).isNotNull();
        assertThat(menuBar.getAddFolderItem()).isNotNull();
        assertThat(menuBar.getRescanLibraryItem()).isNotNull();
        assertThat(menuBar.getRescanItem()).isNotNull();
        assertThat(menuBar.getExitItem()).isNotNull();

        // Accelerators
        assertThat(menuBar.getAddFileItem().getAccelerator())
                .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK));
        assertThat(menuBar.getRescanLibraryItem().getAccelerator())
                .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK));

        // 1: Edit Menu
        JMenu editMenu = menuBar.getEditMenu();
        assertThat(editMenu).isNotNull();
        assertThat(editMenu.getText()).isEqualTo("Edit");
        assertThat(editMenu.getItemCount()).isEqualTo(1);
        assertThat(menuBar.getEditTagsItem()).isNotNull();
        assertThat(menuBar.getEditTagsItem().getAccelerator())
                .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_I, KeyEvent.CTRL_DOWN_MASK));

        // 2: View Menu
        JMenu viewMenu = menuBar.getViewMenu();
        assertThat(viewMenu).isNotNull();
        assertThat(viewMenu.getText()).isEqualTo("View");
        assertThat(viewMenu.getItemCount()).isEqualTo(4); // Theme sub, Lang sub, Sep, Mini Player
        assertThat(menuBar.getThemeSubMenu()).isNotNull();
        assertThat(menuBar.getThemeSubMenu().getItemCount()).isEqualTo(3);
        assertThat(menuBar.getLanguageSubMenu()).isNotNull();
        assertThat(menuBar.getLanguageSubMenu().getItemCount()).isEqualTo(3);
        assertThat(menuBar.getMiniPlayerItem()).isNotNull();
        assertThat(menuBar.getMiniPlayerItem().getAccelerator())
                .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK));

        // 3: Controls Menu
        JMenu controlsMenu = menuBar.getControlsMenu();
        assertThat(controlsMenu).isNotNull();
        assertThat(controlsMenu.getText()).isEqualTo("Controls");
        assertThat(controlsMenu.getItemCount()).isEqualTo(8); // Play, Pause, Sep, Next, Prev, Sep, Shuffle, Repeat
        assertThat(menuBar.getPlayItem()).isNotNull();
        assertThat(menuBar.getPauseItem()).isNotNull();
        assertThat(menuBar.getNextItem()).isNotNull();
        assertThat(menuBar.getPrevItem()).isNotNull();
        assertThat(menuBar.getShuffleMenuItem()).isNotNull();
        assertThat(menuBar.getRepeatSubMenu()).isNotNull();
        assertThat(menuBar.getRepeatSubMenu().getItemCount()).isEqualTo(3);
        assertThat(menuBar.getRepeatOffItem()).isNotNull();
        assertThat(menuBar.getRepeatAllItem()).isNotNull();
        assertThat(menuBar.getRepeatOneItem()).isNotNull();

        // 4: Account Menu
        JMenu accountMenu = menuBar.getAccountMenu();
        assertThat(accountMenu).isNotNull();
        assertThat(accountMenu.getText()).isEqualTo("Account");
        assertThat(accountMenu.getItemCount()).isEqualTo(1);
        assertThat(menuBar.getSwitchAccountItem()).isNotNull();

        // 5: Help Menu
        JMenu helpMenu = menuBar.getHelpMenu();
        assertThat(helpMenu).isNotNull();
        assertThat(helpMenu.getText()).isEqualTo("Help");
    }

    @Test
    @DisplayName("Action callbacks are invoked when menu items are clicked")
    void testActionCallbacks() {
        AppMenuBar menuBar = new AppMenuBar();

        AtomicBoolean addFileClicked = new AtomicBoolean(false);
        AtomicBoolean addFolderClicked = new AtomicBoolean(false);
        AtomicBoolean rescanLibClicked = new AtomicBoolean(false);
        AtomicBoolean rescanMissingClicked = new AtomicBoolean(false);
        AtomicBoolean exitClicked = new AtomicBoolean(false);
        AtomicBoolean editTagsClicked = new AtomicBoolean(false);
        AtomicBoolean miniPlayerClicked = new AtomicBoolean(false);
        AtomicBoolean playClicked = new AtomicBoolean(false);
        AtomicBoolean pauseClicked = new AtomicBoolean(false);
        AtomicBoolean nextClicked = new AtomicBoolean(false);
        AtomicBoolean prevClicked = new AtomicBoolean(false);
        AtomicReference<Boolean> shuffleState = new AtomicReference<>(null);
        AtomicReference<RepeatMode> repeatState = new AtomicReference<>(null);
        AtomicBoolean switchAccountClicked = new AtomicBoolean(false);

        menuBar.setOnAddFileAction(() -> addFileClicked.set(true));
        menuBar.setOnAddFolderAction(() -> addFolderClicked.set(true));
        menuBar.setOnRescanLibraryAction(() -> rescanLibClicked.set(true));
        menuBar.setOnRescanMissingAction(() -> rescanMissingClicked.set(true));
        menuBar.setOnExitAction(() -> exitClicked.set(true));
        menuBar.setOnEditTagsAction(() -> editTagsClicked.set(true));
        menuBar.setOnMiniPlayerAction(() -> miniPlayerClicked.set(true));
        menuBar.setOnPlayAction(() -> playClicked.set(true));
        menuBar.setOnPauseAction(() -> pauseClicked.set(true));
        menuBar.setOnNextAction(() -> nextClicked.set(true));
        menuBar.setOnPrevAction(() -> prevClicked.set(true));
        menuBar.setOnShuffleAction(shuffleState::set);
        menuBar.setOnRepeatModeAction(repeatState::set);
        menuBar.setOnSwitchAccountAction(() -> switchAccountClicked.set(true));

        menuBar.getAddFileItem().doClick();
        assertThat(addFileClicked.get()).isTrue();

        menuBar.getAddFolderItem().doClick();
        assertThat(addFolderClicked.get()).isTrue();

        menuBar.getRescanLibraryItem().doClick();
        assertThat(rescanLibClicked.get()).isTrue();

        menuBar.getRescanItem().doClick();
        assertThat(rescanMissingClicked.get()).isTrue();

        menuBar.getExitItem().doClick();
        assertThat(exitClicked.get()).isTrue();

        menuBar.getEditTagsItem().doClick();
        assertThat(editTagsClicked.get()).isTrue();

        menuBar.getMiniPlayerItem().doClick();
        assertThat(miniPlayerClicked.get()).isTrue();

        menuBar.getPlayItem().doClick();
        assertThat(playClicked.get()).isTrue();

        menuBar.getPauseItem().doClick();
        assertThat(pauseClicked.get()).isTrue();

        menuBar.getNextItem().doClick();
        assertThat(nextClicked.get()).isTrue();

        menuBar.getPrevItem().doClick();
        assertThat(prevClicked.get()).isTrue();

        menuBar.getShuffleMenuItem().doClick();
        assertThat(shuffleState.get()).isNotNull();

        menuBar.getRepeatAllItem().doClick();
        assertThat(repeatState.get()).isEqualTo(RepeatMode.ALL);

        menuBar.getRepeatOneItem().doClick();
        assertThat(repeatState.get()).isEqualTo(RepeatMode.ONE);

        menuBar.getRepeatOffItem().doClick();
        assertThat(repeatState.get()).isEqualTo(RepeatMode.OFF);

        menuBar.getSwitchAccountItem().doClick();
        assertThat(switchAccountClicked.get()).isTrue();
    }

    @Test
    @DisplayName("Language updates refresh text labels across menus and items")
    void testUpdateLanguage() {
        AppMenuBar menuBar = new AppMenuBar();

        // Switch to Japanese
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        menuBar.updateLanguage();

        assertThat(menuBar.getFileMenu().getText()).isEqualTo("ファイル");
        assertThat(menuBar.getEditMenu().getText()).isEqualTo("編集");
        assertThat(menuBar.getViewMenu().getText()).isEqualTo("表示");
        assertThat(menuBar.getControlsMenu().getText()).isEqualTo("操作");
        assertThat(menuBar.getAccountMenu().getText()).isEqualTo("アカウント");
        assertThat(menuBar.getHelpMenu().getText()).isEqualTo("ヘルプ");
        assertThat(menuBar.getAddFileItem().getText()).isEqualTo("ファイルを追加...");
        assertThat(menuBar.getShuffleMenuItem().getText()).isEqualTo("シャッフル");
        assertThat(menuBar.getRepeatSubMenu().getText()).isEqualTo("リピート");
        assertThat(menuBar.getRepeatOffItem().getText()).isEqualTo("リピートオフ");
        assertThat(menuBar.getRepeatAllItem().getText()).isEqualTo("全曲リピート");
        assertThat(menuBar.getRepeatOneItem().getText()).isEqualTo("1曲リピート");

        // Switch back to English
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        menuBar.updateLanguage();

        assertThat(menuBar.getFileMenu().getText()).isEqualTo("File");
        assertThat(menuBar.getEditMenu().getText()).isEqualTo("Edit");
        assertThat(menuBar.getViewMenu().getText()).isEqualTo("View");
        assertThat(menuBar.getControlsMenu().getText()).isEqualTo("Controls");
        assertThat(menuBar.getAccountMenu().getText()).isEqualTo("Account");
        assertThat(menuBar.getHelpMenu().getText()).isEqualTo("Help");
        assertThat(menuBar.getAddFileItem().getText()).isEqualTo("Add File...");
        assertThat(menuBar.getShuffleMenuItem().getText()).isEqualTo("Shuffle");
        assertThat(menuBar.getRepeatSubMenu().getText()).isEqualTo("Repeat");
        assertThat(menuBar.getRepeatOffItem().getText()).isEqualTo("Repeat Off");
        assertThat(menuBar.getRepeatAllItem().getText()).isEqualTo("Repeat All");
        assertThat(menuBar.getRepeatOneItem().getText()).isEqualTo("Repeat One");
    }

    @Test
    @DisplayName("Theme selection sync updates radio items")
    void testThemeSelectionSync() {
        AppMenuBar menuBar = new AppMenuBar();

        menuBar.updateThemeSelection(AppTheme.LIGHT);
        assertThat(menuBar.getLightThemeItem().isSelected()).isTrue();
        assertThat(menuBar.getDarkThemeItem().isSelected()).isFalse();

        menuBar.updateThemeSelection(AppTheme.DARK);
        assertThat(menuBar.getDarkThemeItem().isSelected()).isTrue();
        assertThat(menuBar.getLightThemeItem().isSelected()).isFalse();

        menuBar.updateThemeSelection(AppTheme.SYSTEM);
        assertThat(menuBar.getSystemThemeItem().isSelected()).isTrue();
    }

    @Test
    @DisplayName("Language selection sync updates radio items")
    void testLanguageSelectionSync() {
        AppMenuBar menuBar = new AppMenuBar();

        menuBar.updateLanguageSelection(AppLocale.JAPANESE);
        assertThat(menuBar.getJaLanguageItem().isSelected()).isTrue();
        assertThat(menuBar.getEnLanguageItem().isSelected()).isFalse();

        menuBar.updateLanguageSelection(AppLocale.ENGLISH);
        assertThat(menuBar.getEnLanguageItem().isSelected()).isTrue();
        assertThat(menuBar.getJaLanguageItem().isSelected()).isFalse();

        menuBar.updateLanguageSelection(AppLocale.SYSTEM);
        assertThat(menuBar.getSystemLanguageItem().isSelected()).isTrue();
    }

    @Test
    @DisplayName("Repeat mode and shuffle selection sync")
    void testRepeatAndShuffleSync() {
        AppMenuBar menuBar = new AppMenuBar();

        menuBar.updateRepeatMode(RepeatMode.ALL);
        assertThat(menuBar.getRepeatAllItem().isSelected()).isTrue();
        assertThat(menuBar.getRepeatOffItem().isSelected()).isFalse();

        menuBar.updateRepeatMode(RepeatMode.ONE);
        assertThat(menuBar.getRepeatOneItem().isSelected()).isTrue();
        assertThat(menuBar.getRepeatAllItem().isSelected()).isFalse();

        menuBar.updateRepeatMode(RepeatMode.OFF);
        assertThat(menuBar.getRepeatOffItem().isSelected()).isTrue();
        assertThat(menuBar.getRepeatOneItem().isSelected()).isFalse();

        menuBar.setShuffleSelected(true);
        assertThat(menuBar.getShuffleMenuItem().isSelected()).isTrue();
        menuBar.setShuffleSelected(false);
        assertThat(menuBar.getShuffleMenuItem().isSelected()).isFalse();
    }

    @Test
    @DisplayName("Menu actions safely complete without exception when callbacks are null or unassigned")
    void testDefaultAndNullCallbacksSafelyIgnored() {
        AppMenuBar menuBar = new AppMenuBar();

        // Explicitly set exit action to null so doClick does not terminate JVM
        menuBar.setOnExitAction(null);

        // Click all menu items without registering callbacks
        assertThatCode(() -> {
            menuBar.getAddFileItem().doClick();
            menuBar.getAddFolderItem().doClick();
            menuBar.getRescanLibraryItem().doClick();
            menuBar.getRescanItem().doClick();
            menuBar.getExitItem().doClick();
            menuBar.getEditTagsItem().doClick();
            menuBar.getMiniPlayerItem().doClick();
            menuBar.getPlayItem().doClick();
            menuBar.getPauseItem().doClick();
            menuBar.getNextItem().doClick();
            menuBar.getPrevItem().doClick();
            menuBar.getShuffleMenuItem().doClick();
            menuBar.getRepeatOffItem().doClick();
            menuBar.getRepeatAllItem().doClick();
            menuBar.getRepeatOneItem().doClick();
            menuBar.getSwitchAccountItem().doClick();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Language sub-menu radio items dynamically change I18nManager locale")
    void testDynamicLanguageSwitchingViaMenu() {
        AppMenuBar menuBar = new AppMenuBar();

        try {
            menuBar.getJaLanguageItem().doClick();
            assertThat(I18nManager.getInstance().getCurrentLocale()).isEqualTo(AppLocale.JAPANESE);

            menuBar.getEnLanguageItem().doClick();
            assertThat(I18nManager.getInstance().getCurrentLocale()).isEqualTo(AppLocale.ENGLISH);

            menuBar.getSystemLanguageItem().doClick();
            assertThat(I18nManager.getInstance().getCurrentLocale()).isEqualTo(AppLocale.SYSTEM);
        } finally {
            I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        }
    }

    @Test
    @DisplayName("Theme sub-menu radio items dynamically change ThemeManager theme")
    void testDynamicThemeSwitchingViaMenu() {
        AppMenuBar menuBar = new AppMenuBar();

        try {
            menuBar.getLightThemeItem().doClick();
            assertThat(ThemeManager.getInstance().getCurrentTheme()).isEqualTo(AppTheme.LIGHT);

            menuBar.getDarkThemeItem().doClick();
            assertThat(ThemeManager.getInstance().getCurrentTheme()).isEqualTo(AppTheme.DARK);

            menuBar.getSystemThemeItem().doClick();
            assertThat(ThemeManager.getInstance().getCurrentTheme()).isEqualTo(AppTheme.SYSTEM);
        } finally {
            ThemeManager.getInstance().applyTheme(AppTheme.DARK);
        }
    }

    @Test
    @DisplayName("Shuffle toggle triggers callback with alternate boolean states and stays synced")
    void testShuffleBidirectionalToggleAndCallback() {
        AppMenuBar menuBar = new AppMenuBar();
        List<Boolean> toggles = new ArrayList<>();
        menuBar.setOnShuffleAction(toggles::add);

        assertThat(menuBar.getShuffleMenuItem().isSelected()).isFalse();

        // 1st click -> true
        menuBar.getShuffleMenuItem().doClick();
        assertThat(menuBar.getShuffleMenuItem().isSelected()).isTrue();

        // 2nd click -> false
        menuBar.getShuffleMenuItem().doClick();
        assertThat(menuBar.getShuffleMenuItem().isSelected()).isFalse();

        assertThat(toggles).containsExactly(true, false);
    }

    @Test
    @DisplayName("No-arg sync methods update menu selections from current manager states")
    void testNoArgSyncMethods() {
        AppMenuBar menuBar = new AppMenuBar();

        try {
            // Theme sync
            ThemeManager.getInstance().applyTheme(AppTheme.LIGHT);
            menuBar.updateThemeSelection();
            assertThat(menuBar.getLightThemeItem().isSelected()).isTrue();

            // Language sync
            I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
            menuBar.updateLanguageSelection();
            assertThat(menuBar.getJaLanguageItem().isSelected()).isTrue();
        } finally {
            ThemeManager.getInstance().applyTheme(AppTheme.DARK);
            I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        }
    }
}

