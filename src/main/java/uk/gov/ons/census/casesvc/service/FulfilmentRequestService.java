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
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.CaseState;

@Service
public class FulfilmentRequestService {
  private static final String FULFILMENT_REQUEST_RECEIVED = "Fulfilment Request Received";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_CASE_TYPE = "HI";

  private static final Set<String> individualResponsePrintRequestCodes =
      new HashSet<>(
          Arrays.asList(
              "P_OR_I1", // HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_ENGLAND_PRINT,
              "P_OR_I2", // HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_ENGLISH_PRINT,
              "P_OR_I2W", // HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_WELSH_PRINT,
              "P_OR_I4" // HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_NI_PRINT
              ));

  private static final Set<String> individualResponseRequestCodes;

  static {
    individualResponseRequestCodes =
        new HashSet<>(
            Arrays.asList(
                "UACIT1", // HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_ENGLAND_SMS,
                "UACIT2", // HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_ENGLISH_SMS,
                "UACIT2W", // HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_WELSH_SMS,
                "UACIT4" // HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_NI_SMS
                ));
    individualResponseRequestCodes.addAll(individualResponsePrintRequestCodes);
  }

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

    handleIndividualFulfilment(fulfilmentRequestPayload, caze);

    // we do not want to log contact details for fulfillment requests
    fulfilmentRequestPayload.setContact(null);

    eventLogger.logCaseEvent(
        caze,
        fulfilmentRequestEvent.getDateTime(),
        FULFILMENT_REQUEST_RECEIVED,
        FULFILMENT_REQUESTED,
        fulfilmentRequestEvent,
        convertObjectToJson(fulfilmentRequestPayload));
  }

  private void handleIndividualFulfilment(
      FulfilmentRequestDTO fulfilmentRequestPayload, Case caze) {
    if (individualResponseRequestCodes.contains(fulfilmentRequestPayload.getFulfilmentCode())) {
      Case individualResponseCase =
          prepareIndividualResponseCase(
              caze, UUID.fromString(fulfilmentRequestPayload.getIndividualCaseId()));

      if (individualResponsePrintRequestCodes.contains(
          fulfilmentRequestPayload.getFulfilmentCode())) {
        // If the fulfilment is for PRINT then we need to send the case to Action Scheduler as well
        caseService.saveAndEmitCaseCreatedEvent(individualResponseCase, fulfilmentRequestPayload);
      } else {
        caseService.saveAndEmitCaseCreatedEvent(individualResponseCase);
      }
    }
  }

  private Case prepareIndividualResponseCase(Case parentCase, UUID newCaseId) {
    Case individualResponseCase = new Case();

    individualResponseCase.setCaseId(newCaseId);
    individualResponseCase.setCaseRef(caseService.getUniqueCaseRef());
    individualResponseCase.setState(CaseState.ACTIONABLE);
    individualResponseCase.setCreatedDateTime(OffsetDateTime.now());
    individualResponseCase.setAddressType(parentCase.getAddressType());
    individualResponseCase.setCaseType(HOUSEHOLD_INDIVIDUAL_RESPONSE_CASE_TYPE);
    individualResponseCase.setCollectionExerciseId(parentCase.getCollectionExerciseId());
    individualResponseCase.setActionPlanId(parentCase.getActionPlanId());
    individualResponseCase.setArid(parentCase.getArid());
    individualResponseCase.setEstabArid(parentCase.getEstabArid());
    individualResponseCase.setUprn(parentCase.getUprn());
    individualResponseCase.setEstabType(parentCase.getEstabType());
    individualResponseCase.setAbpCode(parentCase.getAbpCode());
    individualResponseCase.setOrganisationName(parentCase.getOrganisationName());
    individualResponseCase.setAddressLine1(parentCase.getAddressLine1());
    individualResponseCase.setAddressLine2(parentCase.getAddressLine2());
    individualResponseCase.setAddressLine3(parentCase.getAddressLine3());
    individualResponseCase.setTownName(parentCase.getTownName());
    individualResponseCase.setPostcode(parentCase.getPostcode());
    individualResponseCase.setLatitude(parentCase.getLatitude());
    individualResponseCase.setLongitude(parentCase.getLongitude());
    individualResponseCase.setOa(parentCase.getOa());
    individualResponseCase.setLsoa(parentCase.getLsoa());
    individualResponseCase.setMsoa(parentCase.getMsoa());
    individualResponseCase.setLad(parentCase.getLad());
    individualResponseCase.setRegion(parentCase.getRegion());

    return individualResponseCase;
  }
}
