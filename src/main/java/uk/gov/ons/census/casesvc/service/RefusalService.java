package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.RefusalDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@Service
public class RefusalService {
  private static final String REFUSAL_RECEIVED = "Refusal Received";
  private final CaseService caseService;
  private final CaseRepository caseRepository;
  private final EventLogger eventLogger;

  public RefusalService(
      CaseService caseService, CaseRepository caseRepository, EventLogger eventLogger) {
    this.caseService = caseService;
    this.caseRepository = caseRepository;
    this.eventLogger = eventLogger;
  }

  public void processRefusal(ResponseManagementEvent refusalEvent) {
    RefusalDTO refusal = refusalEvent.getPayload().getRefusal();
    Case caze = caseService.getCaseByCaseId(UUID.fromString(refusal.getCollectionCase().getId()));
    caze.setRefusalReceived(true);
    caseRepository.saveAndFlush(caze);
    caseService.emitCaseUpdatedEvent(caze);

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
