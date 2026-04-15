package uk.gov.ons.census.caseprocessor.testutils;

import java.util.UUID;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.ons.census.caseprocessor.model.repository.ActionRuleRepository;
import uk.gov.ons.ssdc.common.model.entity.ActionRule;

@Component
@ActiveProfiles("test")
public class ActionRulePoller {
  private final ActionRuleRepository actionRuleRepository;

  public ActionRulePoller(ActionRuleRepository actionRuleRepository) {
    this.actionRuleRepository = actionRuleRepository;
  }

  @Retryable(
      retryFor = {ActionRuleNotTriggeredFoundException.class},
      maxAttempts = 10,
      backoff = @Backoff(delay = 2000),
      listeners = {"retryListener"})
  public ActionRule getTriggeredActionRule(UUID actionRuleId) throws Exception {

    ActionRule actionRule =
        actionRuleRepository
            .findById(actionRuleId)
            .orElseThrow(() -> new Exception("Action Rule not set up with id " + actionRuleId));

    if (!actionRule.isHasTriggered()) {
      throw new ActionRuleNotTriggeredFoundException(
          "Action Rule " + actionRuleId + " not triggered yet");
    }

    return actionRule;
  }
}
