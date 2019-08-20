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
import org.springframework.util.StringUtils;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.FulfilmentRequestDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.CaseState;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@Service
public class FulfilmentRequestProcessor {

  private static final Logger log = LoggerFactory.getLogger(FulfilmentRequestProcessor.class);

  private static final String CASE_NOT_FOUND_ERROR = "Case not found error";
  private static final String DATETIME_NOT_PRESENT = "Date time not in event error";
  private static final String FULFILMENT_REQUEST_RECEIVED = "Fulfilment Request Received";
  private static final String FULFILMENT_CODE_NOT_FOUND = "Fulfilment Code not found";

  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_ADDRESS_TYPE = "HI";

  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_ENGLAND = "UACIT1";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_ENGLISH = "UACIT2";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_WELSH = "UACIT2W";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_NORTHERN_IRELAND = "UACIT4";
  private static final Set<String> individualResponseRequestCodes =
      new HashSet<>(
          Arrays.asList(
              HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_ENGLAND,
              HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_ENGLISH,
              HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_WELSH,
              HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_NORTHERN_IRELAND));

  private final CaseRepository caseRepository;
  private final EventLogger eventLogger;
  private final CaseProcessor caseProcessor;

  public FulfilmentRequestProcessor(
      CaseRepository caseRepository, EventLogger eventLogger, CaseProcessor caseProcessor) {
    this.caseRepository = caseRepository;
    this.eventLogger = eventLogger;
    this.caseProcessor = caseProcessor;
  }

  public void processFulfilmentRequest(ResponseManagementEvent fulfilmentRequest) {
    EventDTO fulfilmentRequestEvent = fulfilmentRequest.getEvent();
    FulfilmentRequestDTO fulfilmentRequestPayload =
        fulfilmentRequest.getPayload().getFulfilmentRequest();

    Case caze = getCaseByCaseId(fulfilmentRequestPayload);

    validateFulfilmentRequiredFields(
        fulfilmentRequestEvent, fulfilmentRequestPayload, caze.getCaseId().toString());

    eventLogger.logFulfilmentRequestedEvent(
        caze,
        caze.getCaseId(),
        fulfilmentRequestEvent.getDateTime(),
        FULFILMENT_REQUEST_RECEIVED,
        FULFILMENT_REQUESTED,
        fulfilmentRequestPayload,
        fulfilmentRequestEvent);

    if (individualResponseRequestCodes.contains(fulfilmentRequestPayload.getFulfilmentCode())) {
      Case individualResponseCase = saveIndividualResponseCase(caze);
      caseProcessor.emitCaseCreatedEvent(individualResponseCase);
    }
  }

  private Case saveIndividualResponseCase(Case caze) {
    Case individualResponseCase =
        caze.toBuilder()
            .caseId(UUID.randomUUID())
            .caseRef(caseProcessor.getUniqueCaseRef())
            .uacQidLinks(null)
            .events(null)
            .createdDateTime(OffsetDateTime.now())
            .state(CaseState.ACTIONABLE)
            .receiptReceived(false)
            .refusalReceived(false)
            .addressType(HOUSEHOLD_INDIVIDUAL_RESPONSE_ADDRESS_TYPE)
            .addressLevel(null)
            .htcWillingness(null)
            .htcDigital(null)
            .fieldCoordinatorId(null)
            .fieldOfficerId(null)
            .treatmentCode(null)
            .ceExpectedCapacity(null)
            .build();

    caseRepository.save(individualResponseCase);

    return individualResponseCase;
  }

  private void validateFulfilmentRequiredFields(
      EventDTO eventDTO, FulfilmentRequestDTO fulfilmentRequestDTO, String caseId) {
    if (eventDTO.getDateTime() == null) {
      log.error(DATETIME_NOT_PRESENT);
      throw new RuntimeException(
          String.format("Date time not found in fulfilment request event for Case ID '%s", caseId));
    }

    if (StringUtils.isEmpty(fulfilmentRequestDTO.getFulfilmentCode())) {
      log.error(FULFILMENT_CODE_NOT_FOUND);
      throw new RuntimeException(
          String.format(
              "Fulfilment code '%s' not found from event for Case ID '%s",
              caseId, fulfilmentRequestDTO.getFulfilmentCode()));
    }
  }

  private Case getCaseByCaseId(FulfilmentRequestDTO fulfilmentRequest) {
    Optional<Case> cazeResult =
        caseRepository.findByCaseId(UUID.fromString(fulfilmentRequest.getCaseId()));

    if (cazeResult.isEmpty()) {
      log.error(CASE_NOT_FOUND_ERROR);
      throw new RuntimeException(
          String.format("Case ID '%s' not found!", fulfilmentRequest.getCaseId()));
    }
    return cazeResult.get();
  }
}
