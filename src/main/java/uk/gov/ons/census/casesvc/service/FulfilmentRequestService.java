package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.model.entity.EventType.FULFILMENT_REQUESTED;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.FulfilmentRequestDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacCreatedDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;

@Service
public class FulfilmentRequestService {
  private static final String FULFILMENT_REQUEST_RECEIVED = "Fulfilment Request Received";

  private static final Set<String> individualResponsePrintRequestCodes =
      new HashSet<>(
          Arrays.asList(
              "P_OR_I1", // HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_ENGLAND_PRINT,
              "P_OR_I2", // HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_ENGLISH_PRINT,
              "P_OR_I2W", // HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_WELSH_PRINT,
              "P_OR_I4", // HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_NI_PRINT,
              "P_UAC_UACIP1", // INDIVIDUAL_UAC_ENGLAND_PRINT_LETTER
              "P_UAC_UACIP2B", // INDIVIDUAL_UAC_WALES_BILINGUAL_PRINT_LETTER
              "P_UAC_UACIP4", // INDIVIDUAL_UAC_NI_PRINT_LETTER
              "P_LP_ILP1", // INDIVIDUAL_QUESTIONNAIRE_LARGE_PRINT_ENGLAND_LETTER
              "P_LP_ILP2", // INDIVIDUAL_QUESTIONNAIRE_LARGE_PRINT_WALES_ENGLISH_LETTER
              "P_LP_ILP2W", // INDIVIDUAL_QUESTIONNAIRE_LARGE_PRINT_WALES_WELSH_LETTER
              "P_LP_IL4" // INDIVIDUAL_QUESTIONNAIRE_LARGE_PRINT_NI_LETTER
              ));

  private static final Set<String> individualResponseRequestCodes;

  static {
    individualResponseRequestCodes =
        new HashSet<>(
            Arrays.asList(
                "RM_TC_HI", // RM_HOUSEHOLD_INDIVIDUAL_TELEPHONE_CAPTURE
                "UACIT1", // HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_ENGLAND_SMS,
                "UACIT2", // HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_ENGLISH_SMS,
                "UACIT2W", // HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_WELSH_SMS,
                "UACIT4" // HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_NI_SMS
                ));
    individualResponseRequestCodes.addAll(individualResponsePrintRequestCodes);
  }

  private final EventLogger eventLogger;
  private final CaseService caseService;
  private final UacService uacService;

  public FulfilmentRequestService(
      EventLogger eventLogger, CaseService caseService, UacService uacService) {
    this.eventLogger = eventLogger;
    this.caseService = caseService;
    this.uacService = uacService;
  }

  public void processFulfilmentRequest(
      ResponseManagementEvent fulfilmentRequest, OffsetDateTime messageTimestamp) {
    EventDTO fulfilmentRequestEvent = fulfilmentRequest.getEvent();
    FulfilmentRequestDTO fulfilmentRequestPayload =
        fulfilmentRequest.getPayload().getFulfilmentRequest();

    Case caze = caseService.getCaseByCaseId(UUID.fromString(fulfilmentRequestPayload.getCaseId()));

    // As part of a fulfilment, we might need to create a 'child' case (an individual).
    // We will do this only if the fulfilment is for an Individual and the caze caseType is HH.
    handleIndividualFulfilmentForHHCase(fulfilmentRequestPayload, caze);

    // As part of a fulfilment, we might have created a new UAC-QID pair, which needs to be linked
    // to the case it belongs to
    handleUacQidCreated(fulfilmentRequest, messageTimestamp);

    // we do not want to log contact details for fulfillment requests
    fulfilmentRequestPayload.setContact(null);

    eventLogger.logCaseEvent(
        caze,
        fulfilmentRequestEvent.getDateTime(),
        FULFILMENT_REQUEST_RECEIVED,
        FULFILMENT_REQUESTED,
        fulfilmentRequestEvent,
        convertObjectToJson(fulfilmentRequestPayload),
        messageTimestamp);
  }

  private void handleUacQidCreated(
      ResponseManagementEvent responseManagementEvent, OffsetDateTime messageTimestamp) {
    UacCreatedDTO uacQidCreated =
        responseManagementEvent.getPayload().getFulfilmentRequest().getUacQidCreated();

    // There might not always be a new UAC-QID as part of a fulfilment
    if (uacQidCreated != null) {
      uacService.ingestUacCreatedEvent(responseManagementEvent, messageTimestamp, uacQidCreated);
    }
  }

  private void handleIndividualFulfilmentForHHCase(
      FulfilmentRequestDTO fulfilmentRequestPayload, Case caze) {
    if (caze.getCaseType().equals("HH")
        && individualResponseRequestCodes.contains(fulfilmentRequestPayload.getFulfilmentCode())) {
      Case individualResponseCase =
          caseService.prepareIndividualResponseCaseFromParentCase(
              caze, UUID.fromString(fulfilmentRequestPayload.getIndividualCaseId()));
      individualResponseCase = caseService.saveNewCaseAndStampCaseRef(individualResponseCase);

      if (individualResponsePrintRequestCodes.contains(
          fulfilmentRequestPayload.getFulfilmentCode())) {
        // If the fulfilment is for PRINT then we need to send the case to Action Scheduler as well
        caseService.emitCaseCreatedEvent(individualResponseCase, fulfilmentRequestPayload);
      } else {
        caseService.emitCaseCreatedEvent(individualResponseCase);
      }
    }
  }
}
