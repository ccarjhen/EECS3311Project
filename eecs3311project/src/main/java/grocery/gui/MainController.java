package grocery.gui;

import java.util.List;
import java.util.Map;

import grocery.agent.WebPlanResult;
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
    private WebPlanResult currentWebResult;

    private ChecklistController activeChecklistController;

    @FXML
    public void initialize() {
        showHome();
    }

    // ======================================
    // CURRENT SHOPPING PLAN
    // ======================================

    public void setCurrentPlan(ShoppingPlan plan) {
        this.currentPlan = plan;

        // Avoid attaching old web candidates to a different plan.
        if (currentWebResult != null
                && currentWebResult.plan() != plan) {
            currentWebResult = null;
        }
    }

    public ShoppingPlan getCurrentPlan() {
        return currentPlan;
    }

    // ======================================
    // WEB SEARCH RESULTS
    // ======================================

    public void setCurrentWebResult(WebPlanResult result) {
        this.currentWebResult = result;
    }

    public WebPlanResult getCurrentWebResult() {
        return currentWebResult;
    }

    public Map<String, List<WebProductListing>>
            getWebCandidates() {

        if (currentWebResult == null) {
            return Map.of();
        }

        return currentWebResult.candidatesByItem();
    }

    // ======================================
    // PAGE LOADING
    // ======================================

    private void loadPage(String page) {

        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource(
                    "/grocery/gui/" + page + ".fxml"
                )
            );

            Node view = loader.load();

            Object controller = loader.getController();

            // Home page
            if (controller instanceof HomeController home) {
                home.setMainController(this);
            }

            // Shopping Plan page
            if (controller instanceof PlanController plan) {
                plan.setMainController(this);
                plan.setShoppingPlan(currentPlan);

                // Pass web-search candidates to Find Alternative.
                plan.setWebCandidates(getWebCandidates());
            }

            // Checklists page
            if (controller instanceof ChecklistController checklist) {
                checklist.setMainController(this);
                checklist.setShoppingPlan(currentPlan);
            }

            // Remove old checklist observer when navigating.
            if (activeChecklistController != null) {
                activeChecklistController.detach();
                activeChecklistController = null;
            }

            if (controller instanceof ChecklistController checklist) {
                activeChecklistController = checklist;
            }

            contentArea.getChildren().setAll(view);

        } catch (Exception e) {
            e.printStackTrace();

            Throwable cause = e;

            while (cause.getCause() != null) {
                cause = cause.getCause();
            }

            contentArea.getChildren().setAll(
                new Label(
                    "Unable to load " + page + " page:\n"
                    + cause.getMessage()
                )
            );
        }
    }

    // ======================================
    // NAVIGATION STYLING
    // ======================================

    private void setActiveButton(Button active) {

        Button[] buttons = {
            homeButton,
            planButton,
            productsButton,
            preferencesButton,
            checklistsButton
        };

        for (Button button : buttons) {
            button.getStyleClass().remove("active-button");
        }

        active.getStyleClass().add("active-button");
    }

    // ======================================
    // NAVIGATION METHODS
    // ======================================

    @FXML
    public void showHome() {
        loadPage("home");
        setActiveButton(homeButton);
    }

    @FXML
    public void showPlan() {
        loadPage("plan");
        setActiveButton(planButton);
    }

    @FXML
    public void showProducts() {
        loadPage("products");
        setActiveButton(productsButton);
    }

    @FXML
    public void showPreferences() {
        loadPage("preferences");
        setActiveButton(preferencesButton);
    }

    @FXML
    public void showChecklists() {
        loadPage("checklists");
        setActiveButton(checklistsButton);
    }
}
