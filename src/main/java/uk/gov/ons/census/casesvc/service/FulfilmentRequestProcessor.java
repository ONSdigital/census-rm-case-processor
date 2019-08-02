package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.model.entity.EventType.FULFILMENT_REQUESTED;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.FulfilmentRequestDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;

@Service
public class FulfilmentRequestProcessor {

  private static final Logger log = LoggerFactory.getLogger(FulfilmentRequestProcessor.class);

  private static final String CASE_NOT_FOUND_ERROR = "Case not found error";

  private final CaseRepository caseRepository;
  private final EventLogger eventLogger;
  private final EventRepository eventRepository;

  public FulfilmentRequestProcessor(
      CaseRepository caseRepository, EventLogger eventLogger, EventRepository eventRepository) {
    this.caseRepository = caseRepository;
    this.eventLogger = eventLogger;
    this.eventRepository = eventRepository;
  }

  public void processFulfilmentRequest(ResponseManagementEvent fulfilmentRequest) {
    FulfilmentRequestDTO request = fulfilmentRequest.getPayload().getFulfilmentRequest();

    Optional<Case> cazeResult = caseRepository.findByCaseId(UUID.fromString(request.getCaseId()));

    if (cazeResult.isEmpty()) {
      log.error(CASE_NOT_FOUND_ERROR);
      throw new RuntimeException(String.format("Case ID '%s' not found!", request.getCaseId()));
    }

    Case caze = cazeResult.get();

    int caseRef = caze.getCaseRef();

    eventLogger.logFulfilmentRequestedEvent(
        caze,
        UUID.fromString(request.getCaseId()),
        "Fulfilment Request Received",
        FULFILMENT_REQUESTED,
        request,
        fulfilmentRequest.getEvent());
  }
}
