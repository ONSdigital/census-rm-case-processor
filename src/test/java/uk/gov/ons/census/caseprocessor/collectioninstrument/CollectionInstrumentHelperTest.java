package uk.gov.ons.census.caseprocessor.collectioninstrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.CollectionExercise;

@ExtendWith(MockitoExtension.class)
class CollectionInstrumentHelperTest {
  private static final ExpressionParser expressionParser = new SpelExpressionParser();

  @Mock RulesCache rulesCache;

  @InjectMocks CollectionInstrumentHelper underTest;

  @Test
  void getCollectionInstrumentUrl() {
    CollectionExercise collex = new CollectionExercise();
    collex.setId(UUID.randomUUID());

    Case caze = new Case();
    caze.setCollectionExercise(collex);
    caze.setSample(Map.of("questionnaire", "LMB"));

    when(rulesCache.getRules(caze.getCollectionExercise().getId()))
        .thenReturn(
            new CachedRule[] {
              new CachedRule(
                  expressionParser.parseExpression(
                      "caze.sample['questionnaire'] == 'LMB' and uacMetadata['wave'] == 1"),
                  99,
                  "testCollectionInstrumentUrl")
            });

    String collectionInstrumentUrl = underTest.getCollectionInstrumentUrl(caze, Map.of("wave", 1));

    assertThat(collectionInstrumentUrl).isEqualTo("testCollectionInstrumentUrl");
  }

  @Test
  void getCollectionInstrumentUrlFallbackOnDefault() {
    CollectionExercise collex = new CollectionExercise();
    collex.setId(UUID.randomUUID());

    Case caze = new Case();
    caze.setCollectionExercise(collex);
    caze.setSample(Map.of("questionnaire", "LMS"));

    when(rulesCache.getRules(caze.getCollectionExercise().getId()))
        .thenReturn(
            new CachedRule[] {
              new CachedRule(
                  expressionParser.parseExpression(
                      "caze.sample['questionnaire'] == 'LMB' and uacMetadata['wave'] == 1"),
                  99,
                  "testDoNotChooseThisOne"),
              new CachedRule(null, 0, "testCollectionInstrumentUrl")
            });

    String collectionInstrumentUrl = underTest.getCollectionInstrumentUrl(caze, Map.of("wave", 1));

    assertThat(collectionInstrumentUrl).isEqualTo("testCollectionInstrumentUrl");
  }

  @Test
  void getCollectionInstrumentUrlNoDefault() {
    CollectionExercise collex = new CollectionExercise();
    collex.setId(UUID.randomUUID());

    Case caze = new Case();
    caze.setCollectionExercise(collex);
    caze.setSample(Map.of("questionnaire", "LMS"));

    when(rulesCache.getRules(caze.getCollectionExercise().getId()))
        .thenReturn(
            new CachedRule[] {
              new CachedRule(
                  expressionParser.parseExpression(
                      "caze.sample['questionnaire'] == 'LMB' and uacMetadata['wave'] == 1"),
                  99,
                  "testDoNotChooseThisOne")
            });

    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> underTest.getCollectionInstrumentUrl(caze, Map.of("wave", 1)));

    assertThat(thrown.getMessage())
        .isEqualTo(
            "Collection instrument rules are set up incorrectly: there MUST be a default rule");
  }

  @Test
  void selectionRuleErrorException() {
    CollectionExercise collex = new CollectionExercise();
    collex.setId(UUID.randomUUID());

    Case caze = new Case();
    caze.setCollectionExercise(collex);
    caze.setSample(Map.of("questionnaire", "LMB"));

    when(rulesCache.getRules(caze.getCollectionExercise().getId()))
        .thenReturn(
            new CachedRule[] {
              new CachedRule(
                  expressionParser.parseExpression("typo"), 99, "testCollectionInstrumentUrl")
            });

    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> underTest.getCollectionInstrumentUrl(caze, Map.of("wave", 1)));

    assertThat(thrown.getMessage()).isEqualTo("Collection instrument selection rule causing error");
  }
}
