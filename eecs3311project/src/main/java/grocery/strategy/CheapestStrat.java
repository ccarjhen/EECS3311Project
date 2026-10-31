package grocery.strategy;

import java.time.LocalDate;

import grocery.model.ShoppingPlan;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;


public class CheapestStrat implements OptimizationStrat {
  private final OptimizationSupport support;

  public CheapestStrat(ProductSearchService searchService,
    PromotionService promotionService,
    boolean hasMembership,
    LocalDate date
  ) {
    support = new OptimizationSupport(searchService, promotionService, hasMembership, date);
  }

  @Override
  public ShoppingPlan optimize(ShoppingPlan plan){
    return support.buildPlan(plan, null);
  }


}
