package cn.dancingsnow.neoecoae.mixin;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.networking.IGridNode;
import appeng.api.parts.IPatternTerminal;
import appeng.api.storage.StorageName;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerPatternTerm;
import cn.dancingsnow.neoecoae.crafting.upload.PatternRouteKey;
import cn.dancingsnow.neoecoae.crafting.upload.PatternTermUploadExtension;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadSession;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadSessions;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;
import cn.dancingsnow.neoecoae.gui.mui.NeoEcoUiFactory;
import cn.dancingsnow.neoecoae.network.NEPatternUploadNetwork;

@Mixin(value = ContainerPatternTerm.class, remap = false)
public abstract class MixinContainerPatternTerm implements PatternTermUploadExtension {

    private int neoecoae$tooltipTicks;
    private String neoecoae$lastAutoTarget = "";
    private String neoecoae$routeMapId = "";
    private ItemStack neoecoae$routeCircuit;
    private PatternUploadSession neoecoae$tooltipSession;
    private long neoecoae$tooltipSessionCreatedAt;
    private boolean neoecoae$prepareAfterEncode;

    private static final long NEOECOAE_TOOLTIP_CACHE_MILLIS = 1_000L;

    @Shadow
    public abstract IPatternTerminal getPatternTerminal();

    @Invoker("encode")
    protected abstract void neoecoae$invokeEncode();

    @Inject(method = "encode", at = @At("RETURN"))
    private void neoecoae$afterEncode(CallbackInfo ci) {
        this.neoecoae$lastAutoTarget = null;
        this.neoecoae$tooltipSession = null;
        this.neoecoae$sendAutoTargetTooltip();
        if (!this.neoecoae$prepareAfterEncode) this.neoecoae$clearRouteContext();
    }

    @Inject(method = "detectAndSendChanges", at = @At("HEAD"))
    private void neoecoae$refreshAutoUploadTooltip(CallbackInfo ci) {
        if (++this.neoecoae$tooltipTicks < 10) return;
        this.neoecoae$tooltipTicks = 0;
        this.neoecoae$sendAutoTargetTooltip();
    }

    private void neoecoae$sendAutoTargetTooltip() {
        EntityPlayerMP player = (EntityPlayerMP) ((AEBaseContainer) (Object) this).getPlayerInv().player;
        IInventory patternInventory = this.getPatternTerminal()
            .getInventoryByName(StorageName.CRAFTING_PATTERN.getName());
        ItemStack pattern = patternInventory == null ? null : patternInventory.getStackInSlot(1);
        IGridNode node = this.getPatternTerminal()
            .getActionableNode();
        PatternRouteKey routeKey = this.neoecoae$routeKey();
        PatternUploadSession session = this.neoecoae$tooltipSession;
        long now = System.currentTimeMillis();
        if (session == null || now - this.neoecoae$tooltipSessionCreatedAt > NEOECOAE_TOOLTIP_CACHE_MILLIS
            || session.isExpired()
            || !session.matchesPattern(pattern)
            || !session.matchesRouteKey(routeKey)) {
            session = pattern == null || node == null || !node.isActive() || node.getGrid() == null ? null
                : PatternUploadSession.create(
                    player,
                    node.getGrid(),
                    node,
                    pattern,
                    patternInventory,
                    1,
                    !this.getPatternTerminal()
                        .isCraftingRecipe(),
                    routeKey);
            this.neoecoae$tooltipSession = session;
            this.neoecoae$tooltipSessionCreatedAt = now;
        }
        PatternUploadTarget target = session == null ? null : session.getAutoUploadTarget();
        ItemStack autoCircuit = session == null ? null : session.getAutoUploadCircuit();
        ItemStack targetCircuit = session == null || target == null ? null
            : target.getCircuit(session.getRouteKey(), session.getPatternDetails());
        String key = (target == null ? "" : target.getName() + "|" + target.getKind()) + "|"
            + String.valueOf(targetCircuit)
            + "|"
            + String.valueOf(autoCircuit)
            + "|"
            + String.valueOf(pattern);
        if (!key.equals(this.neoecoae$lastAutoTarget)) {
            this.neoecoae$lastAutoTarget = key;
            NEPatternUploadNetwork.sendAutoTarget(
                player,
                target,
                autoCircuit,
                target == null ? "" : target.getTooltipName(session.getRouteKey(), session.getPatternDetails()));
        }
    }

    @Override
    public void neoecoae$encodeAndPrepareUpload() {
        EntityPlayerMP player = (EntityPlayerMP) ((AEBaseContainer) (Object) this).getPlayerInv().player;
        IInventory patternInventory = this.getPatternTerminal()
            .getInventoryByName(StorageName.CRAFTING_PATTERN.getName());
        ItemStack before = patternInventory == null ? null : patternInventory.getStackInSlot(1);
        this.neoecoae$prepareAfterEncode = true;
        try {
            this.neoecoae$invokeEncode();
            ItemStack pattern = patternInventory == null ? null : patternInventory.getStackInSlot(1);
            IGridNode node = this.getPatternTerminal()
                .getActionableNode();
            boolean changed = before == null ? pattern != null
                : pattern != null && (!before.isItemEqual(pattern) || !ItemStack.areItemStackTagsEqual(before, pattern)
                    || before.stackSize != pattern.stackSize);
            // Right-click is an encode-and-prepare action. A failed encode must leave the stock AE2
            // error state untouched and must not open an upload page for an older pattern.
            if (changed && node != null && node.isActive() && node.getGrid() != null) {
                openUpload(player, patternInventory, pattern, node, false, true);
            }
        } finally {
            this.neoecoae$prepareAfterEncode = false;
            this.neoecoae$clearRouteContext();
        }
    }

    @Override
    public void neoecoae$openUpload(boolean autoUpload) {
        EntityPlayerMP player = (EntityPlayerMP) ((AEBaseContainer) (Object) this).getPlayerInv().player;
        IInventory patternInventory = this.getPatternTerminal()
            .getInventoryByName(StorageName.CRAFTING_PATTERN.getName());
        ItemStack pattern = patternInventory == null ? null : patternInventory.getStackInSlot(1);
        IGridNode node = this.getPatternTerminal()
            .getActionableNode();
        this.neoecoae$lastAutoTarget = null;
        this.neoecoae$sendAutoTargetTooltip();
        openUpload(player, patternInventory, pattern, node, autoUpload, false);
    }

    @Override
    public void neoecoae$setRouteContext(String recipeMapId, ItemStack circuit) {
        this.neoecoae$routeMapId = recipeMapId == null ? "" : recipeMapId.trim();
        this.neoecoae$routeCircuit = circuit == null ? null : circuit.copy();
    }

    private PatternRouteKey neoecoae$routeKey() {
        if (this.neoecoae$routeMapId.isEmpty()) return null;
        return new PatternRouteKey(this.neoecoae$routeMapId, this.neoecoae$routeCircuit);
    }

    private void openUpload(EntityPlayerMP player, IInventory patternInventory, ItemStack pattern, IGridNode node,
        boolean autoUpload, boolean forceNew) {
        if (patternInventory == null || node == null || !node.isActive() || node.getGrid() == null) {
            this.neoecoae$clearRouteContext();
            return;
        }
        PatternUploadSession session = forceNew ? null
            : PatternUploadSessions.findForSource(player, patternInventory, 1);
        PatternRouteKey routeKey = this.neoecoae$routeKey();
        if (session != null
            && (session.isUploaded() || !session.matchesPattern(pattern) || !session.matchesRouteKey(routeKey)))
            session = null;
        if (session == null) {
            session = PatternUploadSessions.create(
                player,
                node.getGrid(),
                node,
                pattern,
                patternInventory,
                1,
                !this.getPatternTerminal()
                    .isCraftingRecipe(),
                routeKey);
        }
        if (autoUpload && !session.isUploaded()) {
            if (session.autoUpload()) {
                player.addChatMessage(
                    new net.minecraft.util.ChatComponentTranslation("gui.neoecoae.pattern_upload.success"));
                this.neoecoae$clearRouteContext();
                return;
            }
        }
        NeoEcoUiFactory.openUpload(player, session.getId());
        this.neoecoae$clearRouteContext();
    }

    private void neoecoae$clearRouteContext() {
        this.neoecoae$routeMapId = "";
        this.neoecoae$routeCircuit = null;
        this.neoecoae$tooltipSession = null;
    }
}
