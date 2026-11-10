package cn.dancingsnow.neoecoae.platform.ae2.upload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.util.IInterfaceViewable;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.crafting.upload.PatternRouteProbe;
import cn.dancingsnow.neoecoae.crafting.upload.PatternUploadTarget;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.platform.gt.NativeGregTech;

/** Enumerates upload targets from the grid's own node index; no world tile scan is involved. */
public final class Ae2UploadDiscovery {

    /** AE2's raw interface name when neither a custom name nor an adjacent machine exists. */
    static final String NO_ADJACENT_MACHINE = "Nothing";

    private static volatile PatternRouteProbe probe;

    private Ae2UploadDiscovery() {}

    public static PatternRouteProbe probe() {
        PatternRouteProbe current = probe;
        if (current == null) {
            current = NativeGregTech.isAvailable() ? cn.dancingsnow.neoecoae.platform.gt.upload.GtPatternRoutes.create()
                : PatternRouteProbe.NONE;
            probe = current;
        }
        return current;
    }

    public static List<PatternUploadTarget> discover(IGrid grid) {
        if (grid == null) return Collections.emptyList();
        PatternRouteProbe routes = probe();
        routes.invalidate();
        List<PatternUploadTarget> result = new ArrayList<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Object, EcoPatternDomainTarget> domains = new IdentityHashMap<>();
        try {
            for (Class<? extends IGridHost> type : AEApi.instance()
                .registries()
                .interfaceTerminal()
                .getSupportedClasses())
                for (IGridNode node : grid.getMachines(type)) add(result, seen, domains, node, routes);
        } catch (RuntimeException registry) {
            NeoECOAE.LOG.debug("Interface terminal registry scan failed", registry);
        }
        for (IGridNode node : grid.getNodes()) add(result, seen, domains, node, routes);
        return result;
    }

    private static void add(List<PatternUploadTarget> result, Set<Object> seen,
        Map<Object, EcoPatternDomainTarget> domains, IGridNode node, PatternRouteProbe routes) {
        try {
            IGridHost machine = node.getMachine();
            if (machine == null || !seen.add(machine)) return;
            if (machine instanceof ECOLargeIntegratedWorkingStationInterfaceBlockEntity workstation) {
                if (!workstation.shouldDisplay()) return;
                result.add(new Ae2InterfaceTarget(workstation, node, routes));
                return;
            }
            if (machine instanceof ECOMachineInterfaceBlockEntity) {
                ECOMachineInterfaceBlockEntity<?> host = (ECOMachineInterfaceBlockEntity<?>) machine;
                Object cluster = host.getCluster();
                if (!(cluster instanceof NECraftingCluster) || ((NECraftingCluster) cluster).isDestroyed()) return;
                Object domain = ((NECraftingCluster) cluster).getPatternDomain();
                EcoPatternDomainTarget target = domains.get(domain);
                if (target == null) {
                    target = new EcoPatternDomainTarget(host, domain);
                    domains.put(domain, target);
                    result.add(target);
                } else target.preferHost(host);
                return;
            }
            // Pattern buses belong to the domain above; other ECO components have no upload inventory.
            if (machine.getClass()
                .getName()
                .startsWith("cn.dancingsnow.neoecoae.")) return;
            if (!(machine instanceof IInterfaceViewable)) return;
            IInterfaceViewable viewable = (IInterfaceViewable) machine;
            if (viewable.getPatterns() == null) return;
            // A missing display icon does not rule out a usable container or a native crafting provider.
            if (NO_ADJACENT_MACHINE.equals(viewable.getRawName()) && !viewable.isCraftingPatternProvider()
                && !Ae2InterfaceTarget.facesContainer(viewable)) return;
            // Respect the host's terminal visibility for every interface and pattern hatch.
            if (!viewable.shouldDisplay()) return;
            result.add(new Ae2InterfaceTarget(viewable, node, routes));
        } catch (RuntimeException rebuilding) {
            // A host can disappear while the grid is rebuilt; skip only that node.
        }
    }
}
