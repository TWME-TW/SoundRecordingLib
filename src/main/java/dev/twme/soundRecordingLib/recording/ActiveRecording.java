package dev.twme.soundRecordingLib.recording;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import com.github.retrooper.packetevents.protocol.sound.SoundCategory;

import dev.twme.soundRecordingLib.api.RecordingOptions;
import dev.twme.soundRecordingLib.data.RecordedSoundEvent;
import dev.twme.soundRecordingLib.data.RecordingMode;

public class ActiveRecording {

    private final UUID playerUuid;
    private final String name;
    private final long startTick;
    private final RecordingMode mode;
    private final double refX;
    private final double refY;
    private final double refZ;
    private final String worldName;
    private final @Nullable Set<SoundCategory> categoryFilter;

    private final ConcurrentLinkedQueue<RecordedSoundEvent> events = new ConcurrentLinkedQueue<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public ActiveRecording(UUID playerUuid, RecordingOptions options, long startTick, Location startLocation) {
        this.playerUuid = playerUuid;
        this.name = options.getName();
        this.startTick = startTick;
        this.mode = options.getMode();
        this.categoryFilter = options.getCategoryFilter();

        Location ref = (options.getMode() == RecordingMode.STATIC && options.getReferencePos() != null)
                ? options.getReferencePos()
                : startLocation;

        this.refX = ref.getX();
        this.refY = ref.getY();
        this.refZ = ref.getZ();
        this.worldName = startLocation.getWorld() != null ? startLocation.getWorld().getName() : "world";
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getName() { return name; }
    public long getStartTick() { return startTick; }
    public RecordingMode getMode() { return mode; }
    public double getRefX() { return refX; }
    public double getRefY() { return refY; }
    public double getRefZ() { return refZ; }
    public String getWorldName() { return worldName; }
    public @Nullable Set<SoundCategory> getCategoryFilter() { return categoryFilter; }
    public ConcurrentLinkedQueue<RecordedSoundEvent> getEvents() { return events; }
    public ReentrantReadWriteLock getLock() { return lock; }

    public void addEvent(RecordedSoundEvent event) {
        lock.readLock().lock();
        try {
            events.add(event);
        } finally {
            lock.readLock().unlock();
        }
    }
}
