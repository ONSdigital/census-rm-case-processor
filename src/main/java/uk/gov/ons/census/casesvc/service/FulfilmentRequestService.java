package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.model.entity.EventType.FULFILMENT_REQUESTED;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.FulfilmentRequestDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;

@Service
public class FulfilmentRequestService {
  private static final String FULFILMENT_REQUEST_RECEIVED = "Fulfilment Request Received";
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

  private final EventLogger eventLogger;
  private final CaseService caseService;

  public FulfilmentRequestService(EventLogger eventLogger, CaseService caseService) {
    this.eventLogger = eventLogger;
    this.caseService = caseService;
  }

  public void processFulfilmentRequest(ResponseManagementEvent fulfilmentRequest) {
    EventDTO fulfilmentRequestEvent = fulfilmentRequest.getEvent();
    FulfilmentRequestDTO fulfilmentRequestPayload =
        fulfilmentRequest.getPayload().getFulfilmentRequest();

    Case caze = caseService.getCaseByCaseId(UUID.fromString(fulfilmentRequestPayload.getCaseId()));

    eventLogger.logCaseEvent(
        caze,
        fulfilmentRequestEvent.getDateTime(),
        FULFILMENT_REQUEST_RECEIVED,
        FULFILMENT_REQUESTED,
        fulfilmentRequestEvent,
        convertObjectToJson(fulfilmentRequestPayload));

    if (individualResponseRequestCodes.contains(fulfilmentRequestPayload.getFulfilmentCode())) {
      Case individualResponseCase = caseService.prepareIndividualResponseCaseFromParentCase(caze);
      caseService.saveAndEmitCaseCreatedEvent(individualResponseCase);
    }
  }
}
