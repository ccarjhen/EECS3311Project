package grocery.command;

import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;

public class SetPurchasedCommand
        implements ChecklistCommand {

    private final ShoppingPlan plan;
    private final ShoppingItem item;
    private final boolean purchased;

    public SetPurchasedCommand(
            ShoppingPlan plan,
            ShoppingItem item,
            boolean purchased) {

        this.plan = plan;
        this.item = item;
        this.purchased = purchased;
    }

    @Override
    public void execute() {
        plan.setItemPurchased(item, purchased);
    }
}
