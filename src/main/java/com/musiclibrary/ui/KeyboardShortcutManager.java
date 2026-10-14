package com.musiclibrary.ui;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Manages global keyboard shortcuts across the application window.
 * Binds shortcuts to JRootPane with WHEN_IN_FOCUSED_WINDOW,
 * and safely inhibits single-key shortcuts when a text input component is focused.
 */
public class KeyboardShortcutManager {

    private final JRootPane rootPane;

    private Runnable onPlayPauseToggle;
    private Runnable onSearchFocus;
    private Runnable onSkipForward;
    private Runnable onSkipBackward;
    private Runnable onVolumeUp;
    private Runnable onVolumeDown;
    private Runnable onThemeToggle;
    private Runnable onMiniPlayerToggle;
    private Runnable onAddFiles;
    private Runnable onEditTags;

    /**
     * Creates a shortcut manager attached to the given root pane.
     *
     * @param rootPane target root pane
     */
    public KeyboardShortcutManager(JRootPane rootPane) {
        this.rootPane = rootPane;
        if (rootPane != null) {
            registerKeyBindings();
        }
    }

    private void registerKeyBindings() {
        int mask = getMenuShortcutMask();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        // 1. Space: Play/Pause toggle (suppressed when editing text)
        register(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "playPauseToggle", () -> {
            if (!isTextComponentFocused()) {
                execute(onPlayPauseToggle);
            }
        });

        // 2. Ctrl + F / Cmd + F: Focus search field & select all
        register(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_F, mask), "searchFocus", () -> {
            execute(onSearchFocus);
        });

        // 3. Ctrl + Right / L: Skip forward
        register(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, mask), "skipForwardMask", () -> {
            execute(onSkipForward);
        });
        register(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_L, 0), "skipForwardL", () -> {
            if (!isTextComponentFocused()) {
                execute(onSkipForward);
            }
        });

        // 4. Ctrl + Left / J: Skip backward
        register(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, mask), "skipBackwardMask", () -> {
            execute(onSkipBackward);
        });
        register(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_J, 0), "skipBackwardJ", () -> {
            if (!isTextComponentFocused()) {
                execute(onSkipBackward);
            }
        });

        // 5. Ctrl + Up: Volume Up (+5%)
        register(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_UP, mask), "volumeUp", () -> {
            execute(onVolumeUp);
        });

        // 6. Ctrl + Down: Volume Down (-5%)
        register(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, mask), "volumeDown", () -> {
            execute(onVolumeDown);
        });

        // 7. Ctrl + T: Theme Toggle (Dark / Light)
        register(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_T, mask), "themeToggle", () -> {
            execute(onThemeToggle);
        });

        // 8. Ctrl + M: Mini Player Toggle
        register(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_M, mask), "miniPlayerToggle", () -> {
            execute(onMiniPlayerToggle);
        });

        // 9. Ctrl + O: Add Files
        register(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_O, mask), "addFiles", () -> {
            execute(onAddFiles);
        });

        // 10. Ctrl + I: Edit Tags
        register(inputMap, actionMap, KeyStroke.getKeyStroke(KeyEvent.VK_I, mask), "editTags", () -> {
            execute(onEditTags);
        });
    }

    private void register(InputMap inputMap, ActionMap actionMap, KeyStroke keyStroke, String actionKey, Runnable action) {
        inputMap.put(keyStroke, actionKey);
        actionMap.put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (action != null) {
                    action.run();
                }
            }
        });
    }

    private void execute(Runnable action) {
        if (action != null) {
            action.run();
        }
    }

    /**
     * Checks if the currently focused component is a text component (e.g. JTextField, JTextArea).
     */
    boolean isTextComponentFocused() {
        if (GraphicsEnvironment.isHeadless()) {
            return false;
        }
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return focusOwner instanceof JTextComponent;
    }

    /**
     * Resolves the menu shortcut key mask (Ctrl on Windows/Linux, Cmd on macOS).
     */
    public static int getMenuShortcutMask() {
        if (!GraphicsEnvironment.isHeadless()) {
            try {
                return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            } catch (Exception ignored) {
            }
        }
        return InputEvent.CTRL_DOWN_MASK;
    }

    // --- Action Handlers Setters ---

    public void setOnPlayPauseToggle(Runnable onPlayPauseToggle) {
        this.onPlayPauseToggle = onPlayPauseToggle;
    }

    public void setOnSearchFocus(Runnable onSearchFocus) {
        this.onSearchFocus = onSearchFocus;
    }

    public void setOnSkipForward(Runnable onSkipForward) {
        this.onSkipForward = onSkipForward;
    }

    public void setOnSkipBackward(Runnable onSkipBackward) {
        this.onSkipBackward = onSkipBackward;
    }

    public void setOnVolumeUp(Runnable onVolumeUp) {
        this.onVolumeUp = onVolumeUp;
    }

    public void setOnVolumeDown(Runnable onVolumeDown) {
        this.onVolumeDown = onVolumeDown;
    }

    public void setOnThemeToggle(Runnable onThemeToggle) {
        this.onThemeToggle = onThemeToggle;
    }

    public void setOnMiniPlayerToggle(Runnable onMiniPlayerToggle) {
        this.onMiniPlayerToggle = onMiniPlayerToggle;
    }

    public void setOnAddFiles(Runnable onAddFiles) {
        this.onAddFiles = onAddFiles;
    }

    public void setOnEditTags(Runnable onEditTags) {
        this.onEditTags = onEditTags;
    }

    public JRootPane getRootPane() {
        return rootPane;
    }
}
