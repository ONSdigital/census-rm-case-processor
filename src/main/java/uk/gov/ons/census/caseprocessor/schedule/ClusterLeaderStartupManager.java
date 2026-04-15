package uk.gov.ons.census.caseprocessor.schedule;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.caseprocessor.model.repository.ClusterLeaderRepository;
import uk.gov.ons.ssdc.common.model.entity.ClusterLeader;

@Component
public class ClusterLeaderStartupManager {
  private static final Logger log = LoggerFactory.getLogger(ClusterLeaderStartupManager.class);
  private final ClusterLeaderRepository clusterLeaderRepository;

  private String hostName = InetAddress.getLocalHost().getHostName();

  public ClusterLeaderStartupManager(ClusterLeaderRepository clusterLeaderRepository)
      throws UnknownHostException {
    this.clusterLeaderRepository = clusterLeaderRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
  public void doStartupChecksAndAttemptToElectLeaderIfRequired(UUID leaderId) {
    if (clusterLeaderRepository.existsById(leaderId)) {
      return; // We are not starting up for the first time... nothing to do here
    }

    // We ARE starting up for the first time!
    // Record that this host is the cluster leader in the database
    ClusterLeader clusterLeader = new ClusterLeader();
    clusterLeader.setId(leaderId);
    clusterLeader.setHostName(hostName);
    clusterLeader.setHostLastSeenAliveAt(OffsetDateTime.now());
    clusterLeaderRepository.saveAndFlush(clusterLeader);

    log.atDebug()
        .setMessage("No leader existed in DB, so this host is attempting to become leader")
        .addKeyValue("hostName", hostName)
        .log();
  }
}
