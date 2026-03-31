package dev.lopyluna.death_keeper;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class DeathSnapshot {
    public ResourceLocation dim;
    public int lifespan = Config.maxGraveLifespan;
    public BlockPos pos;
    public Direction dir;
    public int exp;
    public List<ItemStack> inv;
    @Nullable public UUID uuidText;
    public UUID uuid;
    public EntityType<?> entityType;

    public static final Codec<DeathSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("dim").forGetter(s -> s.dim),
            Codec.INT.optionalFieldOf("lifespan", Config.maxGraveLifespan).forGetter(s -> s.lifespan),
            BlockPos.CODEC.fieldOf("pos").forGetter(s -> s.pos),
            Direction.CODEC.fieldOf("dir").forGetter(s -> s.dir),
            Codec.INT.fieldOf("exp").forGetter(s -> s.exp),
            ItemStack.CODEC.listOf().fieldOf("inv").forGetter(s -> s.inv),
            UUIDUtil.CODEC.optionalFieldOf("uuidText").forGetter(s -> java.util.Optional.ofNullable(s.uuidText)),
            UUIDUtil.CODEC.fieldOf("uuid").forGetter(s -> s.uuid),
            EntityType.CODEC.fieldOf("type").forGetter(s -> s.entityType)
            ).apply(instance, (dim, lifespan, pos, dir, exp, inv, uuidTextOpt, uuid, type) -> {
                DeathSnapshot snap = new DeathSnapshot();
                snap.dim = dim;
                snap.lifespan = lifespan;
                snap.pos = pos;
                snap.dir = dir;
                snap.exp = exp;
                snap.inv = inv;
                snap.uuidText = uuidTextOpt.orElse(null);
                snap.uuid = uuid;
                snap.entityType = type;
                return snap;
            })
    );
}
