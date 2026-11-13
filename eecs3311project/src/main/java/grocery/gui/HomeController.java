package grocery.gui;

import java.time.LocalDate;
import java.util.List;

import grocery.agent.ClaudeClient;
import grocery.agent.ShoppingAgent;
import grocery.agent.ShoppingRequest;
import grocery.agent.WebPlanResult;
import grocery.model.ShoppingPlan;
import grocery.model.UserPreferences;
import grocery.service.PreferencesService;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;
import grocery.service.WebListingExtractor;
import grocery.service.WebProductSearchService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class HomeController {

    @FXML private TextArea requestField;
    @FXML private TextField budgetField;
    @FXML private Button generateButton;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Label statusLabel;

    private MainController mainController;

    public void setMainController(MainController controller) {
        this.mainController = controller;
    }

    @FXML
    private void generatePlan() {

        String prompt = requestField.getText().trim();
        String budgetText = budgetField.getText().trim();

        if (prompt.isEmpty()) {
            statusLabel.setText(
                "Please enter a shopping request."
            );
            return;
        }

        double budget;

        try {
            budget = Double.parseDouble(budgetText);

            if (!Double.isFinite(budget) || budget <= 0) {
                throw new NumberFormatException();
            }

        } catch (NumberFormatException e) {
            statusLabel.setText(
                "Please enter a valid budget greater than zero."
            );
            return;
        }

        ShoppingRequest request =
            new ShoppingRequest(prompt, budget);

        generateButton.setDisable(true);
        loadingIndicator.setVisible(true);
        loadingIndicator.setManaged(true);

        statusLabel.setText("Generating grocery item list...");
       
        Task<WebPlanResult> task = new Task<>() {

            @Override
            protected WebPlanResult call() throws Exception {

                ClaudeClient claude = new ClaudeClient();

                ProductSearchService searchService =
                    new ProductSearchService(List.of());

                ShoppingAgent agent = new ShoppingAgent(
                    claude,
                    searchService,
                    new PromotionService()
                );

                PreferencesService preferencesService =
                    new PreferencesService();

                UserPreferences preferences =
                    preferencesService.load();

                String preferenceContext = """
                    Preferred stores: %s
                    Preferred brands: %s
                    Allergies: %s
                    Dietary restrictions: %s
                    Excluded items: %s

                    Respect shopping preferences and restrictions.
                    Do not claim allergy safety without verified
                    ingredient and allergen information.
                    """.formatted(
                        preferences.getPreferredStores(),
                        preferences.getPreferredBrands(),
                        preferences.getAllergies(),
                        preferences.getDietaryRestrictions(),
                        preferences.getExcludedItems()
                    );

                ShoppingRequest webRequest =
                    new ShoppingRequest(
                        request.prompt() + "\n" + preferenceContext,
                        request.budget()
                    );

                WebProductSearchService webSearch =
                    new WebProductSearchService(claude);

                WebListingExtractor extractor =
                    new WebListingExtractor(claude);

                return agent.createWebPlan(
                    webRequest,
                    webSearch,
                    extractor,
                    true,
                    false,
                    LocalDate.now(),
                    message -> {
                        System.out.println("[HOME] " + message);
                        updateMessage(message);
                    }
                );
            }
        };


        statusLabel.textProperty().bind(task.messageProperty());

        // Handle the successful completion of the web plan generation task.
        task.setOnSucceeded(event -> {
            statusLabel.textProperty().unbind();
            System.out.println("[HOME] Web plan generated successfully.");

            generateButton.setDisable(false);
            loadingIndicator.setVisible(false);
            loadingIndicator.setManaged(false);

            WebPlanResult result = task.getValue();
            ShoppingPlan plan = result.plan();

            if (result.unresolvedItems().isEmpty()) {
                statusLabel.setText(
                    "Web shopping plan generated successfully!"
                );
            } else {
                statusLabel.setText(
                    "Plan generated with "
                    + result.unresolvedItems().size()
                    + " unresolved item(s)."
                );
                
            }

            if (!result.warnings().isEmpty()) {
                System.out.println("Web plan warnings:");
                result.warnings().forEach(System.out::println);
            }

            if (mainController != null) {

                // Save both the plan and its web-search candidates.
                mainController.setCurrentPlan(result.plan());

                mainController.setCurrentWebResult(result);

                // Open Shopping Plan with alternatives available.
                mainController.showPlan();
            }


        });


        task.setOnFailed(event -> {
            statusLabel.textProperty().unbind();
            System.err.println("[HOME] Shopping plan generation failed.");

            generateButton.setDisable(false);
            loadingIndicator.setVisible(false);
            loadingIndicator.setManaged(false);

            Throwable error = task.getException();

            statusLabel.setText(
                "Unable to generate plan: " +
                (error == null
                    ? "Unknown error"
                    : error.getMessage())
            );

            if (error != null) {
                error.printStackTrace();
            }
        });

        Thread thread = new Thread(
            task, "grocery-plan-generator"
        );
        thread.setDaemon(true);
        thread.start();
    }
}
