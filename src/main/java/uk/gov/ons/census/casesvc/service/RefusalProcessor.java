package uk.gov.ons.census.casesvc.service;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.util.Optional;
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
  public static final String REFUSAL_RECEIVED = "REFUSAL_RECEIVED";
  private static final String CASE_NOT_FOUND_ERROR = "Failed to find case by receipt id";
  private static final String QID_NOT_FOUND_ERROR = "Qid not found error";
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
    Optional<UacQidLink> uacQidLinkOpt =
        uacQidLinkRepository.findByQid(refusal.getQuestionnaireId());

    if (uacQidLinkOpt.isEmpty()) {
      log.error(QID_NOT_FOUND_ERROR);
      throw new RuntimeException(
          String.format("Questionnaire Id '%s' not found!", refusal.getQuestionnaireId()));
    }

    UacQidLink uacQidLink = uacQidLinkOpt.get();
    Case caze = uacQidLink.getCaze();

    caze.setRefusalReceived(true);
    caseRepository.saveAndFlush(caze);

    caseProcessor.emitCaseUpdatedEvent(caze);

    eventLogger.logEvent(
        uacQidLink,
        REFUSAL_RECEIVED,
        EventType.CASE_UPDATED,
        refusal,
        refusalEvent.getEvent(),
        refusal.getResponseDateTime());
  }
}
