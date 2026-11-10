
package grocery.gui;

import java.io.IOException;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;

public class MainController {

    @FXML private StackPane contentArea;

    @FXML private Button homeButton;
    @FXML private Button planButton;
    @FXML private Button productsButton;
    @FXML private Button preferencesButton;
    @FXML private Button checklistsButton;

    @FXML
    public void initialize() {
        showHome();
    }

    private void loadPage(String page) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource(
                    "/grocery/gui/" + page + ".fxml"
                )
            );

            Node view = loader.load();
            contentArea.getChildren().setAll(view);

        } catch (IOException e) {
            e.printStackTrace();
            contentArea.getChildren().setAll(
                new javafx.scene.control.Label(
                    "Unable to load " + page + " page."
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
    private void showHome() {
        loadPage("home");
        setActiveButton(homeButton);
    }

    @FXML
    private void showPlan() {
        loadPage("plan");
        setActiveButton(planButton);
    }

    @FXML
    private void showProducts() {
        loadPage("products");
        setActiveButton(productsButton);
    }

    @FXML
    private void showPreferences() {
        loadPage("preferences");
        setActiveButton(preferencesButton);
    }

    @FXML
    private void showChecklists() {
        loadPage("checklists");
        setActiveButton(checklistsButton);
    }
}
