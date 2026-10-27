package cn.dancingsnow.neoecoae.blocks.entity;
import cn.dancingsnow.neoecoae.platform.fluid.NativeLargeWorkstationHatch;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockEntityType;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockPos;
import cn.dancingsnow.neoecoae.platform.minecraft.BlockState;
/** Source hatch identity and capacity; Forge automation is owned by the native fluid adapter. */
public class ECOLargeIntegratedWorkingStationInputHatchBlockEntity extends NativeLargeWorkstationHatch {
 public ECOLargeIntegratedWorkingStationInputHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state, true); }
}
