package grocery.command;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
public class MarkPurchasedCommand implements ChecklistCommand {

    private final ShoppingPlan plan;
    private final ShoppingItem item;

    public MarkPurchasedCommand(ShoppingPlan plan, ShoppingItem item) {

        this.plan = plan;
        this.item = item;
    }

    @Override
    public void execute() {
        plan.markItemPurchased(item);
    }
}
