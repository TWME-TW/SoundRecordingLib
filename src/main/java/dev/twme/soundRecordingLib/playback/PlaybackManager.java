package dev.twme.soundRecordingLib.playback;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.sound.Sound;
import com.github.retrooper.packetevents.protocol.sound.Sounds;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;

import dev.twme.soundRecordingLib.api.PlaybackOptions;
import dev.twme.soundRecordingLib.api.PlaybackSession;
import dev.twme.soundRecordingLib.data.RecordedSoundEvent;
import dev.twme.soundRecordingLib.data.SoundRecording;

public class PlaybackManager {

    private final Plugin plugin;
    private final Map<UUID, ActivePlayback> activeSessions = new ConcurrentHashMap<>();
    private BukkitTask tickTask = null;

    public PlaybackManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public PlaybackSession startPlayback(SoundRecording recording, Collection<Player> targets, PlaybackOptions options) {
        ActivePlayback pb = new ActivePlayback(recording, targets, options);
        activeSessions.put(pb.getSessionId(), pb);
        startTickTaskIfNeeded();
        return pb;
    }

    public void stopPlayback(PlaybackSession session) {
        if (session instanceof ActivePlayback pb) {
            pb.setActive(false);
            activeSessions.remove(pb.getSessionId());
            stopTickTaskIfEmpty();
        }
    }

    public void removePlayer(Player player) {
        activeSessions.values().removeIf(pb -> {
            boolean empty = pb.removeTarget(player);
            if (empty) {
                pb.setActive(false);
            }
            return empty;
        });
        stopTickTaskIfEmpty();
    }

    private void startTickTaskIfNeeded() {
        if (tickTask == null || tickTask.isCancelled()) {
            tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::onTick, 1L, 1L);
        }
    }

    private void stopTickTaskIfEmpty() {
        if (activeSessions.isEmpty() && tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private void onTick() {
        if (activeSessions.isEmpty()) return;

        activeSessions.values().removeIf(pb -> {
            if (!pb.isActive()) return true;
            if (pb.isPaused()) return false;
            tickPlayback(pb);
            return !pb.isActive();
        });

        stopTickTaskIfEmpty();
    }

    private void tickPlayback(ActivePlayback pb) {
        PlaybackOptions opts = pb.getOptions();
        pb.setCurrentRelativeTick(pb.getCurrentRelativeTick() + opts.getSpeed());

        List<RecordedSoundEvent> events = pb.getRecording().events();
        double currentTick = pb.getCurrentRelativeTick();
        int cursor = pb.getCursor();
        int size = events.size();

        while (cursor < size) {
            RecordedSoundEvent event = events.get(cursor);

            // Skip stale events
            if (opts.getSkipThresholdTicks() > 0
                    && currentTick - event.relativeTick() > opts.getSkipThresholdTicks()) {
                cursor++;
                continue;
            }

            // Not yet time
            if (event.relativeTick() > currentTick) break;

            sendSoundToTargets(event, pb);
            cursor++;
        }

        pb.setCursor(cursor);

        if (cursor >= size) {
            if (opts.isLoop()) {
                pb.setCurrentRelativeTick(0.0);
                pb.setCursor(0);
            } else {
                pb.setActive(false);
            }
        }
    }

    private void sendSoundToTargets(RecordedSoundEvent event, ActivePlayback pb) {
        PlaybackOptions opts = pb.getOptions();
        SoundRecording recording = pb.getRecording();

        for (Player target : pb.getTargets()) {
            if (!target.isOnline()) continue;

            double[] world = resolveWorldCoords(event, target, opts, recording);
            sendSoundPacket(target, event, world[0], world[1], world[2]);
        }
    }

    private double[] resolveWorldCoords(RecordedSoundEvent event, Player target,
                                        PlaybackOptions opts, SoundRecording recording) {
        // Step 1: base position
        double baseX, baseY, baseZ;
        if (opts.isFollowReplayer()) {
            Location loc = target.getLocation();
            baseX = loc.getX();
            baseY = loc.getY();
            baseZ = loc.getZ();
        } else {
            Location anchor = opts.getAnchor();
            if (anchor != null) {
                baseX = anchor.getX();
                baseY = anchor.getY();
                baseZ = anchor.getZ();
            } else {
                baseX = recording.refX();
                baseY = recording.refY();
                baseZ = recording.refZ();
            }
        }

        // Step 2: offset vector
        double offX = event.relX();
        double offZ = event.relZ();
        double offY = event.relY();

        // Step 3: remove recorder orientation
        if (opts.isApplyRecorderOrientation()) {
            double[] rotated = rotate2D(offX, offZ, -event.recorderYaw());
            offX = rotated[0];
            offZ = rotated[1];
        }

        // Step 4: apply replayer orientation
        if (opts.isApplyReplayerOrientation()) {
            float yaw = target.getLocation().getYaw();
            double[] rotated = rotate2D(offX, offZ, yaw);
            offX = rotated[0];
            offZ = rotated[1];
        }

        // Step 5: apply extra offset and compute world coords
        double worldX = baseX + offX + opts.getOffsetX();
        double worldY = baseY + offY + opts.getOffsetY();
        double worldZ = baseZ + offZ + opts.getOffsetZ();

        // Step 6: speaker mode
        if (opts.isSpeakerMode()) {
            Location anchorLoc = opts.getAnchor();
            double ancX = anchorLoc != null ? anchorLoc.getX() : recording.refX();
            double ancY = anchorLoc != null ? anchorLoc.getY() : recording.refY();
            double ancZ = anchorLoc != null ? anchorLoc.getZ() : recording.refZ();

            double distA = Math.sqrt(event.relX() * event.relX()
                    + event.relY() * event.relY()
                    + event.relZ() * event.relZ());

            Location pLoc = target.getLocation();
            double dx = ancX - pLoc.getX();
            double dy = ancY - pLoc.getY();
            double dz = ancZ - pLoc.getZ();
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (d > 0 && d < distA) {
                // Extend sound in anchor direction to maintain original distance
                double nx = dx / d;
                double ny = dy / d;
                double nz = dz / d;
                worldX = pLoc.getX() + nx * distA;
                worldY = pLoc.getY() + ny * distA;
                worldZ = pLoc.getZ() + nz * distA;
            } else {
                worldX = ancX;
                worldY = ancY;
                worldZ = ancZ;
            }
        }

        return new double[]{worldX, worldY, worldZ};
    }

    private void sendSoundPacket(Player target, RecordedSoundEvent event,
                                 double worldX, double worldY, double worldZ) {
        Sound sound = Sounds.getByNameOrCreate(event.soundKey());

        WrapperPlayServerSoundEffect packet = new WrapperPlayServerSoundEffect(
                sound,
                event.category(),
                new Vector3d(worldX, worldY, worldZ),
                event.volume(),
                event.pitch(),
                event.seed()
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(target, packet);
    }

    // Rotate a 2D vector (x, z) by angleDegrees around the Y axis.
    // Minecraft yaw: 0° = south (+Z), 90° = west (-X), -90° = east (+X)
    // We treat yaw as standard compass bearing: rotate by -yaw in standard math convention.
    private double[] rotate2D(double x, double z, float yawDegrees) {
        double rad = Math.toRadians(yawDegrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        // Minecraft yaw: positive yaw = clockwise when viewed from above
        // Standard 2D rotation (counter-clockwise): x' = x*cos - z*sin, z' = x*sin + z*cos
        // For Minecraft yaw (clockwise): negate the angle
        double newX = x * cos + z * sin;
        double newZ = -x * sin + z * cos;
        return new double[]{newX, newZ};
    }
}
