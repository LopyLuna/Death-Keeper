package dev.lopyluna.death_keeper.kuramastone;

import dev.lopyluna.death_keeper.DeathSnapshot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

public class GravestoneRemovedEvent extends Event {

    private final DeathSnapshot data;

    public GravestoneRemovedEvent(DeathSnapshot itemStack) {
        this.data = itemStack;
    }

    public DeathSnapshot getData() {
        return data;
    }
}