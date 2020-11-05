package uk.gov.ons.census.casesvc.service;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.FulfilmentInformation;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@Service
public class FulfilmentConfirmedService {
  private static final String FULFILMENT_CONFIRMED_RECEIVED =
      "Fulfilment Confirmed Received for pack code %s";

  private final EventLogger eventLogger;
  private final CaseService caseService;
  private final UacService uacService;

  public FulfilmentConfirmedService(
      EventLogger eventLogger, CaseService caseService, UacService uacService) {
    this.eventLogger = eventLogger;
    this.caseService = caseService;
    this.uacService = uacService;
  }

  public void processFulfilmentConfirmed(
      ResponseManagementEvent fulfilmentRequest, OffsetDateTime messageTimestamp) {
    EventDTO fulfilmentRequestEvent = fulfilmentRequest.getEvent();
    FulfilmentInformation fulfilmentInformation =
        fulfilmentRequest.getPayload().getFulfilmentInformation();

    if (StringUtils.isEmpty(fulfilmentInformation.getQuestionnaireId())) {
      Case caze = caseService.getCaseByCaseRef(Long.parseLong(fulfilmentInformation.getCaseRef()));
      eventLogger.logCaseEvent(
          caze,
          fulfilmentRequestEvent.getDateTime(),
          String.format(FULFILMENT_CONFIRMED_RECEIVED, fulfilmentInformation.getFulfilmentCode()),
          EventType.FULFILMENT_CONFIRMED,
          fulfilmentRequestEvent,
          fulfilmentInformation,
          messageTimestamp);
    } else {
      UacQidLink uacQidLink = uacService.findByQid(fulfilmentInformation.getQuestionnaireId());
      eventLogger.logUacQidEvent(
          uacQidLink,
          fulfilmentRequestEvent.getDateTime(),
          String.format(FULFILMENT_CONFIRMED_RECEIVED, fulfilmentInformation.getFulfilmentCode()),
          EventType.FULFILMENT_CONFIRMED,
          fulfilmentRequestEvent,
          fulfilmentInformation,
          messageTimestamp);
    }
  }
}
