package com.musiclibrary.ui.table;

import com.musiclibrary.model.PlaybackState;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.Objects;

/**
 * High-DPI anti-aliased vector icon indicating track playback status
 * (Playing: speaker with sound waves, Paused: double pause bars).
 */
public class PlaybackStatusIcon implements Icon {

    private static final int ICON_SIZE = 16;

    private PlaybackState state;
    private Color customColor;

    public PlaybackStatusIcon(PlaybackState state) {
        this(state, null);
    }

    public PlaybackStatusIcon(PlaybackState state, Color customColor) {
        this.state = state != null ? state : PlaybackState.STOPPED;
        this.customColor = customColor;
    }

    public PlaybackState getState() {
        return state;
    }

    public void setState(PlaybackState state) {
        this.state = state != null ? state : PlaybackState.STOPPED;
    }

    public Color getColor() {
        return customColor;
    }

    public void setColor(Color customColor) {
        this.customColor = customColor;
    }

    @Override
    public int getIconWidth() {
        return ICON_SIZE;
    }

    @Override
    public int getIconHeight() {
        return ICON_SIZE;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        if (state == PlaybackState.STOPPED) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            Color color = customColor;
            if (color == null) {
                color = (c != null && c.getForeground() != null) ? c.getForeground() : Color.WHITE;
            }
            g2.setColor(color);

            if (state == PlaybackState.PLAYING) {
                drawPlayingSpeaker(g2, x, y);
            } else if (state == PlaybackState.PAUSED) {
                drawPausedBars(g2, x, y);
            }
        } finally {
            g2.dispose();
        }
    }

    private void drawPlayingSpeaker(Graphics2D g2, int x, int y) {
        // Speaker cone & body
        Path2D speaker = new Path2D.Float();
        speaker.moveTo(x + 3, y + 6);
        speaker.lineTo(x + 5, y + 6);
        speaker.lineTo(x + 8, y + 3);
        speaker.lineTo(x + 8, y + 13);
        speaker.lineTo(x + 5, y + 10);
        speaker.lineTo(x + 3, y + 10);
        speaker.closePath();
        g2.fill(speaker);

        // Sound waves
        g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // Inner arc
        g2.drawArc(x + 7, y + 5, 5, 6, -50, 100);
        // Outer arc
        g2.drawArc(x + 7, y + 2, 10, 12, -45, 90);
    }

    private void drawPausedBars(Graphics2D g2, int x, int y) {
        int barWidth = 3;
        int barHeight = 10;
        int arc = 2;
        g2.fillRoundRect(x + 4, y + 3, barWidth, barHeight, arc, arc);
        g2.fillRoundRect(x + 9, y + 3, barWidth, barHeight, arc, arc);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaybackStatusIcon that = (PlaybackStatusIcon) o;
        return state == that.state && Objects.equals(customColor, that.customColor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, customColor);
    }
}
