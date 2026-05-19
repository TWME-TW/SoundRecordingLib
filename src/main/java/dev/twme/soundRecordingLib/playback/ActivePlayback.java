package dev.twme.soundRecordingLib.playback;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import dev.twme.soundRecordingLib.api.PlaybackOptions;
import dev.twme.soundRecordingLib.api.PlaybackSession;
import dev.twme.soundRecordingLib.data.SoundRecording;

public class ActivePlayback implements PlaybackSession {

    private final UUID sessionId = UUID.randomUUID();
    private final SoundRecording recording;
    private final Set<Player> targets;
    private final PlaybackOptions options;

    private volatile double currentRelativeTick = 0.0;
    private volatile int cursor = 0;
    private volatile boolean active = true;
    private volatile boolean paused = false;

    public ActivePlayback(SoundRecording recording, Collection<Player> targets, PlaybackOptions options) {
        this.recording = recording;
        this.targets = ConcurrentHashMap.newKeySet();
        this.targets.addAll(targets);
        this.options = options;
    }

    public UUID getSessionId() { return sessionId; }
    public PlaybackOptions getOptions() { return options; }
    public double getCurrentRelativeTick() { return currentRelativeTick; }
    public void setCurrentRelativeTick(double v) { this.currentRelativeTick = v; }
    public int getCursor() { return cursor; }
    public void setCursor(int v) { this.cursor = v; }
    public void setActive(boolean v) { this.active = v; }

    @Override public SoundRecording getRecording() { return recording; }
    @Override public Collection<Player> getTargets() { return Collections.unmodifiableSet(targets); }
    @Override public boolean isActive() { return active; }
    @Override public boolean isPaused() { return paused; }
    @Override public void pause() { this.paused = true; }
    @Override public void resume() { this.paused = false; }

    public boolean removeTarget(Player player) {
        targets.remove(player);
        return targets.isEmpty();
    }
}
