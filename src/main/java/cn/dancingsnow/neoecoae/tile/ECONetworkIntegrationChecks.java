package cn.dancingsnow.neoecoae.tile;

import java.util.ArrayList;
import java.util.List;

import appeng.api.networking.IGrid;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.block.BlockECONetworkSwitch;

/** Opt-in host grouping checks in the Forge runtime; no player blocks are changed. */
public final class ECONetworkIntegrationChecks {

    private ECONetworkIntegrationChecks() {}

    public static void run() {
        IGrid grid = (IGrid) java.lang.reflect.Proxy.newProxyInstance(
            IGrid.class.getClassLoader(),
            new Class<?>[] { IGrid.class },
            (proxy, method, args) -> null);
        List<Host> hosts = new ArrayList<>();
        try {
            for (int i = 8; i >= 0; i--) {
                Host host = new Host(grid);
                host.xCoord = 10000 + i;
                host.setWorldObj(
                    net.minecraft.server.MinecraftServer.getServer()
                        .worldServerForDimension(0));
                host.setNetworkFrequency(1);
                hosts.add(host);
                ECOControllerRegistry.register(host);
            }
            Host leader = hosts.get(8);
            List<TileECOController> group = leader.getNetworkMembers();
            require(group.size() == 8 && group.get(0) == leader, "stable sorted eight-host partition");
            require(
                hosts.get(0)
                    .getNetworkMembers()
                    .size() == 1,
                "ninth host partition");
            leader.setCraftingOverclocked(true);
            for (TileECOController member : group) require(member.isCraftingOverclocked(), "shared settings");
            Host moved = hosts.get(4);
            moved.setNetworkFrequency(2);
            require(
                moved.getNetworkMembers()
                    .size() == 1,
                "frequency split");
            require(
                leader.getNetworkMembers()
                    .size() == 8,
                "partition refill");
            ECOControllerRegistry.unregister(leader);
            require(
                hosts.get(7)
                    .getNetworkMembers()
                    .get(0) == hosts.get(7),
                "leader unload");
            NeoECOAE.LOG.info(
                "ECO network integration checks PASSED: partition, shared settings, frequency split, leader unload");
        } finally {
            for (Host host : hosts) ECOControllerRegistry.unregister(host);
        }
    }

    private static void require(boolean value, String label) {
        if (!value) throw new IllegalStateException(label);
    }

    private static final class Host extends TileECOController {

        private final IGrid grid;
        private final BlockECONetworkSwitch networkSwitch = (BlockECONetworkSwitch) cn.dancingsnow.neoecoae.all.NEBlocks.craftingNetworkSwitch;

        Host(IGrid grid) {
            super(ECOControllerSubsystem.CRAFTING, ECOControllerTier.L9);
            this.grid = grid;
        }

        @Override
        public IGrid getLogicalNetworkGrid() {
            return grid;
        }

        @Override
        public BlockECONetworkSwitch getNetworkSwitch() {
            return networkSwitch;
        }
    }
}
