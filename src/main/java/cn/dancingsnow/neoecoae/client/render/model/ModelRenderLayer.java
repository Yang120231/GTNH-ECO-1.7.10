package cn.dancingsnow.neoecoae.client.render.model;

public enum ModelRenderLayer {

    CUTOUT(0),
    TRANSLUCENT(1);

    private final int renderPass;

    ModelRenderLayer(int renderPass) {
        this.renderPass = renderPass;
    }

    public int getRenderPass() {
        return this.renderPass;
    }

    public static ModelRenderLayer fromJsonName(String name, ModelRenderLayer fallback) {
        if ("translucent".equals(name)) {
            return TRANSLUCENT;
        }
        if ("solid".equals(name) || "cutout".equals(name) || "cutout_mipped".equals(name)) {
            return CUTOUT;
        }
        return fallback;
    }

    public static ModelRenderLayer fromRenderPass(int renderPass) {
        return renderPass == TRANSLUCENT.renderPass ? TRANSLUCENT : CUTOUT;
    }
}
