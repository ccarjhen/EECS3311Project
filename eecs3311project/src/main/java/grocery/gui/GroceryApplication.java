
package grocery.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class GroceryApplication extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/grocery/gui/main.fxml")
        );

        Parent root = loader.load();

        Scene scene = new Scene(root, 1200, 800);

        scene.getStylesheets().add(getClass().getResource("/grocery/gui/styles.css").toExternalForm());

        stage.setTitle("AI Grocery Companion");
        stage.setScene(scene);
        stage.setMinWidth(950);
        stage.setMinHeight(650);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
