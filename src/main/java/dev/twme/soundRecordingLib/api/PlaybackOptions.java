package dev.twme.soundRecordingLib.api;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

public final class PlaybackOptions {

    private final @Nullable Location anchor;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final double speed;
    private final boolean loop;
    private final int skipThresholdTicks;
    private final boolean followReplayer;
    private final boolean applyRecorderOrientation;
    private final boolean applyReplayerOrientation;
    private final boolean speakerMode;

    private PlaybackOptions(Builder builder) {
        this.anchor = builder.anchor;
        this.offsetX = builder.offsetX;
        this.offsetY = builder.offsetY;
        this.offsetZ = builder.offsetZ;
        this.speed = builder.speed;
        this.loop = builder.loop;
        this.skipThresholdTicks = builder.skipThresholdTicks;
        this.followReplayer = builder.followReplayer;
        this.applyRecorderOrientation = builder.applyRecorderOrientation;
        this.applyReplayerOrientation = builder.applyReplayerOrientation;
        this.speakerMode = builder.speakerMode;
    }

    public @Nullable Location getAnchor() { return anchor; }
    public double getOffsetX() { return offsetX; }
    public double getOffsetY() { return offsetY; }
    public double getOffsetZ() { return offsetZ; }
    public double getSpeed() { return speed; }
    public boolean isLoop() { return loop; }
    public int getSkipThresholdTicks() { return skipThresholdTicks; }
    public boolean isFollowReplayer() { return followReplayer; }
    public boolean isApplyRecorderOrientation() { return applyRecorderOrientation; }
    public boolean isApplyReplayerOrientation() { return applyReplayerOrientation; }
    public boolean isSpeakerMode() { return speakerMode; }

    public static Builder builder() { return new Builder(); }

    public static PlaybackOptions defaults() { return builder().build(); }

    public static PlaybackOptions firstPerson() {
        return builder()
                .followReplayer(true)
                .applyRecorderOrientation(true)
                .applyReplayerOrientation(true)
                .build();
    }

    public static final class Builder {
        private @Nullable Location anchor = null;
        private double offsetX = 0;
        private double offsetY = 0;
        private double offsetZ = 0;
        private double speed = 1.0;
        private boolean loop = false;
        private int skipThresholdTicks = 0;
        private boolean followReplayer = false;
        private boolean applyRecorderOrientation = false;
        private boolean applyReplayerOrientation = false;
        private boolean speakerMode = false;

        public Builder anchor(Location anchor) { this.anchor = anchor; return this; }
        public Builder offset(double x, double y, double z) { this.offsetX = x; this.offsetY = y; this.offsetZ = z; return this; }
        public Builder speed(double speed) { this.speed = speed; return this; }
        public Builder loop(boolean loop) { this.loop = loop; return this; }
        public Builder skip(int thresholdTicks) { this.skipThresholdTicks = thresholdTicks; return this; }
        public Builder followReplayer(boolean v) { this.followReplayer = v; return this; }
        public Builder applyRecorderOrientation(boolean v) { this.applyRecorderOrientation = v; return this; }
        public Builder applyReplayerOrientation(boolean v) { this.applyReplayerOrientation = v; return this; }
        public Builder speakerMode(boolean v) { this.speakerMode = v; return this; }
        public PlaybackOptions build() { return new PlaybackOptions(this); }
    }
}
