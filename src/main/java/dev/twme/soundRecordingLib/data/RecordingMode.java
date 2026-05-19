package dev.twme.soundRecordingLib.data;

public enum RecordingMode {
    /**
     * Fixed reference point mode.
     * A fixed world coordinate is set as the reference point (referencePosition) when recording starts.
     * Each sound event stores its offset vector relative to the referencePosition.
     * Suitable for: redstone music, fixed-position sounds in buildings, and other location-fixed scenarios.
     */
    STATIC,

    /**
     * Player-following mode (first-person experience).
     * Each sound event stores its offset vector relative to the recorder's instantaneous position at that tick.
     * Suitable for: capturing the full sound experience a player hears while walking through the world.
     */
    PLAYER_RELATIVE
}
