package uk.gov.ons.census.caseprocessor.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ActionRuleScheduler {
  private static final Logger log = LoggerFactory.getLogger(ActionRuleScheduler.class);
  private final ActionRuleTriggerer actionRuleTriggerer;
  private final ClusterLeaderManager clusterLeaderManager;

  public ActionRuleScheduler(
      ActionRuleTriggerer actionRuleTriggerer, ClusterLeaderManager clusterLeaderManager) {
    this.actionRuleTriggerer = actionRuleTriggerer;
    this.clusterLeaderManager = clusterLeaderManager;
  }

  @Scheduled(fixedDelayString = "${scheduler.frequency}")
  public void triggerActionRule() {
    if (!clusterLeaderManager.isThisHostClusterLeader()) {
      return; // This host (i.e. pod) is not the leader... don't do any scheduling
    }

    try {
      actionRuleTriggerer.triggerAllActionRules();
    } catch (Exception e) {
      log.error("Unexpected exception while processing action rule", e);
      throw e;
    }
  }
}
