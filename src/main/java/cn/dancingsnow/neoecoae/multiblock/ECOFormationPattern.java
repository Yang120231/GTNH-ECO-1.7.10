package cn.dancingsnow.neoecoae.multiblock;

import cn.dancingsnow.neoecoae.tile.TileECOController;

interface ECOFormationPattern {

    ECOFormationResult verify(TileECOController controller, FormationDirections directions);
}
