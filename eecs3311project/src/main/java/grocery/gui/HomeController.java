package grocery.gui;

import java.time.LocalDate;
import java.util.List;

import grocery.adapter.LongosAdapter;
import grocery.adapter.NoFrillsAdapter;
import grocery.adapter.WalmartAdapter;
import grocery.agent.ClaudeClient;
import grocery.agent.ShoppingAgent;
import grocery.agent.ShoppingRequest;
import grocery.model.ShoppingPlan;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;

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

        statusLabel.setText(
            "Claude is generating your shopping plan..."
        );

        // Run API requests outside the JavaFX UI thread.
        Task<ShoppingPlan> task = new Task<>() {

            @Override
            protected ShoppingPlan call() throws Exception {

                ProductSearchService searchService =
                    new ProductSearchService(
                        List.of(
                            new LongosAdapter(),
                            new WalmartAdapter(),
                            new NoFrillsAdapter()
                        )
                    );

                PromotionService promotionService =
                    new PromotionService();

                ClaudeClient claude = new ClaudeClient();

                ShoppingAgent agent = new ShoppingAgent(
                    claude,
                    searchService,
                    promotionService
                );

                return agent.createPlan(
                    request,
                    false,
                    LocalDate.now()
                );
            }
        };

        task.setOnSucceeded(event -> {

            generateButton.setDisable(false);
            loadingIndicator.setVisible(false);
            loadingIndicator.setManaged(false);

            ShoppingPlan plan = task.getValue();

            statusLabel.setText(
                "Shopping plan generated successfully!"
            );

            if (mainController != null) {
                mainController.setCurrentPlan(plan);
                mainController.showPlan();
            }
        });

        task.setOnFailed(event -> {

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
