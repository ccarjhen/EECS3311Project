package grocery.service;
import grocery.adapter.StoreAdapter;
import grocery.model.Product;
import java.util.ArrayList;
import java.util.List;

public class ProductSearchService {
    private final List<StoreAdapter> stores;

    public ProductSearchService(List<StoreAdapter> stores) {
        this.stores = stores;
    }

    public List<Product> search(String query) {
        List<Product> result = new ArrayList<>();

        for (StoreAdapter store : stores) {
            result.addAll(store.search(query));
        }

        return result;
    }

}
