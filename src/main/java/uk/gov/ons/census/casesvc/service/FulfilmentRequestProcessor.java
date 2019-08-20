package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.model.entity.EventType.FULFILMENT_REQUESTED;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.time.OffsetDateTime;
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
    EventDTO fulfilmentRequestEvent = fulfilmentRequest.getEvent();
    FulfilmentRequestDTO fulfilmentRequestPayload =
        fulfilmentRequest.getPayload().getFulfilmentRequest();

    String caseId = fulfilmentRequestPayload.getCaseId();

    Optional<Case> cazeResult = caseRepository.findByCaseId(UUID.fromString(caseId));

    if (cazeResult.isEmpty()) {
      log.error(CASE_NOT_FOUND_ERROR);
      throw new RuntimeException(String.format("Case ID '%s' not found!", caseId));
    }

    if (fulfilmentRequestEvent.getDateTime() == null) {
      log.error(DATETIME_NOT_PRESENT);
      throw new RuntimeException(
          String.format("Date time not found in fulfilment request event for Case ID '%s", caseId));
    }

    Case caze = cazeResult.get();

    eventLogger.logCaseEvent(
        caze,
        fulfilmentRequestEvent.getDateTime(),
        OffsetDateTime.now(),
        FULFILMENT_REQUEST_RECEIVED,
        FULFILMENT_REQUESTED,
        fulfilmentRequestEvent,
        convertObjectToJson(fulfilmentRequestPayload));
  }
}
