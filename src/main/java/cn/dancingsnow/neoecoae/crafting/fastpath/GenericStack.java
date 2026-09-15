package cn.dancingsnow.neoecoae.crafting.fastpath;

public final class GenericStack {

    private final Object what;
    private final long amount;

    public GenericStack(Object what, long amount) {
        this.what = what;
        this.amount = amount;
    }

    public Object what() {
        return what;
    }

    public long amount() {
        return amount;
    }
}
