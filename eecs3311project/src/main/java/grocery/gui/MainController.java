package grocery.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import grocery.agent.WebPlanResult;
import grocery.model.ShoppingItem;
import grocery.model.ShoppingPlan;
import grocery.model.WebProductListing;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public class MainController {
    @FXML private StackPane contentArea;
    @FXML private Button homeButton;
    @FXML private Button planButton;
    @FXML private Button productsButton;
    @FXML private Button preferencesButton;
    @FXML private Button checklistsButton;

    private ShoppingPlan currentPlan;
    private ShoppingPlan optimizationBasePlan;
    private WebPlanResult currentWebResult;
    private final Map<String, List<WebProductListing>> webCandidates = new LinkedHashMap<>();
    private ChecklistController activeChecklistController;
    private String activeOptimization = "Cheapest";
    private ShoppingPlan originalGeneratedPlan;
    private final Map<String, List<WebProductListing>> originalWebCandidates = new LinkedHashMap<>();

    public String getActiveOptimization() {
        return activeOptimization;
    }

    public void setActiveOptimization(String name) {
        this.activeOptimization = name;
    }

    @FXML
    public void initialize() { showHome(); }

    public void setCurrentPlan(ShoppingPlan plan) { currentPlan = plan; }
    public ShoppingPlan getCurrentPlan() { return currentPlan; }
    public ShoppingPlan getOptimizationBasePlan() { return optimizationBasePlan; }
    public WebPlanResult getCurrentWebResult() { return currentWebResult; }

    
    public void setCurrentWebResult(WebPlanResult result) {
        currentWebResult = result;
        webCandidates.clear();
        originalWebCandidates.clear();
        optimizationBasePlan = null;
        originalGeneratedPlan = null;

        if (result != null) {
            result.candidatesByItem().forEach((name, listings) -> {
                webCandidates.put(
                    name,
                    new ArrayList<>(listings)
                );
                originalWebCandidates.put(
                    name,
                    new ArrayList<>(listings)
                );
            });

            // Preserve the original generated plan.
            originalGeneratedPlan = copyPlan(result.plan());
            // Preserve the optimization baseline separately.
            optimizationBasePlan = copyPlan(result.plan());
            activeOptimization = "Cheapest";
        }
    }

    public void restoreOriginalPlan() {
        if (originalGeneratedPlan == null) {
            throw new IllegalStateException(
                "No original AI-generated plan is available."
            );
        }
        // Restore the original shopping items and products.
        currentPlan = copyPlan(originalGeneratedPlan);
        // Reset the optimization baseline.
        optimizationBasePlan = copyPlan(originalGeneratedPlan);
        // Restore original web-search alternatives.
        webCandidates.clear();

        originalWebCandidates.forEach((name, listings) ->
            webCandidates.put(
                name,
                new ArrayList<>(listings)
            )
        );
        // Restore the default optimization selection.
        activeOptimization = "Cheapest";
    }



    public Map<String, List<WebProductListing>> getWebCandidates() {
        Map<String, List<WebProductListing>> copy = new LinkedHashMap<>();
        webCandidates.forEach((name, listings) -> copy.put(name, List.copyOf(listings)));
        return copy;
    }

    /** Adds a product found on the Products page to the same cache used by optimization. */
    public void addSearchedProduct(WebProductListing listing) {
        if (currentPlan == null || listing == null) {
            throw new IllegalStateException("Generate a shopping plan before adding products.");
        }
        ShoppingItem newItem = new ShoppingItem(listing.name(), listing.category(), 1);
        newItem.setProduct(toProduct(listing));
        currentPlan.addItem(newItem);
        if (optimizationBasePlan == null) optimizationBasePlan = copyPlan(currentPlan);
        else optimizationBasePlan.addItem(copyItem(newItem));
        webCandidates.computeIfAbsent(newItem.getName(), key -> new ArrayList<>());
        if (!webCandidates.get(newItem.getName()).contains(listing)) {
            webCandidates.get(newItem.getName()).add(listing);
        }
    }

    public void removeItemFromBaseline(ShoppingItem removed) {
        if (optimizationBasePlan == null || removed == null) return;
        optimizationBasePlan.getShoppingItems().stream()
            .filter(item -> item.getName().equalsIgnoreCase(removed.getName()))
            .findFirst().ifPresent(optimizationBasePlan::removeItem);
    }

    private static grocery.model.Product toProduct(WebProductListing listing) {
        return new grocery.model.Product(
            "WEB-ADDED-" + java.util.UUID.randomUUID(), listing.name(), listing.brand(),
            listing.category(), listing.store(), listing.price(), listing.size(), listing.unit());
    }

    private static ShoppingItem copyItem(ShoppingItem item) {
        ShoppingItem copy = new ShoppingItem(item.getName(), item.getCategory(), item.getQuantity());
        copy.setProduct(item.getProduct());
        copy.setPurchased(item.isPurchased());
        return copy;
    }

    private static ShoppingPlan copyPlan(ShoppingPlan original) {
        ShoppingPlan copy = new ShoppingPlan(original.getName(), original.getEstimatedBudget());
        for (ShoppingItem item : original.getShoppingItems()) copy.addItem(copyItem(item));
        return copy;
    }

    private void loadPage(String page) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/grocery/gui/" + page + ".fxml"));
            Node view = loader.load();
            Object controller = loader.getController();

            if (controller instanceof HomeController home) home.setMainController(this);
            if (controller instanceof PlanController plan) {
                plan.setMainController(this);
                plan.setWebCandidates(getWebCandidates());
                plan.setShoppingPlan(currentPlan);
            }
            if (controller instanceof ProductsController products) products.setMainController(this);
            if (controller instanceof ChecklistController checklist) {
                checklist.setMainController(this);
                checklist.setShoppingPlan(currentPlan);
            }
            if (activeChecklistController != null) {
                activeChecklistController.detach();
                activeChecklistController = null;
            }
            if (controller instanceof ChecklistController checklist) activeChecklistController = checklist;
            contentArea.getChildren().setAll(view);
        } catch (Exception e) {
            e.printStackTrace();
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            contentArea.getChildren().setAll(new Label(
                "Unable to load " + page + " page:\n" + cause.getMessage()));
        }
    }

    private void setActiveButton(Button active) {
        for (Button button : new Button[] {
            homeButton, planButton, productsButton, preferencesButton, checklistsButton
        }) button.getStyleClass().remove("active-button");
        if (active != null) active.getStyleClass().add("active-button");
    }

    @FXML public void showHome() { loadPage("home"); setActiveButton(homeButton); }
    @FXML public void showPlan() { loadPage("plan"); setActiveButton(planButton); }
    @FXML public void showProducts() { loadPage("products"); setActiveButton(productsButton); }
    @FXML public void showPreferences() { loadPage("preferences"); setActiveButton(preferencesButton); }
    @FXML public void showChecklists() { loadPage("checklists"); setActiveButton(checklistsButton); }
}
