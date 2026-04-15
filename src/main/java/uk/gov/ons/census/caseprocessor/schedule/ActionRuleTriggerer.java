package uk.gov.ons.census.caseprocessor.schedule;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.caseprocessor.model.repository.ActionRuleRepository;
import uk.gov.ons.ssdc.common.model.entity.ActionRule;
import uk.gov.ons.ssdc.common.model.entity.ActionRuleStatus;

@Component
public class ActionRuleTriggerer {
  private static final Logger log = LoggerFactory.getLogger(ActionRuleTriggerer.class);
  private final ActionRuleRepository actionRuleRepository;
  private final ActionRuleProcessor actionRuleProcessor;

  private String hostName = InetAddress.getLocalHost().getHostName();

  public ActionRuleTriggerer(
      ActionRuleRepository actionRuleRepository, ActionRuleProcessor actionRuleProcessor)
      throws UnknownHostException {
    this.actionRuleRepository = actionRuleRepository;
    this.actionRuleProcessor = actionRuleProcessor;
  }

  @Transactional
  public void triggerAllActionRules() {
    // NOTE: This method must be run by a single instance only to avoid duplication and conflicts

    // TODO: This method currently runs the entire loop in one transaction.
    // It would be preferable to have a separate transaction for each action rule triggered. This
    // will require some refactoring, as transactional methods must be called via Spring injection
    // to function correctly, so the looping will have to be moved out to a different class (perhaps
    // just up into the ActionRuleScheduler?).

    List<ActionRule> triggeredActionRules =
        actionRuleRepository.findByTriggerDateTimeBeforeAndHasTriggeredIsFalse(
            OffsetDateTime.now());

    for (ActionRule triggeredActionRule : triggeredActionRules) {
      try {
        log.atInfo()
            .setMessage("Action rule selecting cases")
            .addKeyValue("hostName", hostName)
            .addKeyValue("id", triggeredActionRule.getId())
            .log();

        actionRuleProcessor.updateActionRuleStatus(
            triggeredActionRule, ActionRuleStatus.SELECTING_CASES);

        // This call will block for the duration of selecting and enqueuing the cases
        actionRuleProcessor.processTriggeredActionRule(triggeredActionRule);

        log.atInfo()
            .setMessage("Action rule triggered")
            .addKeyValue("hostName", hostName)
            .addKeyValue("id", triggeredActionRule.getId())
            .log();

      } catch (BadSqlGrammarException badSqlGrammarException) {
        // This exception can occur if the classifier SQL clause for a rule is broken. In this case,
        // the rule will never function, and must be aborted and marked ERRORED and triggered, to
        // stop it failing indefinitely.
        String errorMessage =
            "ActionRule "
                + triggeredActionRule.getId()
                + " failed with a BadSqlGrammarException,"
                + " it has been marked Triggered to stop it running until it is fixed."
                + " Exception Message: "
                + badSqlGrammarException.getMessage();
        log.atError()
            .setMessage(errorMessage)
            .addKeyValue("hostName", hostName)
            .addKeyValue("id", triggeredActionRule.getId())
            .log();

        triggeredActionRule.setActionRuleStatus(ActionRuleStatus.ERRORED);
        triggeredActionRule.setHasTriggered(true);
        actionRuleRepository.save(triggeredActionRule);

      } catch (Exception e) {
        // Log any unexpected/unknown errors, but allow the transaction to rollback so that it
        // can be retried, in the case the errors are transient.
        // NOTE: This could result in an infinite error loop on a broken action rule, if the error
        // is not transient. If this happens, we need to manually intervene, and we should amend the
        // code here to catch such errors and abort the rule as we do for badSqlGrammarException
        log.atError()
            .setMessage("Unexpected error while executing action rule")
            .setCause(e)
            .addKeyValue("hostName", hostName)
            .addKeyValue("id", triggeredActionRule.getId())
            .log();
      }
    }
  }
}
