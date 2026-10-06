package com.feastwineallgone.food;

/**
 * Result of a maid attempting to consume one serving
 * from a placed food block.
 */
public record FoodConsumeResult(
        Status status,
        String handlerId,
        int servingsBefore,
        int servingsAfter
) {
    public enum Status {
        SUCCESS,
        UNSUPPORTED,
        EMPTY,
        CLIENT_SIDE,
        FAILED
    }

    public boolean consumed() {
        return status == Status.SUCCESS;
    }

    public static FoodConsumeResult success(
            String handlerId,
            int servingsBefore,
            int servingsAfter
    ) {
        return new FoodConsumeResult(
                Status.SUCCESS,
                handlerId,
                servingsBefore,
                servingsAfter
        );
    }

    public static FoodConsumeResult unsupported() {
        return new FoodConsumeResult(
                Status.UNSUPPORTED,
                "",
                -1,
                -1
        );
    }

    public static FoodConsumeResult empty(
            String handlerId,
            int servings
    ) {
        return new FoodConsumeResult(
                Status.EMPTY,
                handlerId,
                servings,
                servings
        );
    }

    public static FoodConsumeResult clientSide() {
        return new FoodConsumeResult(
                Status.CLIENT_SIDE,
                "",
                -1,
                -1
        );
    }

    public static FoodConsumeResult failed(
            String handlerId,
            int servings
    ) {
        return new FoodConsumeResult(
                Status.FAILED,
                handlerId,
                servings,
                servings
        );
    }
}