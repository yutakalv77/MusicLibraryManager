package com.musiclibrary.model;

/**
 * Playback repeat mode enumerating looping behaviors.
 */
public enum RepeatMode {
    OFF,
    ALL,
    ONE;

    /**
     * Cycles through repeat modes: OFF -> ALL -> ONE -> OFF.
     *
     * @return next repeat mode
     */
    public RepeatMode next() {
        return switch (this) {
            case OFF -> ALL;
            case ALL -> ONE;
            case ONE -> OFF;
        };
    }
}
