/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2022-2023 pcal.net
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package net.pcal.copperhopper.neoforge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.pcal.copperhopper.common.CohoScreenHandler;
import net.pcal.copperhopper.common.CopperHopperBlock;
import net.pcal.copperhopper.common.CopperHopperBlockEntity;
import net.pcal.copperhopper.common.CopperHopperItem;
import net.pcal.copperhopper.common.CopperHopperMinecartEntity;
import net.pcal.copperhopper.common.CopperHopperMinecartItem;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Set;

import static net.pcal.copperhopper.common.CopperHopperMod.COHO_MINECART_ITEM_ID;
import static net.pcal.copperhopper.common.CopperHopperMod.LOGGER_NAME;
import static net.pcal.copperhopper.common.CopperHopperMod.LOG_PREFIX;
import static net.pcal.copperhopper.common.CopperHopperMod.mod;

@Mod("copperhopper")
public class NeoForgeCopperHopperMod {

    private static final String NS = "copperhopper";

    private static final Logger LOGGER = LogManager.getLogger(LOGGER_NAME);
    private static DeferredHolder<Item, CopperHopperItem> cohoItemHolder;

    public NeoForgeCopperHopperMod(IEventBus modBus) {
        LOGGER.info(LOG_PREFIX + "Mod constructor called");
        modBus.addListener(NeoForgeCopperHopperMod::onCommonSetup);
        modBus.addListener(NeoForgeCopperHopperMod::onBuildCreativeTab);
        NeoForge.EVENT_BUS.addListener(NeoForgeCopperHopperMod::onServerStarting);
        registerBlocksAndItems(modBus);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info(LOG_PREFIX + "FMLCommonSetupEvent fired");
        try {
            mod().createDefaultConfig();
        } catch (Exception e) {
            LOGGER.catching(Level.ERROR, e);
            LOGGER.error(LOG_PREFIX + "Failed to create default config");
        }
        // All registries are fully bound by FMLCommonSetupEvent — safe to call registerBlocks now
        CopperHopperItem item = cohoItemHolder.get();
        item.registerBlocks(Item.BY_BLOCK, item);
    }

    private static void onServerStarting(ServerStartingEvent event) {
        LOGGER.info(LOG_PREFIX + "ServerStartingEvent fired");
        try {
            mod().loadConfig();
        } catch (Exception e) {
            LOGGER.catching(Level.ERROR, e);
            LOGGER.error(LOG_PREFIX + "Failed to load config");
        }
    }

    private static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.REDSTONE_BLOCKS) return;
        event.insertAfter(new ItemStack(Items.HOPPER), new ItemStack(cohoItemHolder.get()),
                CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);

        Item minecartItem = BuiltInRegistries.ITEM.get(COHO_MINECART_ITEM_ID);
        event.insertAfter(new ItemStack(Items.HOPPER_MINECART), new ItemStack(minecartItem),
                CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    private static void registerBlocksAndItems(IEventBus modBus) {
        // --- Menu / Screen ---
        DeferredRegister<MenuType<?>> menuTypes = DeferredRegister.create(Registries.MENU, NS);
        menuTypes.register("copper_hopper", () -> new MenuType<>(CohoScreenHandler::new, FeatureFlags.VANILLA_SET));
        menuTypes.register(modBus);

        // --- Block and Block Item ---
        DeferredRegister<Block> blocks = DeferredRegister.create(Registries.BLOCK, NS);
        DeferredRegister<Item> items = DeferredRegister.create(Registries.ITEM, NS);

        final DeferredHolder<Block, CopperHopperBlock> blockHolder =
                blocks.register("copper_hopper", () -> new CopperHopperBlock(CopperHopperBlock.getDefaultSettings()));

        cohoItemHolder = items.register("copper_hopper",
                () -> new CopperHopperItem(blockHolder.get(), new Item.Properties()));

        // --- Block Entity ---
        DeferredRegister<BlockEntityType<?>> blockEntityTypes = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NS);
        blockEntityTypes.register("copper_hopper_entity", () ->
                new BlockEntityType<>(CopperHopperBlockEntity::new, Set.of(blockHolder.get()), null));

        // --- Minecart Entity ---
        DeferredRegister<EntityType<?>> entityTypes = DeferredRegister.create(Registries.ENTITY_TYPE, NS);
        entityTypes.register("copper_hopper_minecart",
                () -> EntityType.Builder.<CopperHopperMinecartEntity>of(CopperHopperMinecartEntity::new, MobCategory.MISC)
                        .sized(0.98f, 0.7f)
                        .build("copper_hopper_minecart"));

        // --- Minecart Item ---
        items.register("copper_hopper_minecart",
                () -> new CopperHopperMinecartItem(new Item.Properties().stacksTo(1)));

        // Commit all deferred registers to the mod event bus
        blocks.register(modBus);
        items.register(modBus);
        blockEntityTypes.register(modBus);
        entityTypes.register(modBus);
    }
}