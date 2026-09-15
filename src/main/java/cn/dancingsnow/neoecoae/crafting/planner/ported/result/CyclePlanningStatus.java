package cn.dancingsnow.neoecoae.crafting.planner.ported.result;

import cn.dancingsnow.neoecoae.crafting.planner.ported.cycle.CycleSolveStatus;

public enum CyclePlanningStatus {

    NOT_REQUIRED,

    DISABLED,

    NOT_IMPLEMENTED,

    UNSUPPORTED,

    SOLVED,

    INSUFFICIENT_EXTERNAL_INPUT,

    UNKNOWN_BUDGET,

    TOO_COMPLEX,

    UNREPRESENTABLE,

    CANCELLED;

    public static CyclePlanningStatus of(CycleSolveStatus status) {
        return switch (status) {
            case SUCCESS -> SOLVED;
            case INSUFFICIENT_EXTERNAL_INPUT -> INSUFFICIENT_EXTERNAL_INPUT;
            case UNKNOWN_BUDGET -> UNKNOWN_BUDGET;
            case TOO_COMPLEX -> TOO_COMPLEX;
            case UNSUPPORTED_PATTERN -> UNSUPPORTED;
            case UNREPRESENTABLE -> UNREPRESENTABLE;
            case CANCELLED -> CANCELLED;
            case NOT_IMPLEMENTED -> NOT_IMPLEMENTED;
        };
    }
}
