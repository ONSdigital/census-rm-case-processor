package uk.gov.ons.census.caseprocessor.schedule;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.caseprocessor.model.repository.FulfilmentNextTriggerRepository;
import uk.gov.ons.census.common.model.entity.FulfilmentNextTrigger;

@Service
public class FulfilmentScheduler {
  private static final Logger log = LoggerFactory.getLogger(FulfilmentScheduler.class);
  private final ClusterLeaderManager clusterLeaderManager;
  private final FulfilmentNextTriggerRepository fulfilmentNextTriggerRepository;
  private final FulfilmentProcessor fulfilmentProcessor;

  private String hostName = InetAddress.getLocalHost().getHostName();

  public FulfilmentScheduler(
      ClusterLeaderManager clusterLeaderManager,
      FulfilmentNextTriggerRepository fulfilmentNextTriggerRepository,
      FulfilmentProcessor fulfilmentProcessor)
      throws UnknownHostException {
    this.clusterLeaderManager = clusterLeaderManager;
    this.fulfilmentNextTriggerRepository = fulfilmentNextTriggerRepository;
    this.fulfilmentProcessor = fulfilmentProcessor;
  }

  @Scheduled(fixedDelayString = "${scheduler.frequency}")
  @Transactional
  public void scheduleFulfilments() {
    if (!clusterLeaderManager.isThisHostClusterLeader()) {
      return; // This host (i.e. pod) is not the leader... don't do any scheduling
    }

    // Check if it's time to process the fulfilments
    Optional<FulfilmentNextTrigger> triggerOptional =
        fulfilmentNextTriggerRepository.findByTriggerDateTimeBefore(OffsetDateTime.now());

    if (triggerOptional.isPresent()) {
      log.atInfo()
          .setMessage("Fulfilment processing triggered")
          .addKeyValue("hostName", hostName)
          .log();

      fulfilmentProcessor.addFulfilmentBatchIdAndQuantity();

      FulfilmentNextTrigger trigger = triggerOptional.get();
      trigger.setTriggerDateTime(trigger.getTriggerDateTime().plusDays(1));
      fulfilmentNextTriggerRepository.saveAndFlush(trigger);
    }
  }
}
