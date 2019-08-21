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
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@Service
public class FulfilmentRequestProcessor {
  private static final String FULFILMENT_REQUEST_RECEIVED = "Fulfilment Request Received";
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

    Case caze =
        caseProcessor.getCaseByCaseId(UUID.fromString(fulfilmentRequestPayload.getCaseId()));

    eventLogger.logCaseEvent(
        caze,
        fulfilmentRequestEvent.getDateTime(),
        OffsetDateTime.now(),
        FULFILMENT_REQUEST_RECEIVED,
        FULFILMENT_REQUESTED,
        fulfilmentRequestEvent,
        convertObjectToJson(fulfilmentRequestPayload));

    if (individualResponseRequestCodes.contains(fulfilmentRequestPayload.getFulfilmentCode())) {
      Case individualResponseCase = saveIndividualResponseCaseFromParentCase(caze);
      caseProcessor.emitCaseCreatedEvent(individualResponseCase);
    }
  }

  private Case saveIndividualResponseCaseFromParentCase(Case parentCase) {
    Case individualResponseCase = new Case();

    individualResponseCase.setCaseId(UUID.randomUUID());
    individualResponseCase.setCaseRef(caseProcessor.getUniqueCaseRef());
    individualResponseCase.setState(CaseState.ACTIONABLE);
    individualResponseCase.setCreatedDateTime(OffsetDateTime.now());
    individualResponseCase.setAddressType(HOUSEHOLD_INDIVIDUAL_RESPONSE_ADDRESS_TYPE);

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

    caseRepository.save(individualResponseCase);

    return individualResponseCase;
  }
}
