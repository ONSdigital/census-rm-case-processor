package uk.gov.ons.census.caseprocessor.collectioninstrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.ons.census.caseprocessor.model.repository.CollectionExerciseRepository;
import uk.gov.ons.census.common.model.entity.CollectionExercise;
import uk.gov.ons.census.common.model.entity.CollectionInstrumentSelectionRule;

@ExtendWith(MockitoExtension.class)
class RulesCacheTest {
  @Mock CollectionExerciseRepository collectionExerciseRepository;

  @InjectMocks RulesCache underTest;

  @Test
  void getRules() {
    UUID collexId = UUID.randomUUID();
    CollectionExercise collex = new CollectionExercise();
    collex.setCollectionInstrumentSelectionRules(
        new CollectionInstrumentSelectionRule[] {
          new CollectionInstrumentSelectionRule(3, "'foo' == 'bar'", "url3", null),
          new CollectionInstrumentSelectionRule(1, "'baz' == 'brr'", "url1", null),
          new CollectionInstrumentSelectionRule(2, "'fiz' == 'foz'", "url2", null)
        });

    when(collectionExerciseRepository.findById(collexId)).thenReturn(Optional.of(collex));

    CachedRule[] rules = underTest.getRules(collexId);

    assertThat(rules.length).isEqualTo(3);

    assertThat(rules[0].getPriority()).isEqualTo(3);
    assertThat(rules[0].getSpelExpression().getExpressionString()).isEqualTo("'foo' == 'bar'");

    assertThat(rules[1].getPriority()).isEqualTo(2);
    assertThat(rules[1].getSpelExpression().getExpressionString()).isEqualTo("'fiz' == 'foz'");

    assertThat(rules[2].getPriority()).isEqualTo(1);
    assertThat(rules[2].getSpelExpression().getExpressionString()).isEqualTo("'baz' == 'brr'");
  }
}
