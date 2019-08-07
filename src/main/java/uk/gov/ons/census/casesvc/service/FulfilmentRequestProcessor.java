package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.model.entity.EventType.FULFILMENT_REQUESTED;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.FulfilmentRequestDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@Service
public class FulfilmentRequestProcessor {

  private static final Logger log = LoggerFactory.getLogger(FulfilmentRequestProcessor.class);

  private static final String CASE_NOT_FOUND_ERROR = "Case not found error";
  private static final String DATETIME_NOT_PRESENT = "Date time not in event error";
  private static final String FULFILMENT_REQUEST_RECEIVED = "Fulfilment Request Received";

  private final CaseRepository caseRepository;
  private final EventLogger eventLogger;

  public FulfilmentRequestProcessor(CaseRepository caseRepository, EventLogger eventLogger) {
    this.caseRepository = caseRepository;
    this.eventLogger = eventLogger;
  }

  public void processFulfilmentRequest(ResponseManagementEvent fulfilmentRequest) {
    EventDTO event = fulfilmentRequest.getEvent();
    FulfilmentRequestDTO fulfilmentRequestPayload =
        fulfilmentRequest.getPayload().getFulfilmentRequest();

    String caseId = fulfilmentRequestPayload.getCaseId();

    Optional<Case> cazeResult = caseRepository.findByCaseId(UUID.fromString(caseId));

    if (cazeResult.isEmpty()) {
      log.error(CASE_NOT_FOUND_ERROR);
      throw new RuntimeException(
          String.format("Case ID '%s' not found!", fulfilmentRequestPayload.getCaseId()));
    }

    if (event.getDateTime() == null) {
      log.error(DATETIME_NOT_PRESENT);
      throw new RuntimeException(
          String.format("Date time not found in fulfilment request event for case '%s", caseId));
    }

    Case caze = cazeResult.get();

    eventLogger.logFulfilmentRequestedEvent(
        caze,
        UUID.fromString(fulfilmentRequestPayload.getCaseId()),
        event.getDateTime(),
        FULFILMENT_REQUEST_RECEIVED,
        FULFILMENT_REQUESTED,
        fulfilmentRequestPayload,
        fulfilmentRequest.getEvent());
  }
}
