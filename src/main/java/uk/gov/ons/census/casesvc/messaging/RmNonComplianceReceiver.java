package uk.gov.ons.census.casesvc.messaging;

import static uk.gov.ons.census.casesvc.utility.MsgDateHelper.getMsgTimeStamp;

import java.time.OffsetDateTime;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.CaseMetadata;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.NonComplianceType;
import uk.gov.ons.census.casesvc.service.CaseService;

@MessageEndpoint
public class RmNonComplianceReceiver {

  private final CaseService caseService;
  private final EventLogger eventLogger;

  public RmNonComplianceReceiver(CaseService caseService, EventLogger eventLogger) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  @Transactional
  @ServiceActivator(inputChannel = "rmNonComplianceInputChannel")
  public void receiveMessage(Message<ResponseManagementEvent> message) {
    OffsetDateTime messageTimestamp = getMsgTimeStamp(message);
    ResponseManagementEvent responseManagementEvent = message.getPayload();
    CollectionCase collectionCase = responseManagementEvent.getPayload().getCollectionCase();

    Case caze = caseService.getCaseByCaseId(collectionCase.getId());
    caze.setMetadata(getCaseMetaData(caze));

    caze.getMetadata()
        .setNonCompliance(
            NonComplianceType.valueOf(collectionCase.getNonComplianceStatus().name()));

    if (!StringUtils.isEmpty(collectionCase.getFieldOfficerId())) {
      caze.setFieldOfficerId(collectionCase.getFieldOfficerId());
    }

    if (!StringUtils.isEmpty(collectionCase.getFieldCoordinatorId())) {
      caze.setFieldCoordinatorId(collectionCase.getFieldCoordinatorId());
    }

    caseService.saveCaseAndEmitCaseUpdatedEvent(caze, null);

    eventLogger.logCaseEvent(
        caze,
        responseManagementEvent.getEvent().getDateTime(),
        "Non Compliance",
        EventType.SELECTED_FOR_NON_COMPLIANCE,
        responseManagementEvent.getEvent(),
        responseManagementEvent.getPayload().getCollectionCase(),
        messageTimestamp);
  }

  private CaseMetadata getCaseMetaData(Case caze) {
    if (caze.getMetadata() == null) {
      return new CaseMetadata();
    }

    return caze.getMetadata();
  }
}
