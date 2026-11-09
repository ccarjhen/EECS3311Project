
package grocery.agent;

import java.util.List;
import java.util.Map;

import grocery.model.ShoppingPlan;
import grocery.model.WebProductListing;

public record WebPlanResult(
    ShoppingPlan plan,
    List<WebProductListing> approvedListings,
    List<RequestedItem> unresolvedItems,
    List<String> warnings,
    Map<String, List<WebProductListing>> candidatesByItem,
    boolean estimatedPricesUsed
) {

    public WebPlanResult {
        approvedListings = List.copyOf(approvedListings);
        unresolvedItems = List.copyOf(unresolvedItems);
        warnings = List.copyOf(warnings);

        candidatesByItem = candidatesByItem.entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> List.copyOf(entry.getValue())
            ));
    }

    // Compatibility with earlier tests using 4 arguments.
    public WebPlanResult(
            ShoppingPlan plan,
            List<WebProductListing> approvedListings,
            List<RequestedItem> unresolvedItems,
            List<String> warnings) {

        this(
            plan,
            approvedListings,
            unresolvedItems,
            warnings,
            Map.of(),
            false
        );
    }

    public boolean isComplete() {
        return unresolvedItems.isEmpty();
    }
}
