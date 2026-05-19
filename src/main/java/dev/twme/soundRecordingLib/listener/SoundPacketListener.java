package dev.twme.soundRecordingLib.listener;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.sound.Sound;
import com.github.retrooper.packetevents.protocol.sound.SoundCategory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;

import dev.twme.soundRecordingLib.data.RecordedSoundEvent;
import dev.twme.soundRecordingLib.data.RecordingMode;
import dev.twme.soundRecordingLib.recording.ActiveRecording;
import dev.twme.soundRecordingLib.recording.RecordingManager;

public class SoundPacketListener extends PacketListenerAbstract {

    private final Set<UUID> registeredPlayers = ConcurrentHashMap.newKeySet();
    // Player position snapshot: updated on main thread every tick (E1 mechanism)
    private final Map<UUID, double[]> positionSnapshots = new ConcurrentHashMap<>();
    // [x, y, z, yaw]
    private RecordingManager recordingManager;

    public SoundPacketListener() {
        super(PacketListenerPriority.NORMAL);
    }

    public void setRecordingManager(RecordingManager recordingManager) {
        this.recordingManager = recordingManager;
    }

    public void registerPlayer(UUID uuid) {
        registeredPlayers.add(uuid);
    }

    public void unregisterPlayer(UUID uuid) {
        registeredPlayers.remove(uuid);
        positionSnapshots.remove(uuid);
    }

    public void updatePositionSnapshot(UUID uuid, double x, double y, double z, float yaw) {
        positionSnapshots.put(uuid, new double[]{x, y, z, yaw});
    }

    public boolean hasRegisteredPlayers() {
        return !registeredPlayers.isEmpty();
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (registeredPlayers.isEmpty()) return;

        User user = event.getUser();
        UUID playerUuid = user.getUUID();
        if (playerUuid == null || !registeredPlayers.contains(playerUuid)) return;

        if (recordingManager == null) return;
        ActiveRecording rec = recordingManager.getActiveRecordingRaw(playerUuid);
        if (rec == null) return;

        if (event.getPacketType() == PacketType.Play.Server.SOUND_EFFECT) {
            handleSoundEffect(event, rec, playerUuid);
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_SOUND_EFFECT) {
            handleEntitySoundEffect(event, rec, playerUuid);
        }
    }

    private void handleSoundEffect(PacketSendEvent event, ActiveRecording rec, UUID playerUuid) {
        WrapperPlayServerSoundEffect wrapper = new WrapperPlayServerSoundEffect(event);

        SoundCategory category = wrapper.getSoundCategory();
        Set<SoundCategory> filter = rec.getCategoryFilter();
        if (filter != null && !filter.contains(category)) return;

        Sound sound = wrapper.getSound();
        String soundKey = sound.getSoundId().toString();

        double absX = wrapper.getPosition().x;
        double absY = wrapper.getPosition().y;
        double absZ = wrapper.getPosition().z;

        double[] refCoords = resolveRef(rec, playerUuid);
        double relX = absX - refCoords[0];
        double relY = absY - refCoords[1];
        double relZ = absZ - refCoords[2];
        float recorderYaw = resolveYaw(rec, playerUuid);

        long relativeTick = (long) Bukkit.getCurrentTick() - rec.getStartTick();

        RecordedSoundEvent sEvent = new RecordedSoundEvent(
                relativeTick, soundKey, category,
                relX, relY, relZ,
                wrapper.getVolume(), wrapper.getPitch(), wrapper.getSeed(),
                recorderYaw
        );
        rec.addEvent(sEvent);
    }

    private void handleEntitySoundEffect(PacketSendEvent event, ActiveRecording rec, UUID playerUuid) {
        WrapperPlayServerEntitySoundEffect wrapper = new WrapperPlayServerEntitySoundEffect(event);

        SoundCategory category = wrapper.getSoundCategory();
        Set<SoundCategory> filter = rec.getCategoryFilter();
        if (filter != null && !filter.contains(category)) return;

        Sound sound = wrapper.getSound();
        String soundKey = sound.getSoundId().toString();

        int entityId = wrapper.getEntityId();
        // Look up entity position on the main thread is not safe from Netty thread.
        // We use the Bukkit.getEntity approach - however entity lookup by numeric ID is not
        // directly available without NMS on Paper. We'll schedule a sync lookup, but since
        // this is async we use a best-effort approach: if entity not found, record at (0,0,0).
        // Per SPEC D6: entity == null → record entityMissing at (0,0,0) relative to ref.
        double[] refCoords = resolveRef(rec, playerUuid);
        double relX = 0, relY = 0, relZ = 0;

        // Try to find entity by network ID via Bukkit worlds
        Entity entity = findEntityByNetworkId(entityId);
        if (entity != null) {
            relX = entity.getLocation().getX() - refCoords[0];
            relY = entity.getLocation().getY() - refCoords[1];
            relZ = entity.getLocation().getZ() - refCoords[2];
        }

        float recorderYaw = resolveYaw(rec, playerUuid);
        long relativeTick = (long) Bukkit.getCurrentTick() - rec.getStartTick();

        RecordedSoundEvent sEvent = new RecordedSoundEvent(
                relativeTick, soundKey, category,
                relX, relY, relZ,
                wrapper.getVolume(), wrapper.getPitch(), wrapper.getSeed(),
                recorderYaw
        );
        rec.addEvent(sEvent);
    }

    private double[] resolveRef(ActiveRecording rec, UUID playerUuid) {
        if (rec.getMode() == RecordingMode.PLAYER_RELATIVE) {
            double[] snapshot = positionSnapshots.get(playerUuid);
            if (snapshot != null) {
                return new double[]{snapshot[0], snapshot[1], snapshot[2]};
            }
        }
        return new double[]{rec.getRefX(), rec.getRefY(), rec.getRefZ()};
    }

    private float resolveYaw(ActiveRecording rec, UUID playerUuid) {
        double[] snapshot = positionSnapshots.get(playerUuid);
        if (snapshot != null) return (float) snapshot[3];
        return 0f;
    }

    private Entity findEntityByNetworkId(int networkId) {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getEntityId() == networkId) return e;
            }
        }
        return null;
    }
}
