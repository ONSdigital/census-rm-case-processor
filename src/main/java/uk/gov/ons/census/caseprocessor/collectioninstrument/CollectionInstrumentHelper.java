package uk.gov.ons.census.caseprocessor.collectioninstrument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.common.model.entity.Case;

@Component
public class CollectionInstrumentHelper {
  private static final Logger log = LoggerFactory.getLogger(CollectionInstrumentHelper.class);

  private final RulesCache rulesCache;

  public CollectionInstrumentHelper(RulesCache rulesCache) {
    this.rulesCache = rulesCache;
  }

  public String getCollectionInstrumentUrl(Case caze, Object uacMetadata) {
    EvaluationBundle bundle = new EvaluationBundle(caze, uacMetadata);
    EvaluationContext context = new StandardEvaluationContext(bundle);

    CachedRule[] rules = rulesCache.getRules(caze.getCollectionExercise().getId());

    String selectedCollectionInstrumentUrl = null;
    for (CachedRule cachedRule : rules) {
      Boolean expressionResult = Boolean.TRUE;

      // No expression means "match anything"... used for 'default' rule
      if (cachedRule.getSpelExpression() != null) {
        try {
          expressionResult = cachedRule.getSpelExpression().getValue(context, Boolean.class);
        } catch (Exception spelExpressionEvaluationException) {

          log.atError()
              .setMessage("Collection instrument selection rule causing error")
              .setCause(spelExpressionEvaluationException)
              .addKeyValue("case_id", caze.getId())
              .addKeyValue("uac_metadata", uacMetadata)
              .addKeyValue("expression", cachedRule.getSpelExpression().getExpressionString())
              .log();

          throw new RuntimeException(
              "Collection instrument selection rule causing error",
              spelExpressionEvaluationException);
        }
      }

      if (expressionResult) {
        selectedCollectionInstrumentUrl = cachedRule.getCollectionInstrumentUrl();
        break;
      }
    }

    // This check is waaaay too late... these checks need to happen in the UI etc to stop
    // dodgy rules from ever being configured... it's here as a final line of defence, but we
    // absolutely can not rely on it because it will wreak havoc on operational support.
    if (selectedCollectionInstrumentUrl == null) {
      log.atError()
          .setMessage(
              "Collection instrument rules are set up incorrectly: there MUST be a default rule")
          .addKeyValue("collection_exercise_id", caze.getCollectionExercise().getId())
          .log();

      throw new RuntimeException(
          "Collection instrument rules are set up incorrectly: there MUST be a default rule");
    }

    return selectedCollectionInstrumentUrl;
  }
}
