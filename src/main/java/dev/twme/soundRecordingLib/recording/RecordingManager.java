package dev.twme.soundRecordingLib.recording;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import dev.twme.soundRecordingLib.api.RecordingOptions;
import dev.twme.soundRecordingLib.api.RecordingSession;
import dev.twme.soundRecordingLib.data.RecordedSoundEvent;
import dev.twme.soundRecordingLib.data.SoundRecording;
import dev.twme.soundRecordingLib.listener.SoundPacketListener;

public class RecordingManager {

    private final Map<UUID, ActiveRecording> activeRecordings = new ConcurrentHashMap<>();
    private final SoundPacketListener soundPacketListener;

    public RecordingManager(SoundPacketListener soundPacketListener) {
        this.soundPacketListener = soundPacketListener;
    }

    public RecordingSession startRecording(Player player, RecordingOptions options) {
        UUID uuid = player.getUniqueId();
        if (activeRecordings.containsKey(uuid)) {
            stopRecording(player);
        }

        long startTick = Bukkit.getCurrentTick();
        ActiveRecording rec = new ActiveRecording(uuid, options, startTick, player.getLocation());
        activeRecordings.put(uuid, rec);
        soundPacketListener.registerPlayer(uuid);

        return new RecordingSessionImpl(rec, this);
    }

    public SoundRecording stopRecording(Player player) {
        return stopRecordingByUuid(player.getUniqueId());
    }

    public SoundRecording stopRecordingByUuid(UUID uuid) {
        ActiveRecording rec = activeRecordings.remove(uuid);
        if (rec == null) return null;

        soundPacketListener.unregisterPlayer(uuid);

        // Acquire write lock to wait for all in-flight Netty handlers to finish writing
        rec.getLock().writeLock().lock();
        try {
            long totalTicks = Bukkit.getCurrentTick() - rec.getStartTick();

            List<RecordedSoundEvent> sorted = new ArrayList<>(rec.getEvents());
            sorted.sort(Comparator.comparingLong(RecordedSoundEvent::relativeTick));

            return new SoundRecording(
                    UUID.randomUUID(),
                    rec.getName(),
                    System.currentTimeMillis(),
                    totalTicks,
                    rec.getWorldName(),
                    rec.getMode(),
                    rec.getRefX(),
                    rec.getRefY(),
                    rec.getRefZ(),
                    Collections.unmodifiableList(sorted)
            );
        } finally {
            rec.getLock().writeLock().unlock();
        }
    }

    public Optional<RecordingSession> getActiveRecording(Player player) {
        ActiveRecording rec = activeRecordings.get(player.getUniqueId());
        if (rec == null) return Optional.empty();
        return Optional.of(new RecordingSessionImpl(rec, this));
    }

    public ActiveRecording getActiveRecordingRaw(UUID uuid) {
        return activeRecordings.get(uuid);
    }

    public boolean isRecording(UUID uuid) {
        return activeRecordings.containsKey(uuid);
    }

    public Set<UUID> getActivePlayerUuids() {
        return activeRecordings.keySet();
    }
}
