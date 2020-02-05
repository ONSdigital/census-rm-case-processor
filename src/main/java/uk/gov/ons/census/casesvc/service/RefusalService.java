package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.RefusalDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;

@Service
public class RefusalService {

  private static final String REFUSAL_RECEIVED = "Refusal Received";
  private static final String ESTAB_ADDRESS_LEVEL = "E";
  private static final String FIELD_CHANNEL = "FIELD";

  private final CaseService caseService;
  private final EventLogger eventLogger;

  public RefusalService(CaseService caseService, EventLogger eventLogger) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  public void processRefusal(
      ResponseManagementEvent refusalEvent, OffsetDateTime messageTimestamp) {
    RefusalDTO refusal = refusalEvent.getPayload().getRefusal();
    Case caze = caseService.getCaseByCaseId(UUID.fromString(refusal.getCollectionCase().getId()));

    String channel = refusalEvent.getEvent().getChannel();
    if (isEstabLevelAddressAndChannelIsNotField(caze.getAddressLevel(), channel)) {
      throw new IllegalArgumentException(
          String.format(
              "Refusal received for Estab level case ID '%s' from channel '%s'. "
                  + "This type of refusal should ONLY come from Field",
              caze.getCaseId(), channel));
    }

    caze.setRefusalReceived(true);
    caseService.saveAndEmitCaseUpdatedEvent(caze);

    eventLogger.logCaseEvent(
        caze,
        refusalEvent.getEvent().getDateTime(),
        REFUSAL_RECEIVED,
        EventType.REFUSAL_RECEIVED,
        refusalEvent.getEvent(),
        convertObjectToJson(refusalEvent.getPayload().getRefusal()),
        messageTimestamp);
  }

  private boolean isEstabLevelAddressAndChannelIsNotField(String addressLevel, String channel) {
    return addressLevel.equals(ESTAB_ADDRESS_LEVEL) && !FIELD_CHANNEL.equalsIgnoreCase(channel);
  }
}
