package grocery.strategy;

import grocery.model.ShoppingPlan;

public interface OptimizationStrat {
    ShoppingPlan optimize(ShoppingPlan plan);
}
