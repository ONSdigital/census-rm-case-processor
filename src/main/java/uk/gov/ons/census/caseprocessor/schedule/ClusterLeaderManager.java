package uk.gov.ons.census.caseprocessor.schedule;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.caseprocessor.model.repository.ClusterLeaderRepository;
import uk.gov.ons.census.common.model.entity.ClusterLeader;

@Component
public class ClusterLeaderManager {
  private static final Logger log = LoggerFactory.getLogger(ClusterLeaderManager.class);
  private static final UUID LEADER_ID = UUID.fromString("e469807b-f2e2-47bd-acf6-74f8943ff3db");

  private final ClusterLeaderRepository clusterLeaderRepository;
  private final ClusterLeaderStartupManager clusterLeaderStartupManager;

  @Value("${scheduler.leaderDeathTimeout}")
  private int leaderDeathTimeout;

  private String hostName = InetAddress.getLocalHost().getHostName();

  public ClusterLeaderManager(
      ClusterLeaderRepository clusterLeaderRepository,
      ClusterLeaderStartupManager clusterLeaderStartupManager)
      throws UnknownHostException {
    this.clusterLeaderRepository = clusterLeaderRepository;
    this.clusterLeaderStartupManager = clusterLeaderStartupManager;
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public boolean isThisHostClusterLeader() {
    synchronized (LEADER_ID) {
      try {
        clusterLeaderStartupManager.doStartupChecksAndAttemptToElectLeaderIfRequired(LEADER_ID);
      } catch (DataIntegrityViolationException dataIntegrityViolationException) {
        // Multiple processes had a dead-heat when trying to become leader, so let the losers fail
        return false;
      }
    }

    Optional<ClusterLeader> lockedClusterLeaderOpt =
        clusterLeaderRepository.getClusterLeaderAndLockById(LEADER_ID);

    if (!lockedClusterLeaderOpt.isPresent()) {
      log.atDebug()
          .setMessage("Could not get leader row, presumably because of lock contention")
          .addKeyValue("hostName", hostName)
          .log();
      return false;
    }

    ClusterLeader clusterLeader = lockedClusterLeaderOpt.get();

    if (clusterLeader.getHostName().equals(hostName)) {
      log.atDebug().setMessage("This host is leader").addKeyValue("hostName", hostName).log();

      return true;
    } else if (clusterLeader
        .getHostLastSeenAliveAt()
        .isBefore(OffsetDateTime.now().minusSeconds(leaderDeathTimeout))) {
      String oldHostName = clusterLeader.getHostName();
      clusterLeader.setHostName(hostName);
      clusterLeader.setHostLastSeenAliveAt(OffsetDateTime.now());
      clusterLeaderRepository.saveAndFlush(clusterLeader);

      log.atDebug()
          .setMessage("Leader has transferred from dead host to this host")
          .addKeyValue("hostName", hostName)
          .addKeyValue("oldHostName", oldHostName)
          .log();

      return true;
    }

    log.atDebug().setMessage("This host is not the leader").addKeyValue("hostName", hostName).log();

    return false;
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public void leaderKeepAlive() {
    Optional<ClusterLeader> lockedClusterLeaderOpt =
        clusterLeaderRepository.getClusterLeaderAndLockById(LEADER_ID);

    if (!lockedClusterLeaderOpt.isPresent()) {
      return;
    }

    ClusterLeader clusterLeader = lockedClusterLeaderOpt.get();

    if (clusterLeader.getHostName().equals(hostName)) {
      clusterLeader.setHostLastSeenAliveAt(OffsetDateTime.now());
      clusterLeaderRepository.saveAndFlush(clusterLeader);

      log.atDebug()
          .setMessage("Leader keepalive updated. This host is leader")
          .addKeyValue("hostName", hostName)
          .log();
    }
  }
}
