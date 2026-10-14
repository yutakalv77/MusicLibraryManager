package com.musiclibrary.ui.layout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.Dimension;
import java.awt.FlowLayout;

import static org.assertj.core.api.Assertions.assertThat;

class WrapLayoutTest {

    @Test
    @DisplayName("WrapLayout initializes with default and custom gap constructors")
    void testConstructors() {
        WrapLayout layout1 = new WrapLayout();
        assertThat(layout1.getAlignment()).isEqualTo(FlowLayout.CENTER);

        WrapLayout layout2 = new WrapLayout(FlowLayout.LEFT);
        assertThat(layout2.getAlignment()).isEqualTo(FlowLayout.LEFT);

        WrapLayout layout3 = new WrapLayout(FlowLayout.RIGHT, 10, 20);
        assertThat(layout3.getAlignment()).isEqualTo(FlowLayout.RIGHT);
        assertThat(layout3.getHgap()).isEqualTo(10);
        assertThat(layout3.getVgap()).isEqualTo(20);
    }

    @Test
    @DisplayName("preferredLayoutSize wraps components to multiple rows when container width is restricted")
    void testPreferredLayoutSizeWrapping() {
        JPanel panel = new JPanel(new WrapLayout(FlowLayout.LEFT, 10, 10));

        // Add 4 buttons of size 100x50
        for (int i = 0; i < 4; i++) {
            JButton btn = new JButton("Btn" + i);
            btn.setPreferredSize(new Dimension(100, 50));
            panel.add(btn);
        }

        // 1. Wide container (e.g. 600px wide): all 4 fit on 1 row
        panel.setSize(600, 100);
        Dimension wideDim = panel.getPreferredSize();
        // Row height = 50, + vgap*2 (20) = 70
        assertThat(wideDim.height).isEqualTo(70);

        // 2. Narrow container (e.g. 250px wide): max 2 buttons per row -> 2 rows
        panel.setSize(250, 100);
        Dimension narrowDim = panel.getPreferredSize();
        // 2 rows: 50 + 10 (gap) + 50 + 20 (vgap*2) = 130
        assertThat(narrowDim.height).isEqualTo(130);

        // 3. Very narrow container (e.g. 120px wide): 1 button per row -> 4 rows
        panel.setSize(120, 100);
        Dimension veryNarrowDim = panel.getPreferredSize();
        // 4 rows: 50*4 + 10*3 (gap) + 20 (vgap*2) = 250
        assertThat(veryNarrowDim.height).isEqualTo(250);
    }

    @Test
    @DisplayName("minimumLayoutSize returns dimension adapted for minimum size of components")
    void testMinimumLayoutSize() {
        JPanel panel = new JPanel(new WrapLayout(FlowLayout.LEFT, 5, 5));
        JButton btn = new JButton("Test");
        btn.setMinimumSize(new Dimension(80, 40));
        panel.add(btn);
        panel.setSize(300, 100);

        Dimension minDim = panel.getMinimumSize();
        assertThat(minDim.height).isGreaterThanOrEqualTo(40);
        assertThat(minDim.width).isGreaterThan(0);
    }

    @Test
    @DisplayName("preferredLayoutSize inside JScrollPane adjusts width when target is valid")
    void testInsideScrollPane() {
        JPanel panel = new JPanel(new WrapLayout(FlowLayout.LEFT, 10, 10));
        for (int i = 0; i < 3; i++) {
            JButton btn = new JButton("B" + i);
            btn.setPreferredSize(new Dimension(80, 40));
            panel.add(btn);
        }

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setSize(400, 300);
        panel.setSize(400, 300);
        panel.validate();

        Dimension dim = panel.getPreferredSize();
        assertThat(dim.width).isGreaterThan(0);
        assertThat(dim.height).isGreaterThan(0);
    }
}
