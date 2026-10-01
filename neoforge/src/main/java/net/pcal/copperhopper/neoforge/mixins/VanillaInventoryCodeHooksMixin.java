package net.pcal.copperhopper.neoforge.mixins;

import static net.pcal.copperhopper.common.CopperHopperMod.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.pcal.copperhopper.common.CopperInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.neoforged.neoforge.items.VanillaInventoryCodeHooks", remap = false)
public abstract class VanillaInventoryCodeHooksMixin {

    // ---- pull: handler above -> copper hopper (or copper minecart) ----
    @Inject(method = "extractHook(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/Hopper;)Ljava/lang/Boolean;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void copperhopper$extractHook(Level level, Hopper dest, CallbackInfoReturnable<Boolean> cir) {
        if (!(dest instanceof CopperInventory)) return;
        cir.setReturnValue(copperhopper$pull(level, dest));
    }

    private static Boolean copperhopper$pull(Level level, Hopper dest) {
        final BlockPos source = BlockPos.containing(dest.getLevelX(), dest.getLevelY() + 1.0D, dest.getLevelZ());
        if (copperhopper$isVanillaContainer(level, source)) return null;   // vanilla path filters
        final IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, source, Direction.DOWN);
        if (handler == null) return null;                                  // let vanilla do its thing
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            final ItemStack candidate = handler.extractItem(slot, 1, true);
            if (candidate.isEmpty()) continue;
            if (mod().shouldVetoPullInto(dest, candidate)) continue;
            final int room = copperhopper$findRoom(dest, candidate);
            if (room < 0) continue;
            final ItemStack taken = handler.extractItem(slot, 1, false);
            if (taken.isEmpty()) continue;
            final ItemStack present = dest.getItem(room);
            if (present.isEmpty()) dest.setItem(room, taken.copy());
            else present.grow(taken.getCount());
            dest.setChanged();
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    private static int copperhopper$findRoom(Hopper dest, ItemStack stack) {
        final int limit = Math.min(dest.getMaxStackSize(), stack.getMaxStackSize());
        for (int j = 0; j < dest.getContainerSize(); j++) {            // first: top up an existing stack
            final ItemStack present = dest.getItem(j);
            if (!present.isEmpty() && present.getCount() < limit && ItemStack.isSameItemSameComponents(present, stack)) return j;
        }
        for (int j = 0; j < dest.getContainerSize(); j++) {            // then: an empty slot
            if (dest.getItem(j).isEmpty()) return j;
        }
        return -1;
    }

    // ---- push: copper hopper -> handler in front ----
    @Inject(method = "insertHook(Lnet/minecraft/world/level/block/entity/HopperBlockEntity;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void copperhopper$insertHook(HopperBlockEntity hopper, CallbackInfoReturnable<Boolean> cir) {
        if (!(hopper instanceof CopperInventory)) return;
        cir.setReturnValue(copperhopper$push(hopper));
    }

    private static boolean copperhopper$push(HopperBlockEntity hopper) {
        final Level level = hopper.getLevel();
        if (level == null) return false;
        final Direction facing = hopper.getBlockState().getValue(HopperBlock.FACING);
        final BlockPos target = hopper.getBlockPos().relative(facing);
        if (copperhopper$isVanillaContainer(level, target)) return false;  // vanilla path filters
        final IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, target, facing.getOpposite());
        if (handler == null) return false;
        for (int i = 0; i < hopper.getContainerSize(); i++) {
            final ItemStack stack = hopper.getItem(i);
            if (stack.isEmpty()) continue;
            if (mod().shouldVetoPushFrom(hopper, stack, level, hopper.getBlockPos())) continue;
            final ItemStack remainder = ItemHandlerHelper.insertItem(handler, stack.copyWithCount(1), false);
            if (remainder.isEmpty()) {
                hopper.removeItem(i, 1);
                return true;
            }
        }
        return false;
    }

    private static boolean copperhopper$isVanillaContainer(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof WorldlyContainerHolder) return true;
        return level.getBlockEntity(pos) instanceof Container;
    }
}