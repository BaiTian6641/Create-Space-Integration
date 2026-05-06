package com.createmechanomania.spaceintegration.registry;

import com.createmechanomania.spaceintegration.CreateSpaceIntegration;
import com.createmechanomania.spaceintegration.content.StationGravityControllerBlock;
import com.createmechanomania.spaceintegration.content.StationGravityControllerBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SpaceIntegrationRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, CreateSpaceIntegration.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, CreateSpaceIntegration.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, CreateSpaceIntegration.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateSpaceIntegration.MOD_ID);

    public static final DeferredHolder<Block, StationGravityControllerBlock> STATION_GRAVITY_CONTROLLER = BLOCKS.register(
            "station_gravity_controller",
            () -> new StationGravityControllerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .sound(SoundType.COPPER)
                    .strength(6.0F, 8.0F)
                    .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Item, BlockItem> STATION_GRAVITY_CONTROLLER_ITEM = ITEMS.register(
            "station_gravity_controller",
            () -> new BlockItem(STATION_GRAVITY_CONTROLLER.get(), new Item.Properties())
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StationGravityControllerBlockEntity>> STATION_GRAVITY_CONTROLLER_BLOCK_ENTITY = BLOCK_ENTITIES.register(
            "station_gravity_controller",
            () -> BlockEntityType.Builder.of(StationGravityControllerBlockEntity::new, STATION_GRAVITY_CONTROLLER.get()).build(null)
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.create_space_integration.main"))
                    .icon(() -> new ItemStack(STATION_GRAVITY_CONTROLLER_ITEM.get()))
                    .withTabsBefore(CreativeModeTabs.REDSTONE_BLOCKS)
                    .displayItems((parameters, output) -> output.accept(STATION_GRAVITY_CONTROLLER_ITEM.get()))
                    .build()
    );

    private SpaceIntegrationRegistry() {
    }

    public static void register(final IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
    }
}