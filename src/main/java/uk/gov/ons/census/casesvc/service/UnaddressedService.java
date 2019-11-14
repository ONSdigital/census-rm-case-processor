package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.EventHelper.createEventDTO;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CreateUacQid;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@Service
public class UnaddressedService {

  private final UacService uacService;
  private final EventLogger eventLogger;

  public UnaddressedService(UacService uacService, EventLogger eventLogger) {
    this.uacService = uacService;
    this.eventLogger = eventLogger;
  }

  public void receiveMessage(CreateUacQid createUacQid) {
    UacQidLink uacQidLink =
        uacService.buildUacQidLink(
            null, Integer.parseInt(createUacQid.getQuestionnaireType()), createUacQid.getBatchId());
    PayloadDTO uacPayloadDTO = uacService.saveAndEmitUacUpdatedEvent(uacQidLink);
    eventLogger.logUacQidEvent(
        uacQidLink,
        OffsetDateTime.now(),
        "Unaddressed UAC/QID pair created",
        EventType.UAC_UPDATED,
        createEventDTO(EventTypeDTO.UAC_UPDATED),
        convertObjectToJson(uacPayloadDTO));
  }
}
