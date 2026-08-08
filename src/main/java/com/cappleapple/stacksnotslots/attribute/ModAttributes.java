package com.cappleapple.stacksnotslots.attribute;

import com.cappleapple.stacksnotslots.StacksNotSlots;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModAttributes {
    public static final double DEFAULT_CAPACITY = 1728.0;
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, StacksNotSlots.MOD_ID);
    public static final DeferredHolder<Attribute, Attribute> INVENTORY_CAPACITY = ATTRIBUTES.register(
            "inventory_capacity",
            () -> new RangedAttribute("attribute.name.stacksnotslots.inventory_capacity", DEFAULT_CAPACITY, 0, Integer.MAX_VALUE).setSyncable(true)
    );

    private ModAttributes() {}

    public static void addPlayerAttributes(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, INVENTORY_CAPACITY);
    }
}
