package grocery.command;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;

public class ChangeQuantityCommand implements ChecklistCommand {
    private final ShoppingPlan plan;
    private final ShoppingItem item;
    private final int quantity;

    public ChangeQuantityCommand(ShoppingPlan plan, ShoppingItem item, int quantity) {
        this.plan = plan;
        this.item = item;
        this.quantity = quantity;
    }

    @Override
    public void execute() {
        plan.changeItemQuantity(item, quantity);
    }
}
