package grocery.adapter;
import grocery.model.Product;
import java.util.List;

public interface StoreAdapter {
    List<Product> search(String query);
}
