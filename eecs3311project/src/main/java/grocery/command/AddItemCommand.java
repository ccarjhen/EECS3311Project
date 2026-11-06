package grocery.command;

import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;

public class AddItemCommand implements ChecklistCommand {

    private final ShoppingPlan plan;
    private final ShoppingItem item;

    public AddItemCommand(ShoppingPlan plan, ShoppingItem item) {

        this.plan = plan;
        this.item = item;
    }


    @Override
    public void execute() {
        // Implementation for adding an item goes here
        plan.addItem(item);
    }
}
