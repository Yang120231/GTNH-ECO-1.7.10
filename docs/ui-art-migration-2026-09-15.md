# UI and artwork migration audit (2026-09-15)

Baseline: local NeoECOAEExtension-1.21.1, with NeoECOAEExtension-1.21.1-ECO-Planner as the older planner reference.

## Implemented in this change

- The AE2 crafting confirmation screen uses the original 330 x 260 ECO report texture for synchronized ECO reports. Vanilla AE2 jobs retain their normal screen.
- Seven rows of three material cells use AE2's actual synchronized storage, pending and missing lists, not the bounded graph snapshot. Full long quantities and fluid mB units are available in tooltips.
- The separate cycle column shows seed, net output and seed shortfall; missing and cycle materials are highlighted. Both tables support wheel scrolling and scrollbar dragging.
- CPU selection, start and cancel keep AE2's existing network handlers and CPU validation. Enter starts an eligible job; Escape returns to the originating terminal. The graph keeps the existing container-preserving navigation.
- The report scales down on small GUI resolutions. English and Chinese report labels are included.
- The crafting host exposes ignore-pattern-substitutions and synchronizes changes with computation/crafting controllers on the logical grid.
- Added 32 missing source assets. All source files now exist under textures/gui (60), textures/guis (1), and the storage (46), crafting (59), compute (90) block texture subtrees, mapping block to blocks for 1.7.10. Existing adapted files were preserved.

## Validation

- Gradle test/build, Checkstyle and Spotless passed; existing suite: 83 tests, zero failures/errors.
- Development client completed loading. The log confirms MixinGuiCraftConfirmReport applied to GuiCraftConfirm without injection errors.
- Existing model texture resource tests passed.
- No in-world visual/interaction acceptance has been performed. Client startup is not evidence that every panel is visually identical.

## Remaining compatibility boundaries

This is not a claim of full feature parity with every feature added to the current 1.21.1 branch. Its MEGA partition editor depends on a MEGA cell backend absent in 1.7.10; copying its background does not implement that backend. Similarly, BigInteger crafting quantities/force-start require a different server execution contract: this change preserves 1.7.10 long quantities and simulation rejection. Newly supplied automatic-mode/compatibility artwork is available for the corresponding future states but does not introduce those machines or modes. JSON screen layouts from modern AE2 are implemented as Java drawing code because legacy AE2 does not load those layouts.

## World-entry crash follow-up

The 19:47 development client later crashed at 19:49:43 while rendering a drive LED: NoClassDefFoundError for DriveRenderHandler$1. Compilation had rewritten the live development class directory while that client was running. This invalidates treating that launch as a world-entry smoke test. DriveRenderHandler.rotate now uses direct enum comparisons without the synthetic switch helper. Build and client execution must be sequential; never rebuild the classpath of a running client. The simultaneous CodeChickenLib chunk-unload exception remains a separate observation until a fresh-process world test establishes whether it recurs.

## Screenshot-driven UI corrections

Report rows and footer are now composed explicitly with OpenGL, removing baked empty-cycle symbols and the stepped footer. Report/graph controls share a flat hover/disabled style. Material text is enlarged from 0.65 to 0.75 scale. Graph links attach at node edges with orthogonal segments and visible arrowheads; cards have header bands and shadows. Small-window graph toolbars put search on a second row.

Storage rows now explicitly show shared host capacity rather than unpopulated per-channel totals. Host-domain usage no longer adds portable-drive usage a second time. Shared host progress bars use a one-pixel border: the previous three-pixel inset produced negative fill heights for four-pixel bars. All host titles use a smaller common scale.

Validation: Gradle test/build and formatting checks passed before final report polish; final build repeated after that change. In-world visual and interaction verification is still pending; these changes are not a claim of complete 1.21.1 feature parity.

### 1.20.1 reference correction

The 1.20.1 NEHostTextures adapter bundles four ae2_121 sprites and draws 200x20 buttons using three-pixel nine-slices. These assets were missing from the 1.7.10 resource set. They are now copied unchanged and used by ECOGuiButton instead of the provisional flat rectangles. The 1.20.1 compact-tree renderer and host adapters remain the reference for further parity work; the earlier 1.21.1-only asset audit did not cover these migration-specific assets.
