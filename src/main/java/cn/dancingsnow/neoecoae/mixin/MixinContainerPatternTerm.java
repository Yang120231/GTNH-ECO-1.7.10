package cn.dancingsnow.neoecoae.mixin;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.api.networking.IGridNode;
import appeng.api.parts.IPatternTerminal;
import appeng.api.storage.StorageName;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerPatternTerm;
import cn.dancingsnow.neoecoae.crafting.upload.PatternTermUploadExtension;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadSession;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadSessions;
import cn.dancingsnow.neoecoae.gui.mui.NeoEcoUiFactory;

@Mixin(value = ContainerPatternTerm.class, remap = false)
public abstract class MixinContainerPatternTerm implements PatternTermUploadExtension {

    @Shadow
    public abstract IPatternTerminal getPatternTerminal();

    @Invoker("encode")
    protected abstract void neoecoae$invokeEncode();

    @Override
    public void neoecoae$encodeAndPrepareUpload() {
        EntityPlayerMP player = (EntityPlayerMP) ((AEBaseContainer) (Object) this).getPlayerInv().player;
        this.neoecoae$invokeEncode();
        IInventory patternInventory = this.getPatternTerminal()
            .getInventoryByName(StorageName.CRAFTING_PATTERN.getName());
        ItemStack pattern = patternInventory == null ? null : patternInventory.getStackInSlot(1);
        IGridNode node = this.getPatternTerminal()
            .getActionableNode();
        if (pattern == null || node == null || !node.isActive() || node.getGrid() == null) return;
        PatternUploadSession session = PatternUploadSessions.create(
            player,
            node.getGrid(),
            node,
            pattern,
            patternInventory,
            1,
            !this.getPatternTerminal()
                .isCraftingRecipe());
        NeoEcoUiFactory.openUpload(player, session.getId());
    }
}
