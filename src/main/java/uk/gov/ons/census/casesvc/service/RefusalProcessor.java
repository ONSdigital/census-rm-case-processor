package uk.gov.ons.census.casesvc.service;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.RefusalDTO;
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
  private final UacProcessor uacProcessor;
  private final EventLogger eventLogger;

  public RefusalProcessor(
      CaseProcessor caseProcessor,
      CaseRepository caseRepository,
      UacProcessor uacProcessor,
      UacQidLinkRepository uacQidLinkRepository,
      EventLogger eventLogger) {
    this.caseProcessor = caseProcessor;
    this.caseRepository = caseRepository;
    this.uacProcessor = uacProcessor;
    this.uacQidLinkRepository = uacQidLinkRepository;
    this.eventLogger = eventLogger;
  }

  public void processRefusal(RefusalDTO refusal, Map<String, String> headers) {
    String questionnaireId = refusal.getQuestionnaire_Id();

    Optional<UacQidLink> uacQidLinkOpt = uacQidLinkRepository.findByQid(questionnaireId);

    if (uacQidLinkOpt.isEmpty()) {
      log.error(QID_NOT_FOUND_ERROR);
      throw new RuntimeException(
          String.format("Questionnaire Id '%s' not found!", questionnaireId));
    }

    UacQidLink uacQidLink = uacQidLinkOpt.get();
    Case caze = uacQidLink.getCaze();

    caze.setRefusalReceived(true);
    caseRepository.saveAndFlush(caze);

    caseProcessor.emitCaseUpdatedEvent(caze);

    eventLogger.logRefusalEvent(
        uacQidLink,
        REFUSAL_RECEIVED,
        EventType.CASE_UPDATED,
        refusal,
        headers,
        refusal.getResponseDateTime());
  }
}
