<h1 align="center">Neo ECO AE Extension</h1>
<p align="center"><strong>GT New Horizons · Applied Energistics 2 Addon</strong></p>

<p align="center"><a href="README.md">简体中文</a> · <strong>English</strong></p>

This is the official Minecraft 1.7.10 version of Neo ECO AE Extension (ECO), built for GT New Horizons.

Add expandable storage, crafting, and computation multiblocks to your ME network. Use each system independently or combine them for centralized storage, batch manufacturing, and complex crafting jobs.

## Version Support

| GTNH | Download |
| --- | --- |
| 2.9.0-RC1 and later | [![Latest release](https://img.shields.io/github/v/release/Yang120231/GTNH-ECO-1.7.10)](https://github.com/Yang120231/GTNH-ECO-1.7.10/releases) |

Required dependencies include Applied Energistics 2, AE2 Fluid Crafting (AE2FC, 1.5.64-gtnh or later), ModularUI2, and StructureLib. Workstation recipes can be transferred directly from NEI into the Pattern Terminal, preserving item counts, fluid types, and fluid amounts in mB.

## Start with an ME Network

Check the structure with the controller's automatic building panel or a StructureLib projector, then connect its communication interface to your ME network. Establish a working power supply and basic operation before adding more drives, working cores, or threading cores.

The three systems have distinct roles: **storage holds resources, crafting executes patterns, and computation plans and schedules jobs**. Use them separately or combine them into a storage and batch manufacturing network.

## Interfaces and Gameplay

These screenshots show different structure sizes and operating states. Actual capacity, parallel processing, and available features depend on the controller tier, structure, installed components, and server configuration.

### Extensible Storage Subsystem

![Extensible Storage Subsystem showing storage matrices, energy, resource distribution, and infinite storage mode](docs/images/en_us/storage-subsystem.png)

**How to play:** Choose an L4, L6, or L9 controller, build its drives and supporting structure, and install item or fluid storage matrices. Connect the communication interface to make those resources available through ME terminals. Extend the structure with more drives as needed, and use the controller to check capacity usage, resource distribution, priorities, and energy.

The interface provides storage, input, and output modes. Combine these with its resource filter to move selected resources into the local matrices or back into other network storage. The screenshot shows L9 infinite storage mode: migration requires the appropriate components and member matrices. Before removing the components, restore the inventory into ordinary matrices with compatible capacity and resource types.

### Extensible Crafting Subsystem

![Extensible Crafting Subsystem showing FX working slots, the task queue, parallel capacity, and active cooling](docs/images/en_us/crafting-subsystem.png)

**How to play:** Insert crafting patterns into pattern buses, connect the communication interface to your ME network, and request a craft from a terminal. FX working cores execute the patterns. Add working cores for more independent execution channels and parallel cores for greater processing capacity when manufacturing basic materials, components, and intermediate products in bulk.

Establish stable crafting first, then enable overclocking and active cooling as your power supply allows. Supply coolant to the input hatch and leave room in the output hatch for spent coolant. Watch the queue, FX slots, and coolant summary during operation. For larger setups, connect multiple F9 controllers with normal or high-energy network switch modules to share patterns and distribute work.

### Extensible Computation Subsystem

![Extensible Computation Subsystem showing the CPU list, storage usage, task threads, and parallel capacity](docs/images/en_us/computation-subsystem.png)

**How to play:** Build drives, threading cores, and parallel cores along the transmission bus, install flash arrays in the drives, and connect the communication interface to your ME network. The structure appears as a crafting CPU when placing an order. Threading cores let it handle more simultaneous jobs, parallel cores provide acceleration, and flash arrays supply CPU storage for those jobs.

Check the CPU list, busy threads, and remaining storage in the controller interface. Configure it to accept any request, player requests only, or machine requests only. Enable ECO fast planning for complex orders; cyclic recipes also require cycle planning and actual startup materials. Multiple C9 controllers can pool computation resources through network switch modules.

### ECO Planner

![ECO Planner showing CPU selection, material requirements, crafting steps, and order controls](docs/images/en_us/eco-planner.png)

**How to play:** Request a craft from an ME terminal, then use the confirmation screen to select a CPU and check required job storage, existing stock, quantities to craft, and missing materials. Search, sort, and resize the view to inspect large orders. Check that your supply chain can meet the requirements before starting the job.

Use **Start With Follow** to track the job after submission. When eligible, **Merge** adds demand to a job for the same output. Add missing materials to NEI bookmarks or switch to the planning graph to investigate their dependencies. Cyclic routes need actual startup materials, and an incomplete route search needs separate investigation rather than being treated as an ordinary material shortage.

### Planning Graph

![Planning Graph showing recipe dependencies, missing materials, display depth, and search](docs/images/en_us/planning-graph.png)

**How to play:** Click **Planning Graph** on the crafting confirmation screen to trace the target output through intermediate products down to its raw materials. Identify where to add stock or processing machines. Pan, zoom, expand or collapse nodes, and adjust the display depth to inspect long recipe chains one layer at a time.

Use **Fit All** for an overview, then enable **Missing only** or search for a resource to locate a shortage. The material list helps verify quantities, while the graph helps explain where a shortage comes from. Switch between them to check the route before starting the craft.

### Real-Time Monitoring

![Real-Time Monitoring showing job inventory, crafting quantities, associated machines, and processing progress](docs/images/en_us/real-time-monitoring.png)

**How to play:** After starting a job, open crafting status and select a CPU on the left to inspect stored, scheduled, and crafting outputs. Hover over a processing entry to inspect associated machines, actual parallels, progress, and remaining time, including pause or shutdown reasons when the machine provides them.

Right-click an entry to locate its pattern, open the machine, or open the pattern provider when investigating a shortage or blocked process. Opening a remote GUI requires the target to be in the same dimension and its chunk to be loaded by your client. For targets outside that range, locate them first and move closer. Use **Suspend**, **Follow**, and **Cancel** to manage the selected job.

## Supporting Content

### Integrated Working Station and Large Integrated Working Station

Process ECO materials, storage components, and machine parts with item, fluid, and batch processing support, integrated with GT recipes and machine processing.

### Building, Recipes, and Guides

Controllers include an automatic building panel for structure inspection, material counts, and assistance with construction and expansion. NEI provides recipe lookup, WAILA shows machine and operating status, and GuideNH provides the in-game guide.
