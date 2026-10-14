package com.musiclibrary.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class KeyboardShortcutManagerTest {

    @Test
    @DisplayName("KeyboardShortcutManager registers all 11 required key strokes in RootPane InputMap")
    void testKeyBindingsRegistered() {
        JRootPane rootPane = new JRootPane();
        KeyboardShortcutManager manager = new KeyboardShortcutManager(rootPane);

        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        int mask = KeyboardShortcutManager.getMenuShortcutMask();

        // 1. Space
        assertThat(inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0))).isEqualTo("playPauseToggle");
        // 2. Ctrl + F
        assertThat(inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_F, mask))).isEqualTo("searchFocus");
        // 3. Ctrl + Right & L
        assertThat(inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, mask))).isEqualTo("skipForwardMask");
        assertThat(inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_L, 0))).isEqualTo("skipForwardL");
        // 4. Ctrl + Left & J
        assertThat(inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, mask))).isEqualTo("skipBackwardMask");
        assertThat(inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_J, 0))).isEqualTo("skipBackwardJ");
        // 5. Ctrl + Up
        assertThat(inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_UP, mask))).isEqualTo("volumeUp");
        // 6. Ctrl + Down
        assertThat(inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, mask))).isEqualTo("volumeDown");
        // 7. Ctrl + T
        assertThat(inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_T, mask))).isEqualTo("themeToggle");
        // 8. Ctrl + M
        assertThat(inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_M, mask))).isEqualTo("miniPlayerToggle");
        // 9. Ctrl + O
        assertThat(inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_O, mask))).isEqualTo("addFiles");
    }

    @Test
    @DisplayName("ActionMap actions trigger all registered callbacks accurately")
    void testActionExecutionAllCallbacks() {
        JRootPane rootPane = new JRootPane();
        KeyboardShortcutManager manager = new KeyboardShortcutManager(rootPane);

        AtomicBoolean searchFocusCalled = new AtomicBoolean(false);
        AtomicBoolean themeToggleCalled = new AtomicBoolean(false);
        AtomicBoolean volumeUpCalled = new AtomicBoolean(false);
        AtomicBoolean volumeDownCalled = new AtomicBoolean(false);
        AtomicBoolean miniPlayerCalled = new AtomicBoolean(false);
        AtomicBoolean addFilesCalled = new AtomicBoolean(false);
        AtomicBoolean playPauseCalled = new AtomicBoolean(false);
        AtomicInteger skipForwardCount = new AtomicInteger(0);
        AtomicInteger skipBackwardCount = new AtomicInteger(0);

        manager.setOnPlayPauseToggle(() -> playPauseCalled.set(true));
        manager.setOnSearchFocus(() -> searchFocusCalled.set(true));
        manager.setOnThemeToggle(() -> themeToggleCalled.set(true));
        manager.setOnVolumeUp(() -> volumeUpCalled.set(true));
        manager.setOnVolumeDown(() -> volumeDownCalled.set(true));
        manager.setOnMiniPlayerToggle(() -> miniPlayerCalled.set(true));
        manager.setOnAddFiles(() -> addFilesCalled.set(true));
        manager.setOnSkipForward(skipForwardCount::incrementAndGet);
        manager.setOnSkipBackward(skipBackwardCount::incrementAndGet);

        ActionMap actionMap = rootPane.getActionMap();

        triggerAction(actionMap, "playPauseToggle");
        assertThat(playPauseCalled.get()).isTrue();

        triggerAction(actionMap, "searchFocus");
        assertThat(searchFocusCalled.get()).isTrue();

        triggerAction(actionMap, "themeToggle");
        assertThat(themeToggleCalled.get()).isTrue();

        triggerAction(actionMap, "volumeUp");
        assertThat(volumeUpCalled.get()).isTrue();

        triggerAction(actionMap, "volumeDown");
        assertThat(volumeDownCalled.get()).isTrue();

        triggerAction(actionMap, "miniPlayerToggle");
        assertThat(miniPlayerCalled.get()).isTrue();

        triggerAction(actionMap, "addFiles");
        assertThat(addFilesCalled.get()).isTrue();

        triggerAction(actionMap, "skipForwardMask");
        triggerAction(actionMap, "skipForwardL");
        assertThat(skipForwardCount.get()).isEqualTo(2);

        triggerAction(actionMap, "skipBackwardMask");
        triggerAction(actionMap, "skipBackwardJ");
        assertThat(skipBackwardCount.get()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "playPauseToggle", "searchFocus", "skipForwardMask", "skipForwardL",
            "skipBackwardMask", "skipBackwardJ", "volumeUp", "volumeDown",
            "themeToggle", "miniPlayerToggle", "addFiles"
    })
    @DisplayName("ActionMap actions do not throw NullPointerException when handlers are null")
    void testNullHandlersDoNotThrowException(String actionKey) {
        JRootPane rootPane = new JRootPane();
        new KeyboardShortcutManager(rootPane); // handlers remain null

        ActionMap actionMap = rootPane.getActionMap();
        assertThatCode(() -> triggerAction(actionMap, actionKey))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Single-key shortcuts (Space, J, L) are inhibited when text component is focused")
    void testSingleKeySuppressionWhenTextComponentFocused() {
        JRootPane rootPane = new JRootPane();
        // Create manager with simulated focused text component
        KeyboardShortcutManager manager = new KeyboardShortcutManager(rootPane) {
            @Override
            boolean isTextComponentFocused() {
                return true;
            }
        };

        AtomicBoolean playPauseCalled = new AtomicBoolean(false);
        AtomicBoolean skipForwardLCalled = new AtomicBoolean(false);
        AtomicBoolean skipBackwardJCalled = new AtomicBoolean(false);

        manager.setOnPlayPauseToggle(() -> playPauseCalled.set(true));
        manager.setOnSkipForward(() -> skipForwardLCalled.set(true));
        manager.setOnSkipBackward(() -> skipBackwardJCalled.set(true));

        ActionMap actionMap = rootPane.getActionMap();

        // 1. Space should be suppressed
        triggerAction(actionMap, "playPauseToggle");
        assertThat(playPauseCalled.get()).isFalse();

        // 2. 'L' single key should be suppressed
        triggerAction(actionMap, "skipForwardL");
        assertThat(skipForwardLCalled.get()).isFalse();

        // 3. 'J' single key should be suppressed
        triggerAction(actionMap, "skipBackwardJ");
        assertThat(skipBackwardJCalled.get()).isFalse();
    }

    @Test
    @DisplayName("Modifier shortcuts (Ctrl+F, Ctrl+Right, Ctrl+Left, Ctrl+Up, etc.) execute even when text component is focused")
    void testModifierShortcutsExecuteWhenTextComponentFocused() {
        JRootPane rootPane = new JRootPane();
        KeyboardShortcutManager manager = new KeyboardShortcutManager(rootPane) {
            @Override
            boolean isTextComponentFocused() {
                return true;
            }
        };

        AtomicBoolean searchFocusCalled = new AtomicBoolean(false);
        AtomicBoolean skipForwardMaskCalled = new AtomicBoolean(false);
        AtomicBoolean skipBackwardMaskCalled = new AtomicBoolean(false);
        AtomicBoolean volumeUpCalled = new AtomicBoolean(false);
        AtomicBoolean volumeDownCalled = new AtomicBoolean(false);
        AtomicBoolean themeToggleCalled = new AtomicBoolean(false);
        AtomicBoolean miniPlayerCalled = new AtomicBoolean(false);
        AtomicBoolean addFilesCalled = new AtomicBoolean(false);

        manager.setOnSearchFocus(() -> searchFocusCalled.set(true));
        manager.setOnSkipForward(() -> skipForwardMaskCalled.set(true));
        manager.setOnSkipBackward(() -> skipBackwardMaskCalled.set(true));
        manager.setOnVolumeUp(() -> volumeUpCalled.set(true));
        manager.setOnVolumeDown(() -> volumeDownCalled.set(true));
        manager.setOnThemeToggle(() -> themeToggleCalled.set(true));
        manager.setOnMiniPlayerToggle(() -> miniPlayerCalled.set(true));
        manager.setOnAddFiles(() -> addFilesCalled.set(true));

        ActionMap actionMap = rootPane.getActionMap();

        triggerAction(actionMap, "searchFocus");
        assertThat(searchFocusCalled.get()).isTrue();

        triggerAction(actionMap, "skipForwardMask");
        assertThat(skipForwardMaskCalled.get()).isTrue();

        triggerAction(actionMap, "skipBackwardMask");
        assertThat(skipBackwardMaskCalled.get()).isTrue();

        triggerAction(actionMap, "volumeUp");
        assertThat(volumeUpCalled.get()).isTrue();

        triggerAction(actionMap, "volumeDown");
        assertThat(volumeDownCalled.get()).isTrue();

        triggerAction(actionMap, "themeToggle");
        assertThat(themeToggleCalled.get()).isTrue();

        triggerAction(actionMap, "miniPlayerToggle");
        assertThat(miniPlayerCalled.get()).isTrue();

        triggerAction(actionMap, "addFiles");
        assertThat(addFilesCalled.get()).isTrue();
    }

    @Test
    @DisplayName("Rebinding handler to new callback or null works correctly")
    void testRebindingHandlers() {
        JRootPane rootPane = new JRootPane();
        KeyboardShortcutManager manager = new KeyboardShortcutManager(rootPane);
        ActionMap actionMap = rootPane.getActionMap();

        AtomicInteger callCount1 = new AtomicInteger(0);
        AtomicInteger callCount2 = new AtomicInteger(0);

        // Bind first handler
        manager.setOnThemeToggle(callCount1::incrementAndGet);
        triggerAction(actionMap, "themeToggle");
        assertThat(callCount1.get()).isEqualTo(1);
        assertThat(callCount2.get()).isEqualTo(0);

        // Rebind to second handler
        manager.setOnThemeToggle(callCount2::incrementAndGet);
        triggerAction(actionMap, "themeToggle");
        assertThat(callCount1.get()).isEqualTo(1);
        assertThat(callCount2.get()).isEqualTo(1);

        // Unbind (set null)
        manager.setOnThemeToggle(null);
        triggerAction(actionMap, "themeToggle");
        assertThat(callCount1.get()).isEqualTo(1);
        assertThat(callCount2.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Null rootPane constructor behaves safely without exceptions")
    void testNullRootPane() {
        KeyboardShortcutManager manager = new KeyboardShortcutManager(null);
        assertThat(manager.getRootPane()).isNull();

        assertThatCode(() -> {
            manager.setOnPlayPauseToggle(() -> {});
            manager.setOnSearchFocus(() -> {});
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("getMenuShortcutMask returns valid non-zero mask in any environment")
    void testMenuShortcutMask() {
        int mask = KeyboardShortcutManager.getMenuShortcutMask();
        assertThat(mask).isNotZero();
    }

    private void triggerAction(ActionMap actionMap, String key) {
        Action action = actionMap.get(key);
        assertThat(action).as("Action for key: " + key).isNotNull();
        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, key));
    }
}
