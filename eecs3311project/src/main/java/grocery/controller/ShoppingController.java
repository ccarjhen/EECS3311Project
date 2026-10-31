package grocery.controller;

import java.util.List;

import grocery.model.Product;
import grocery.model.ShoppingPlan;
import grocery.service.ProductSearchService;
import grocery.strategy.OptimizationStrat;


public class ShoppingController {

    private final ProductSearchService searchService;
    private OptimizationStrat optimizationStrategy;

    public ShoppingController(ProductSearchService searchService) {
        this.searchService = searchService;
    }

    public List<Product> search(String query){
        return searchService.search(query);
    }

    public void setOptimizationStrategy(OptimizationStrat strategy) {
        if(strategy == null){
            throw new IllegalArgumentException("Optimization strategy cannot be null");
        }
        this.optimizationStrategy = strategy;
    }

    public ShoppingPlan optimize(ShoppingPlan plan){
        if(plan == null){
            throw new IllegalArgumentException("Shopping plan cannot be null");
        }

        if(optimizationStrategy == null){
            throw new IllegalStateException("Optimization strategy is not set");
        }

        return optimizationStrategy.optimize(plan);
    }

}
