package cn.dancingsnow.neoecoae.mixin;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GuiScreen.class, remap = false)
public interface MixinGuiScreenAccessor {

    @Accessor(value = "buttonList", remap = true)
    List<GuiButton> neoecoae$getButtonList();
}
