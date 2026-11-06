package grocery.strategy;

import java.time.LocalDate;

import grocery.model.ShoppingPlan;
import grocery.model.UserPreferences;
import grocery.service.ProductSearchService;
import grocery.service.PromotionService;


public class CheapestStrat implements OptimizationStrat {
  private final OptimizationSupport support;

  public CheapestStrat(ProductSearchService searchService,
    PromotionService promotionService,
    boolean hasMembership,
    LocalDate date,
    UserPreferences preferences
  ) {
    support = new OptimizationSupport(searchService, promotionService, hasMembership, date, preferences);
  }

  @Override
  public ShoppingPlan optimize(ShoppingPlan plan){
    return support.buildPlan(plan, null);
  }


}
