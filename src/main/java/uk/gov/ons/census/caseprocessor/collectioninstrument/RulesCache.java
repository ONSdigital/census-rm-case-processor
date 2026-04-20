package uk.gov.ons.census.caseprocessor.collectioninstrument;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.caseprocessor.model.repository.CollectionExerciseRepository;
import uk.gov.ons.census.common.model.entity.CollectionExercise;
import uk.gov.ons.census.common.model.entity.CollectionInstrumentSelectionRule;

@Component
public class RulesCache {
  private static final ExpressionParser expressionParser = new SpelExpressionParser();

  private final CollectionExerciseRepository collectionExerciseRepository;

  public RulesCache(CollectionExerciseRepository collectionExerciseRepository) {
    this.collectionExerciseRepository = collectionExerciseRepository;
  }

  @Cacheable("collectionInstrumentRules")
  public CachedRule[] getRules(UUID collectionExerciseId) {
    CollectionExercise collectionExercise =
        collectionExerciseRepository
            .findById(collectionExerciseId)
            .orElseThrow(() -> new RuntimeException("Collex not found"));

    return prepareAndSortRules(collectionExercise.getCollectionInstrumentSelectionRules());
  }

  private CachedRule[] prepareAndSortRules(CollectionInstrumentSelectionRule[] unpreparedRules) {
    List<CachedRule> preparedRules = new ArrayList<>(unpreparedRules.length);

    for (CollectionInstrumentSelectionRule unpreparedRule : unpreparedRules) {
      Expression spelExpression = null;

      if (unpreparedRule.getSpelExpression() != null) {
        spelExpression = expressionParser.parseExpression(unpreparedRule.getSpelExpression());
      }

      preparedRules.add(
          new CachedRule(
              spelExpression,
              unpreparedRule.getPriority(),
              unpreparedRule.getCollectionInstrumentUrl()));
    }

    preparedRules.sort(Comparator.comparingInt(CachedRule::getPriority).reversed());

    return preparedRules.toArray(new CachedRule[preparedRules.size()]);
  }
}
