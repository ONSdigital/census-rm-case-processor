package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.RefusalDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@Service
public class RefusalProcessor {
  private static final Logger log = LoggerFactory.getLogger(RefusalProcessor.class);
  private static final String REFUSAL_RECEIVED = "Refusal Received";
  private static final String CASE_NOT_FOUND_ERROR = "Case Id not found error";
  private static final String DATETIME_NOT_PRESENT = "Date time not in event error";
  private final CaseProcessor caseProcessor;
  private final CaseRepository caseRepository;
  private final EventLogger eventLogger;

  public RefusalProcessor(
      CaseProcessor caseProcessor, CaseRepository caseRepository, EventLogger eventLogger) {
    this.caseProcessor = caseProcessor;
    this.caseRepository = caseRepository;
    this.eventLogger = eventLogger;
  }

  public void processRefusal(ResponseManagementEvent refusalEvent) {
    RefusalDTO refusal = refusalEvent.getPayload().getRefusal();
    UUID caseId = UUID.fromString(refusal.getCollectionCase().getId());
    Optional<Case> optCase = caseRepository.findByCaseId(caseId);

    if (optCase.isEmpty()) {
      log.error(CASE_NOT_FOUND_ERROR);
      throw new RuntimeException(String.format("Case Id '%s' not found!", caseId.toString()));
    }

    if (refusalEvent.getEvent().getDateTime() == null) {
      log.error(DATETIME_NOT_PRESENT);
      throw new RuntimeException(
          String.format(
              "Date time not found in refusal request event for Case Id '%s", caseId.toString()));
    }

    Case caze = optCase.get();
    caze.setRefusalReceived(true);
    caseRepository.saveAndFlush(caze);

    caseProcessor.emitCaseUpdatedEvent(caze);

    eventLogger.logCaseEvent(
        caze,
        refusalEvent.getEvent().getDateTime(),
        OffsetDateTime.now(),
        REFUSAL_RECEIVED,
        EventType.REFUSAL_RECEIVED,
        refusalEvent.getEvent(),
        convertObjectToJson(refusalEvent.getPayload().getRefusal()));
  }
}
