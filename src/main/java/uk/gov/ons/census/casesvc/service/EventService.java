package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.EventHelper.createEventDTO;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.utility.FieldworkHelper;
import uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper;

@Service
public class EventService {

  public static final String CREATE_CASE_SAMPLE_RECEIVED = "Create case sample received";
  private static final String CREATE_BULK_CASE_SAMPLE_RECEIVED = "Create bulk case sample received";
  private static final String BULK_PROCESSING_EVENT_CHANNEL = "AR"; // Address Resolution
  private static final String BULK_PROCESSING_EVENT_SOURCE = "RM";

  private final CaseService caseService;
  private final UacService uacService;
  private final EventLogger eventLogger;

  public EventService(CaseService caseService, UacService uacService, EventLogger eventLogger) {
    this.caseService = caseService;
    this.uacService = uacService;
    this.eventLogger = eventLogger;
  }

  public void processSampleReceivedMessage(
      CreateCaseSample createCaseSample, OffsetDateTime messageTimestamp) {
    Case caze = caseService.saveCaseSample(createCaseSample);
    int questionnaireType =
        QuestionnaireTypeHelper.calculateQuestionnaireType(
            caze.getCaseType(), caze.getRegion(), caze.getAddressLevel());
    if (createCaseSample.isBulkProcessed()) {
      caseService.saveCaseAndEmitCaseCreatedEvent(caze, buildSendToFieldIfConditionsMet(caze));

      eventLogger.logCaseEvent(
          caze,
          OffsetDateTime.now(),
          CREATE_BULK_CASE_SAMPLE_RECEIVED,
          EventType.CLERICAL_ADDRESS_RESOLUTION,
          createEventDTO(
              EventTypeDTO.CLERICAL_ADDRESS_RESOLUTION,
              BULK_PROCESSING_EVENT_CHANNEL,
              BULK_PROCESSING_EVENT_SOURCE),
          createCaseSample,
          messageTimestamp);
    } else {
      EventDTO dummyEvent = new EventDTO();
      dummyEvent.setSource("RM");
      dummyEvent.setChannel("RM");
      UacQidLink uacQidLink = uacService.buildUacQidLink(caze, questionnaireType, null, dummyEvent);
      uacService.saveAndEmitUacUpdatedEvent(uacQidLink);
      caseService.saveCaseAndEmitCaseCreatedEvent(caze);

      eventLogger.logCaseEvent(
          caze,
          OffsetDateTime.now(),
          CREATE_CASE_SAMPLE_RECEIVED,
          EventType.SAMPLE_LOADED,
          createEventDTO(EventTypeDTO.SAMPLE_LOADED),
          createCaseSample,
          messageTimestamp);

      if (QuestionnaireTypeHelper.isQuestionnaireWelsh(caze.getTreatmentCode())) {
        uacQidLink = uacService.buildUacQidLink(caze, 3, null, dummyEvent);
        uacService.saveAndEmitUacUpdatedEvent(uacQidLink);
      }
    }
  }

  private Metadata buildSendToFieldIfConditionsMet(Case caze) {
    if (FieldworkHelper.shouldSendCaseToField(caze)) {
      return buildMetadata(EventTypeDTO.CLERICAL_ADDRESS_RESOLUTION, ActionInstructionType.CREATE);
    }

    return null;
  }

  public void processPrintCaseSelected(
      ResponseManagementEvent responseManagementEvent, OffsetDateTime messageTimestamp) {
    processEvent(
        responseManagementEvent.getPayload().getPrintCaseSelected().getCaseRef(),
        responseManagementEvent,
        String.format(
            "Case sent to printer with pack code %s",
            responseManagementEvent.getPayload().getPrintCaseSelected().getPackCode()),
        EventType.PRINT_CASE_SELECTED,
        messageTimestamp);
  }

  public void processFieldCaseSelected(
      ResponseManagementEvent responseManagementEvent, OffsetDateTime messageTimestamp) {
    processEvent(
        responseManagementEvent.getPayload().getFieldCaseSelected().getCaseRef(),
        responseManagementEvent,
        "Case sent for fieldwork followup",
        EventType.FIELD_CASE_SELECTED,
        messageTimestamp);
  }

  private void processEvent(
      long caseRef,
      ResponseManagementEvent responseManagementEvent,
      String eventDescription,
      EventType eventType,
      OffsetDateTime messageTimestamp) {
    Case caze = caseService.getCaseByCaseRef(caseRef);

    eventLogger.logCaseEvent(
        caze,
        responseManagementEvent.getEvent().getDateTime(),
        eventDescription,
        eventType,
        responseManagementEvent.getEvent(),
        responseManagementEvent.getPayload(),
        messageTimestamp);
  }
}
