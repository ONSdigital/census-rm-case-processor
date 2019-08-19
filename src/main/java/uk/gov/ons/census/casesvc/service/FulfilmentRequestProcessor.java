package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.model.entity.EventType.FULFILMENT_REQUESTED;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.FulfilmentRequestDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.CaseState;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.utility.RandomCaseRefGenerator;

@Service
public class FulfilmentRequestProcessor {

  private static final Logger log = LoggerFactory.getLogger(FulfilmentRequestProcessor.class);

  private static final String CASE_NOT_FOUND_ERROR = "Case not found error";
  private static final String DATETIME_NOT_PRESENT = "Date time not in event error";
  private static final String FULFILMENT_REQUEST_RECEIVED = "Fulfilment Request Received";

  public static final Set<String> individualResponseRequestCodes =
      new HashSet<>(Arrays.asList("UACIT1", "UACIT2", "UACIT2W", "UACIT4"));
  public static final String HOUSEHOLD_INDIVIDUAL_RESPONSE = "HI";

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

    eventLogger.logFulfilmentRequestedEvent(
        caze,
        UUID.fromString(caseId),
        fulfilmentRequestEvent.getDateTime(),
        FULFILMENT_REQUEST_RECEIVED,
        FULFILMENT_REQUESTED,
        fulfilmentRequestPayload,
        fulfilmentRequestEvent);

    if (individualResponseRequestCodes.contains(fulfilmentRequestPayload.getFulfilmentCode())) {
      int caseRef = RandomCaseRefGenerator.getCaseRef();
      // Check for collisions
      if (caseRepository.existsById(caseRef)) {
        throw new RuntimeException();
      }

      Case individualResponseCase = caze.toBuilder()
          .caseId(UUID.randomUUID())
          .caseRef(caseRef)
          .uacQidLinks(null)
          .events(null)
          .createdDateTime(OffsetDateTime.now())
          .state(CaseState.ACTIONABLE)
          .receiptReceived(false)
          .refusalReceived(false)
          .addressType(HOUSEHOLD_INDIVIDUAL_RESPONSE)
          .addressLevel(null)
          .htcWillingness(null)
          .htcDigital(null)
          .fieldCoordinatorId(null)
          .fieldOfficerId(null)
          .treatmentCode(null)
          .ceExpectedCapacity(null)
          .build();

      caseRepository.save(individualResponseCase);

      // emit case created event

    }
  }
}
