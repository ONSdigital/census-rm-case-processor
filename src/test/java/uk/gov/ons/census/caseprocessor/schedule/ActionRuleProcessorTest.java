package uk.gov.ons.census.caseprocessor.schedule;

import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.ons.census.caseprocessor.model.repository.ActionRuleRepository;
import uk.gov.ons.census.common.model.entity.ActionRule;
import uk.gov.ons.census.common.model.entity.ActionRuleStatus;
import uk.gov.ons.census.common.model.entity.ActionRuleType;
import uk.gov.ons.census.common.model.entity.CollectionExercise;

class ActionRuleProcessorTest {
  private final ActionRuleRepository actionRuleRepository = mock(ActionRuleRepository.class);
  private final CaseClassifier caseClassifier = mock(CaseClassifier.class);

  @Test
  void testProcessTriggeredActionRule() {
    // Given
    ActionRule actionRule = setUpActionRule(ActionRuleType.EXPORT_FILE);
    int mockSelectedCases = 1729;
    when(caseClassifier.enqueueCasesForActionRule(actionRule)).thenReturn(mockSelectedCases);

    // When
    ActionRuleProcessor underTest = new ActionRuleProcessor(caseClassifier, actionRuleRepository);
    underTest.processTriggeredActionRule(actionRule);

    // Then
    ArgumentCaptor<ActionRule> actionRuleCaptor = ArgumentCaptor.forClass(ActionRule.class);
    verify(actionRuleRepository, times(1)).save(actionRuleCaptor.capture());
    verify(caseClassifier).enqueueCasesForActionRule(actionRule);

    ActionRule savedActionRule = actionRuleCaptor.getAllValues().get(0);
    Assertions.assertThat(savedActionRule.isHasTriggered()).isTrue();
    Assertions.assertThat(savedActionRule.getActionRuleStatus())
        .isEqualTo(ActionRuleStatus.PROCESSING_CASES);
    Assertions.assertThat(savedActionRule.getSelectedCaseCount()).isEqualTo(mockSelectedCases);
  }

  private ActionRule setUpActionRule(ActionRuleType actionRuleType) {
    ActionRule actionRule = new ActionRule();
    UUID actionRuleId = UUID.randomUUID();
    actionRule.setId(actionRuleId);
    actionRule.setTriggerDateTime(OffsetDateTime.now());
    actionRule.setHasTriggered(false);
    actionRule.setActionRuleStatus(ActionRuleStatus.SELECTING_CASES);

    actionRule.setType(actionRuleType);

    CollectionExercise collectionExercise = new CollectionExercise();
    collectionExercise.setId(UUID.randomUUID());

    actionRule.setCollectionExercise(collectionExercise);

    return actionRule;
  }
}
