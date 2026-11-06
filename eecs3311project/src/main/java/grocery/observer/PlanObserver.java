package grocery.observer;

import grocery.model.ShoppingPlan;

public interface PlanObserver {
    void onPlanChanged(ShoppingPlan plan);
}
