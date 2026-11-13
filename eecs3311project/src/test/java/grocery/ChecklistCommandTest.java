package grocery;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import grocery.command.AddItemCommand;
import grocery.command.ChangeQuantityCommand;
import grocery.command.RemoveItemCommand;
import grocery.command.SetPurchasedCommand;
import grocery.controller.ShoppingController;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.observer.PlanObserver;
import grocery.service.ProductSearchService;

public class ChecklistCommandTest {

    private ShoppingPlan plan;
    private ShoppingController controller;
    private ShoppingItem milk;
    private ShoppingItem bread;

    @BeforeEach
    void setUp() {

        plan = new ShoppingPlan("Test Checklist", 50.00);

        // An empty search service is sufficient
        // because checklist commands do not search products.
        controller = new ShoppingController(
            new ProductSearchService(List.of())
        );

        milk = new ShoppingItem("Milk", "Dairy", 1);
        bread = new ShoppingItem("Bread", "Bakery", 2);
    }

    // --- TEST 1: ADD ITEM COMMAND ---

    @Test
    void testAddItemCommand() {

        controller.updateChecklist(
            new AddItemCommand(plan, milk)
        );

        assertEquals(1, plan.getShoppingItems().size());
        assertTrue(plan.getShoppingItems().contains(milk));
        assertEquals("Milk",
            plan.getShoppingItems().get(0).getName());
    }

    // --- TEST 2: REMOVE ITEM COMMAND ---

    @Test
    void testRemoveItemCommand() {

        plan.addItem(milk);
        plan.addItem(bread);

        controller.updateChecklist(
            new RemoveItemCommand(plan, milk)
        );

        assertEquals(1, plan.getShoppingItems().size());
        assertFalse(plan.getShoppingItems().contains(milk));
        assertTrue(plan.getShoppingItems().contains(bread));
    }

    // --- TEST 3: CHANGE QUANTITY COMMAND ---

    @Test
    void testChangeQuantityCommand() {

        plan.addItem(milk);

        controller.updateChecklist(
            new ChangeQuantityCommand(plan, milk, 5)
        );

        assertEquals(5, milk.getQuantity());
    }

    // --- TEST 4: INVALID QUANTITY ---

    @Test
    void testInvalidQuantityThrowsException() {

        plan.addItem(milk);

        assertThrows(
            IllegalArgumentException.class,
            () -> controller.updateChecklist(
                new ChangeQuantityCommand(plan, milk, 0)
            )
        );

        // Quantity should remain unchanged.
        assertEquals(1, milk.getQuantity());
    }

    // --- TEST 5: MARK ITEM AS PURCHASED ---

    @Test
    void testMarkItemPurchased() {

        plan.addItem(milk);

        controller.updateChecklist(
            new SetPurchasedCommand(plan, milk, true)
        );

        assertTrue(milk.isPurchased());
    }

    // --- TEST 6: UNCHECK PURCHASED ITEM ---

    @Test
    void testUnmarkPurchasedItem() {

        plan.addItem(milk);

        controller.updateChecklist(
            new SetPurchasedCommand(plan, milk, true)
        );

        assertTrue(milk.isPurchased());

        controller.updateChecklist(
            new SetPurchasedCommand(plan, milk, false)
        );

        assertFalse(milk.isPurchased());
    }

    // --- TEST 7: OBSERVER NOTIFIED ON ADD --- 

    @Test
    void testObserverNotifiedWhenItemAdded() {

        AtomicInteger notificationCount =
            new AtomicInteger(0);

        PlanObserver observer = updatedPlan ->
            notificationCount.incrementAndGet();

        plan.addObserver(observer);

        controller.updateChecklist(
            new AddItemCommand(plan, milk)
        );

        assertEquals(1, notificationCount.get());
    }

    // --- TEST 8: OBSERVER NOTIFIED ON REMOVE ---

    @Test
    void testObserverNotifiedWhenItemRemoved() {

        plan.addItem(milk);

        AtomicInteger notificationCount =
            new AtomicInteger(0);

        PlanObserver observer = updatedPlan ->
            notificationCount.incrementAndGet();

        plan.addObserver(observer);

        controller.updateChecklist(
            new RemoveItemCommand(plan, milk)
        );

        assertEquals(1, notificationCount.get());
    }

    // --- TEST 9: OBSERVER NOTIFIED ON QUANTITY ---

    @Test
    void testObserverNotifiedWhenQuantityChanges() {

        plan.addItem(milk);

        AtomicInteger notificationCount =
            new AtomicInteger(0);

        PlanObserver observer = updatedPlan ->
            notificationCount.incrementAndGet();

        plan.addObserver(observer);

        controller.updateChecklist(
            new ChangeQuantityCommand(plan, milk, 3)
        );

        assertEquals(1, notificationCount.get());
        assertEquals(3, milk.getQuantity());
    }

    // --- TEST 10: OBSERVER NOTIFIED ON PURCHASE ---

    @Test
    void testObserverNotifiedWhenPurchased() {

        plan.addItem(milk);

        AtomicInteger notificationCount =
            new AtomicInteger(0);

        PlanObserver observer = updatedPlan ->
            notificationCount.incrementAndGet();

        plan.addObserver(observer);

        controller.updateChecklist(
            new SetPurchasedCommand(plan, milk, true)
        );

        assertEquals(1, notificationCount.get());
        assertTrue(milk.isPurchased());
    }

    // --- TEST 11: REMOVE OBSERVER ---

    @Test
    void testRemovedObserverNotNotified() {

        AtomicInteger notificationCount =
            new AtomicInteger(0);

        PlanObserver observer = updatedPlan ->
            notificationCount.incrementAndGet();

        plan.addObserver(observer);
        plan.removeObserver(observer);

        controller.updateChecklist(
            new AddItemCommand(plan, milk)
        );

        assertEquals(0, notificationCount.get());
    }

    // --- TEST 12: MULTIPLE COMMANDS ---

    @Test
    void testMultipleChecklistCommands() {

        controller.updateChecklist(
            new AddItemCommand(plan, milk)
        );

        controller.updateChecklist(
            new AddItemCommand(plan, bread)
        );

        controller.updateChecklist(
            new ChangeQuantityCommand(plan, milk, 3)
        );

        controller.updateChecklist(
            new SetPurchasedCommand(plan, bread, true)
        );

        controller.updateChecklist(
            new RemoveItemCommand(plan, milk)
        );

        assertEquals(1, plan.getShoppingItems().size());
        assertTrue(plan.getShoppingItems().contains(bread));
        assertTrue(bread.isPurchased());
        assertFalse(plan.getShoppingItems().contains(milk));
    }
}
