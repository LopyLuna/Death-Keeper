package dev.lopyluna.death_keeper;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

public class DeathSnapshotData extends SavedData {
    public static final String NAME = "death_snapshots";
    public final List<DeathSnapshot> snapshots = new ArrayList<>();

    public static final Codec<DeathSnapshotData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(DeathSnapshot.CODEC.listOf().fieldOf("Snapshots").forGetter(data -> data.snapshots)).apply(instance, snaps -> {
                var data = new DeathSnapshotData();
                data.snapshots.addAll(snaps);
                return data;
            })
    );

    public static DeathSnapshotData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedDataType<>(NAME, DeathSnapshotData::new, CODEC));
    }

    public static List<DeathSnapshot> getSnapshots(ServerLevel level) {
        var data = DeathSnapshotData.get(level);
        return data.snapshots;
    }

    public static List<DeathSnapshot> getSnapshots(ServerLevel level, BlockPos pos) {
        var snaps = new ArrayList<DeathSnapshot>();
        var dim = level.dimension().identifier();
        for (var snap : getSnapshots(level)) {
            if (!snap.dim.equals(dim)) continue;
            if (!snap.pos.equals(pos)) continue;
            snaps.add(snap);
        }
        return snaps;
    }

    public static void add(ServerLevel level, DeathSnapshot snap) {
        var data = DeathSnapshotData.get(level);
        data.snapshots.add(snap);
        data.setDirty();
    }

    public static void remove(ServerLevel level, DeathSnapshot snap) {
        var data = DeathSnapshotData.get(level);
        data.snapshots.remove(snap);
        data.setDirty();
    }
}
