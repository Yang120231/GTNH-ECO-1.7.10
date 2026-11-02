package cn.dancingsnow.neoecoae.command;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import cpw.mods.fml.common.event.FMLServerStartingEvent;

/**
 * Operator-only {@code /neoecoae} command, 1.21's Brigadier tree as a 1.7.10 server command. Only the subcommands
 * this port supports are registered.
 */
public final class NECommands extends CommandBase {

    private static final String EXPORT_PLAN = "export_plan";

    public static void register(FMLServerStartingEvent event) {
        event.registerServerCommand(new NECommands());
    }

    @Override
    public String getCommandName() {
        return "neoecoae";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/neoecoae " + EXPORT_PLAN;
    }

    /** The same operator level as 1.21's {@code hasPermission(2)}. */
    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) {
        if (arguments.length == 1 && EXPORT_PLAN.equals(arguments[0])) {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            CraftingPlanExport.arm(player);
            sender.addChatMessage(
                new ChatComponentText(
                    "The next crafting calculation you open will be exported with all network patterns."));
            return;
        }
        throw new WrongUsageException(getCommandUsage(sender));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] arguments) {
        return arguments.length == 1 ? getListOfStringsMatchingLastWord(arguments, EXPORT_PLAN) : null;
    }
}
