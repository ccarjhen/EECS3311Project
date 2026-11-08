package grocery.controller;

import java.time.LocalDate;
import java.util.List;

import grocery.agent.ShoppingAgent;
import grocery.agent.ShoppingRequest;
import grocery.command.ChecklistCommand;
import grocery.model.Product;
import grocery.model.ShoppingPlan;
import grocery.service.ProductSearchService;
import grocery.strategy.OptimizationStrat;

public class ShoppingController {

    private final ProductSearchService searchService;
    private OptimizationStrat optimizationStrategy;
    private ShoppingAgent shoppingAgent;

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

    public void updateChecklist(ChecklistCommand command) {

        if(command == null){
            throw new IllegalArgumentException("Checklist command cannot be null");
        }
        command.execute();
    }


    public void setShoppingAgent(ShoppingAgent agent) {
        if (agent == null) {
            throw new IllegalArgumentException(
                "ShoppingAgent cannot be null"
            );
        }

        this.shoppingAgent = agent;
    }

    public ShoppingPlan createPlan(
            ShoppingRequest request,
            boolean hasMembership,
            LocalDate date) throws Exception {

        if (shoppingAgent == null) {
            throw new IllegalStateException(
                "ShoppingAgent is not configured"
            );
        }

        return shoppingAgent.createPlan(
            request,
            hasMembership,
            date
        );
    }

}
