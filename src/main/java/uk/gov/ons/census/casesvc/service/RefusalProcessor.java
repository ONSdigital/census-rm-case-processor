package uk.gov.ons.census.casesvc.service;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.RefusalDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@Service
public class RefusalProcessor {
  private static final Logger log = LoggerFactory.getLogger(RefusalProcessor.class);
  private static final String REFUSAL_RECEIVED = "Refusal Received";
  private static final String CASE_NOT_FOUND_ERROR = "Case Id not found error";
  private final CaseProcessor caseProcessor;
  private final CaseRepository caseRepository;
  private final UacQidLinkRepository uacQidLinkRepository;
  private final EventLogger eventLogger;

  public RefusalProcessor(
      CaseProcessor caseProcessor,
      CaseRepository caseRepository,
      UacQidLinkRepository uacQidLinkRepository,
      EventLogger eventLogger) {
    this.caseProcessor = caseProcessor;
    this.caseRepository = caseRepository;
    this.uacQidLinkRepository = uacQidLinkRepository;
    this.eventLogger = eventLogger;
  }

  public void processRefusal(ResponseManagementEvent refusalEvent) {
    RefusalDTO refusal = refusalEvent.getPayload().getRefusal();
    UUID caseId = UUID.fromString(refusal.getCollectionCase().getId());
    Optional<Case> optCase  = caseRepository.findByCaseId(caseId);

    if (optCase.isEmpty()) {
      log.error(CASE_NOT_FOUND_ERROR);
      throw new RuntimeException(
          String.format("Case Id '%s' not found!", caseId.toString()));
    }

    Case caze = optCase.get();

    caze.setRefusalReceived(true);
    caseRepository.saveAndFlush(caze);

    caseProcessor.emitCaseUpdatedEvent(caze);

    eventLogger.logRefusalEvent(
        caze,
        REFUSAL_RECEIVED,
        EventType.CASE_UPDATED,
        refusal,
        refusalEvent.getEvent());
  }
}
