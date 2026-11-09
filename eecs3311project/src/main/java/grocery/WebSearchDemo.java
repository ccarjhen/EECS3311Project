
package grocery;

import java.util.Scanner;

import grocery.agent.ClaudeClient;
import grocery.service.WebProductSearchService;
import grocery.service.WebSearchResult;

public class WebSearchDemo {

    public static void main(String[] args) {

        System.out.println("=== AI WEB PRODUCT SEARCH ===");

        try (Scanner scanner = new Scanner(System.in)) {

            System.out.print("Enter grocery product: ");
            String product = scanner.nextLine();

            ClaudeClient claude = new ClaudeClient();

            WebProductSearchService service =
                new WebProductSearchService(claude);

            System.out.println("\nSearching retailer websites...\n");

            WebSearchResult result = service.search(product);

            System.out.println("=== SEARCH RESULTS ===");
            System.out.println(result.summary());

            System.out.println("\n=== SOURCES ===");

            if (result.sourceUrls().isEmpty()) {
                System.out.println("No source URLs returned.");
            } else {
                for (String url : result.sourceUrls()) {
                    System.out.println(url);
                }
            }

            System.out.println(
                "\nWeb search performed: " +
                result.searchPerformed()
            );

        } catch (Exception e) {
            System.out.println(
                "Search failed: " + e.getMessage()
            );
        }
    }
}
