package cn.dancingsnow.neoecoae.dev;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.grid.AENetworkTile;
import cn.dancingsnow.neoecoae.platform.minecraft.RegistryAccess;
import cn.dancingsnow.neoecoae.platform.nbt.CompoundTag;
import cn.dancingsnow.neoecoae.platform.registry.NativeContent;
import cpw.mods.fml.common.registry.GameRegistry;

/** Native rv3 implementation; explicitly registered only by the development client launcher. */
public final class DevResourceGenerator {

    private static final String DATA = "neoecoae_dev_generator";
    public static final Block BLOCK = new GeneratorBlock();

    public static void register() {
        GameRegistry.registerBlock(BLOCK, GeneratorItem.class, "dev_resource_generator");
        GameRegistry.registerTileEntity(GeneratorTile.class, "neoecoae.dev_resource_generator");
        AEApi.instance()
            .registries()
            .cell()
            .addCellHandler(new CellHandler());
        AEApi.instance()
            .registries()
            .gridCache()
            .registerGridCache(GeneratorGridService.class, GeneratorGridService.class);
    }

    static boolean isGenerator(ItemStack stack) {
        return stack != null && stack.getItem() == Item.getItemFromBlock(BLOCK);
    }

    static CompoundTag readItem(ItemStack stack) {
        return new CompoundTag(
            stack.hasTagCompound() ? stack.getTagCompound()
                .getCompoundTag(DATA) : new NBTTagCompound());
    }

    static void writeItem(ItemStack stack, CompoundTag data) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setTag(DATA, data.nativeTag());
    }

    public static final class GeneratorBlock extends BlockContainer {

        GeneratorBlock() {
            super(Material.iron);
            setBlockName("neoecoae.dev_resource_generator");
            setBlockTextureName("command_block_back");
            setHardness(2);
            setCreativeTab(NativeContent.TAB);
        }

        @Override
        public int getRenderType() {
            return 0;
        }

        @Override
        public TileEntity createNewTileEntity(World world, int metadata) {
            return new GeneratorTile();
        }

        @Override
        public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack stack) {
            super.onBlockPlacedBy(world, x, y, z, player, stack);
            if (!world.isRemote && world.getTileEntity(x, y, z) instanceof GeneratorTile tile) {
                tile.state = GeneratorState.read(readItem(stack), RegistryAccess.EMPTY);
                if (player instanceof EntityPlayer owner) tile.getProxy()
                    .setOwner(owner);
                tile.markDirty();
            }
        }

        @Override
        public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
            var drops = new ArrayList<ItemStack>();
            ItemStack stack = new ItemStack(BLOCK);
            if (world.getTileEntity(x, y, z) instanceof GeneratorTile tile)
                writeItem(stack, tile.state.write(RegistryAccess.EMPTY));
            drops.add(stack);
            return drops;
        }

        @Override
        public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
            return willHarvest || super.removedByPlayer(world, player, x, y, z, false);
        }

        @Override
        public void harvestBlock(World world, EntityPlayer player, int x, int y, int z, int metadata) {
            // Keep the tile until its configured drop has been created.
            super.harvestBlock(world, player, x, y, z, metadata);
            world.setBlockToAir(x, y, z);
        }
    }

    public static final class GeneratorItem extends ItemBlock {

        public GeneratorItem(Block block) {
            super(block);
            setMaxStackSize(1);
        }

        @Override
        public String getItemStackDisplayName(ItemStack stack) {
            return "Dev Random Resource Generator / Cell";
        }

        @Override
        @SuppressWarnings("unchecked")
        public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
            lines.add("DEV: Place and cable, or install in an ME Drive/Chest.");
            lines.add("/neodev set <types> <min> <max> <ticks> [x y z]");
            lines.add("/neodev reroll [x y z] | /neodev info [x y z]");
        }
    }

    public static final class GeneratorTile extends AENetworkTile {

        GeneratorState state = new GeneratorState();

        @Override
        protected AENetworkProxy createProxy() {
            var proxy = new AENetworkProxy(this, "proxy", new ItemStack(BLOCK), true);
            proxy.setIdlePowerUsage(0);
            return proxy;
        }

        @appeng.tile.TileEvent(appeng.tile.events.TileEventType.WORLD_NBT_READ)
        public void readGenerator(NBTTagCompound tag) {
            state = GeneratorState.read(new CompoundTag(tag.getCompoundTag(DATA)), RegistryAccess.EMPTY);
        }

        @appeng.tile.TileEvent(appeng.tile.events.TileEventType.WORLD_NBT_WRITE)
        public void writeGenerator(NBTTagCompound tag) {
            tag.setTag(
                DATA,
                state.write(RegistryAccess.EMPTY)
                    .nativeTag());
        }
    }

    public static final class GeneratorCommand extends CommandBase {

        @Override
        public String getCommandName() {
            return "neodev";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 2;
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/neodev give | set <types> <min> <max> <ticks> [x y z] | reroll [x y z] | info [x y z]";
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if (args.length == 0) throw new WrongUsageException(getCommandUsage(sender));
            var player = getCommandSenderAsPlayer(sender);
            if (args[0].equals("give") && args.length == 1) {
                var stack = new ItemStack(BLOCK);
                if (!player.inventory.addItemStackToInventory(stack))
                    player.dropPlayerItemWithRandomChoice(stack, false);
                return;
            }
            boolean configure = args[0].equals("set");
            if (!configure && !args[0].equals("reroll") && !args[0].equals("info"))
                throw new WrongUsageException(getCommandUsage(sender));
            int base = configure ? 5 : 1;
            if (args.length != base && args.length != base + 3) throw new WrongUsageException(getCommandUsage(sender));
            GeneratorTile tile = null;
            ItemStack stack = player.getHeldItem();
            if (args.length > base) {
                int x = parseInt(sender, args[base]), y = parseInt(sender, args[base + 1]),
                    z = parseInt(sender, args[base + 2]);
                if (!player.worldObj.blockExists(x, y, z)
                    || !(player.worldObj.getTileEntity(x, y, z) instanceof GeneratorTile))
                    throw new WrongUsageException("No loaded dev generator at these coordinates");
                tile = (GeneratorTile) player.worldObj.getTileEntity(x, y, z);
            } else if (!isGenerator(stack))
                throw new WrongUsageException("Hold a dev generator, or append its placed block coordinates");
            try {
                GeneratorState state = tile == null ? GeneratorState.read(readItem(stack), RegistryAccess.EMPTY)
                    : tile.state;
                if (configure) state.configure(
                    new GeneratorSettings(
                        parseInt(sender, args[1]),
                        Long.parseLong(args[2]),
                        Long.parseLong(args[3]),
                        parseInt(sender, args[4])));
                if (args[0].equals("reroll")) state.reroll();
                if (!args[0].equals("info")) {
                    if (tile == null) {
                        writeItem(stack, state.write(RegistryAccess.EMPTY));
                        player.inventory.markDirty();
                    } else tile.markDirty();
                }
                sender.addChatMessage(new ChatComponentText(state.describe()));
                if (args[0].equals("info"))
                    for (var key : state.selected.subList(0, Math.min(32, state.selected.size())))
                        sender.addChatMessage(new ChatComponentText(key.getDisplayName()));
            } catch (IllegalArgumentException error) {
                throw new WrongUsageException(error.getMessage());
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static final class EmptyCell implements IMEInventoryHandler {

        private final StorageChannel channel;

        EmptyCell(StorageChannel channel) {
            this.channel = channel;
        }

        @Override
        public IAEStack injectItems(IAEStack input, Actionable mode, BaseActionSource source) {
            return input;
        }

        @Override
        public IAEStack extractItems(IAEStack request, Actionable mode, BaseActionSource source) {
            return null;
        }

        @Override
        public IItemList getAvailableItems(IItemList out, int iteration) {
            return out;
        }

        @Override
        public StorageChannel getChannel() {
            return channel;
        }

        @Override
        public AccessRestriction getAccess() {
            return AccessRestriction.NO_ACCESS;
        }

        @Override
        public boolean isPrioritized(IAEStack input) {
            return false;
        }

        @Override
        public boolean canAccept(IAEStack input) {
            return false;
        }

        @Override
        public int getPriority() {
            return 0;
        }

        @Override
        public int getSlot() {
            return 0;
        }

        @Override
        public boolean validForPass(int pass) {
            return false;
        }
    }

    @SuppressWarnings("rawtypes")
    static final class CellHandler implements ICellHandler {

        @Override
        public boolean isCell(ItemStack stack) {
            return isGenerator(stack);
        }

        @Override
        public IMEInventoryHandler getCellInventory(ItemStack stack, ISaveProvider host, StorageChannel channel) {
            return isCell(stack) ? new EmptyCell(channel) : null;
        }

        @Override
        public int getStatusForCell(ItemStack stack, IMEInventory inventory) {
            return 1;
        }

        @Override
        public double cellIdleDrain(ItemStack stack, IMEInventory inventory) {
            return 0;
        }

        @Override
        @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
        public IIcon getTopTexture_Light() {
            return appeng.client.texture.ExtraBlockTextures.BlockMEChestItems_Light.getIcon();
        }

        @Override
        @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
        public IIcon getTopTexture_Medium() {
            return appeng.client.texture.ExtraBlockTextures.BlockMEChestItems_Medium.getIcon();
        }

        @Override
        @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
        public IIcon getTopTexture_Dark() {
            return appeng.client.texture.ExtraBlockTextures.BlockMEChestItems_Dark.getIcon();
        }

        @Override
        public void openChestGui(EntityPlayer player, IChestOrDrive chest, ICellHandler handler,
            IMEInventoryHandler inventory, ItemStack stack, StorageChannel channel) {
            appeng.util.Platform.openGUI(player, (TileEntity) chest, chest.getUp(), appeng.core.sync.GuiBridge.GUI_ME);
        }
    }
}
