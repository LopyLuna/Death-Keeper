package dev.lopyluna.death_keeper;

import com.mojang.logging.LogUtils;
import dev.lopyluna.death_keeper.gravestone.GravestoneBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(DeathKeeper.MOD_ID)
public class DeathKeeper {
    public static final String MOD_ID = "death_keeper";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    public static final TagKey<Block> GRAVESTONE_REPLACEABLE = TagKey.create(Registries.BLOCK, loc("gravestone_replaceable"));

    public static final DeferredBlock<Block> GRAVESTONE = BLOCKS.registerBlock("gravestone", GravestoneBlock::new,
            () -> BlockBehaviour.Properties.of().strength(1.5f, 6).requiresCorrectToolForDrops().noOcclusion().mapColor(MapColor.STONE));
    public static final DeferredItem<BlockItem> GRAVESTONE_ITEM = ITEMS.registerSimpleBlockItem("gravestone", GRAVESTONE);


    public DeathKeeper(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);

        modEventBus.addListener(this::addCreative);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) event.accept(GRAVESTONE_ITEM);
    }

    public static ResourceLocation loc(String loc) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, loc);
    }
}
