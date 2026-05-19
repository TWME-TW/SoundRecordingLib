package dev.twme.soundRecordingLib.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import com.github.retrooper.packetevents.protocol.sound.SoundCategory;

import dev.twme.soundRecordingLib.data.RecordingMode;

public final class RecordingOptions {

    private final String name;
    private final RecordingMode mode;
    private final @Nullable Location referencePos;
    private final @Nullable Set<SoundCategory> categoryFilter;

    private RecordingOptions(Builder builder) {
        this.name = builder.name;
        this.mode = builder.mode;
        this.referencePos = builder.referencePos;
        this.categoryFilter = builder.categoryFilter == null
                ? null
                : Collections.unmodifiableSet(EnumSet.copyOf(builder.categoryFilter));
    }

    public String getName() { return name; }
    public RecordingMode getMode() { return mode; }
    public @Nullable Location getReferencePos() { return referencePos; }
    public @Nullable Set<SoundCategory> getCategoryFilter() { return categoryFilter; }

    public static Builder builder(String name) { return new Builder(name); }

    public static final class Builder {
        private final String name;
        private RecordingMode mode = RecordingMode.STATIC;
        private @Nullable Location referencePos = null;
        private @Nullable Set<SoundCategory> categoryFilter = null;

        public Builder(String name) {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
            this.name = name;
        }

        public Builder mode(RecordingMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder reference(Location pos) {
            this.referencePos = pos;
            return this;
        }

        public Builder filter(SoundCategory... categories) {
            if (categories.length == 0) {
                this.categoryFilter = null;
            } else {
                Set<SoundCategory> set = EnumSet.noneOf(SoundCategory.class);
                for (SoundCategory c : categories) set.add(c);
                this.categoryFilter = set;
            }
            return this;
        }

        public RecordingOptions build() { return new RecordingOptions(this); }
    }
}
