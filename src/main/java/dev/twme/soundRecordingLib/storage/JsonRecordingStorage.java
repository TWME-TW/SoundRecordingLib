package dev.twme.soundRecordingLib.storage;

import com.github.retrooper.packetevents.protocol.sound.SoundCategory;
import com.google.gson.*;
import dev.twme.soundRecordingLib.data.RecordedSoundEvent;
import dev.twme.soundRecordingLib.data.RecordingMode;
import dev.twme.soundRecordingLib.data.SoundRecording;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class JsonRecordingStorage implements RecordingStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path storageDir;

    public JsonRecordingStorage(File pluginDataFolder) {
        this.storageDir = pluginDataFolder.toPath().resolve("recordings");
    }

    private void ensureDir() throws IOException {
        Files.createDirectories(storageDir);
    }

    private Path fileFor(String name) {
        // Sanitize name: allow only safe characters to prevent path traversal
        String safe = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return storageDir.resolve(safe + ".json");
    }

    @Override
    public void save(SoundRecording recording) throws IOException {
        ensureDir();
        JsonObject obj = new JsonObject();
        obj.addProperty("id", recording.id().toString());
        obj.addProperty("name", recording.name());
        obj.addProperty("createdAt", recording.createdAt());
        obj.addProperty("totalTicks", recording.totalTicks());
        obj.addProperty("worldName", recording.worldName());
        obj.addProperty("mode", recording.mode().name());
        obj.addProperty("refX", recording.refX());
        obj.addProperty("refY", recording.refY());
        obj.addProperty("refZ", recording.refZ());

        JsonArray eventsArray = new JsonArray();
        for (RecordedSoundEvent e : recording.events()) {
            JsonObject ev = new JsonObject();
            ev.addProperty("relativeTick", e.relativeTick());
            ev.addProperty("soundKey", e.soundKey());
            ev.addProperty("category", e.category().name());
            ev.addProperty("relX", e.relX());
            ev.addProperty("relY", e.relY());
            ev.addProperty("relZ", e.relZ());
            ev.addProperty("volume", e.volume());
            ev.addProperty("pitch", e.pitch());
            ev.addProperty("seed", e.seed());
            ev.addProperty("recorderYaw", e.recorderYaw());
            eventsArray.add(ev);
        }
        obj.add("events", eventsArray);

        try (Writer w = new OutputStreamWriter(
                Files.newOutputStream(fileFor(recording.name())), StandardCharsets.UTF_8)) {
            GSON.toJson(obj, w);
        }
    }

    @Override
    public Optional<SoundRecording> load(String name) throws IOException {
        Path path = fileFor(name);
        if (!Files.exists(path)) return Optional.empty();

        try (Reader r = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            JsonObject obj = GSON.fromJson(r, JsonObject.class);
            return Optional.of(parseRecording(obj));
        }
    }

    @Override
    public List<String> listNames() throws IOException {
        ensureDir();
        try (var stream = Files.list(storageDir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replace(".json", ""))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    @Override
    public boolean delete(String name) throws IOException {
        Path path = fileFor(name);
        return Files.deleteIfExists(path);
    }

    private SoundRecording parseRecording(JsonObject obj) {
        UUID id = UUID.fromString(obj.get("id").getAsString());
        String name = obj.get("name").getAsString();
        long createdAt = obj.get("createdAt").getAsLong();
        long totalTicks = obj.get("totalTicks").getAsLong();
        String worldName = obj.get("worldName").getAsString();
        RecordingMode mode = RecordingMode.valueOf(obj.get("mode").getAsString());
        double refX = obj.get("refX").getAsDouble();
        double refY = obj.get("refY").getAsDouble();
        double refZ = obj.get("refZ").getAsDouble();

        List<RecordedSoundEvent> events = new ArrayList<>();
        for (JsonElement el : obj.getAsJsonArray("events")) {
            JsonObject ev = el.getAsJsonObject();
            long relativeTick = ev.get("relativeTick").getAsLong();
            String soundKey = ev.get("soundKey").getAsString();
            SoundCategory category = SoundCategory.valueOf(ev.get("category").getAsString());
            double relX = ev.get("relX").getAsDouble();
            double relY = ev.get("relY").getAsDouble();
            double relZ = ev.get("relZ").getAsDouble();
            float volume = ev.get("volume").getAsFloat();
            float pitch = ev.get("pitch").getAsFloat();
            long seed = ev.get("seed").getAsLong();
            float recorderYaw = ev.get("recorderYaw").getAsFloat();
            events.add(new RecordedSoundEvent(relativeTick, soundKey, category,
                    relX, relY, relZ, volume, pitch, seed, recorderYaw));
        }

        return new SoundRecording(id, name, createdAt, totalTicks, worldName, mode,
                refX, refY, refZ, Collections.unmodifiableList(events));
    }
}
