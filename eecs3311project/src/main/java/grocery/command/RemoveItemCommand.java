package grocery.command;

import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;

public class RemoveItemCommand implements ChecklistCommand {

    private final ShoppingPlan plan;
    private final ShoppingItem item;

    public RemoveItemCommand(ShoppingPlan plan, ShoppingItem item) {

        this.plan = plan;
        this.item = item;
    }

    @Override
    public void execute() {
        plan.removeItem(item);
    }
}
