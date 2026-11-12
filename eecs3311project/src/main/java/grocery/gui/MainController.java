package grocery.gui;

import grocery.model.ShoppingPlan;
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
    private ChecklistController activeChecklistController;

    @FXML
    public void initialize() {
        showHome();
    }

    public void setCurrentPlan(ShoppingPlan plan) {
        this.currentPlan = plan;
    }

    public ShoppingPlan getCurrentPlan() {
        return currentPlan;
    }

    private void loadPage(String page) {

        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource(
                    "/grocery/gui/" + page + ".fxml"
                )
            );

            Node view = loader.load();

            Object controller = loader.getController();

            if (controller instanceof HomeController home) {
                home.setMainController(this);
            }

            if (controller instanceof PlanController plan) {
                plan.setMainController(this);
                plan.setShoppingPlan(currentPlan);
            }

            if (controller instanceof ChecklistController checklist) {
                checklist.setMainController(this);
                checklist.setShoppingPlan(currentPlan);
            }

            if (controller instanceof HomeController home) {
                home.setMainController(this);
            }

            if (controller instanceof PlanController plan) {
                plan.setMainController(this);
                plan.setShoppingPlan(currentPlan);
            }

            if (controller instanceof ChecklistController checklist) {
                checklist.setMainController(this);
                checklist.setShoppingPlan(currentPlan);
            }

            // Remove observer from the previous checklist screen.
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
