package uk.gov.ons.census.casesvc.service;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.client.UacQidServiceClient;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CCSPropertyDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@Service
public class CCSPropertyListedService {
  private static final Logger log = LoggerFactory.getLogger(CCSPropertyListedService.class);
  private static final String UAC_UPDATE_ROUTING_KEY = "event.uac.update";
  private static final String QID_NOT_FOUND_ERROR = "Qid not found error";

  private final UacQidLinkRepository uacQidLinkRepository;
  private final RabbitTemplate rabbitTemplate;
  private final UacQidServiceClient uacQidServiceClient;
  private final EventLogger eventLogger;
  private final CaseService caseService;

  @Value("${ccsconfig.action-plan-id}")
  private String actionPlanId;

  @Value("${ccsconfig.collection-exercise-id}")
  private String collectionExerciseId;

  public CCSPropertyListedService(
      UacQidLinkRepository uacQidLinkRepository,
      RabbitTemplate rabbitTemplate,
      UacQidServiceClient uacQidServiceClient,
      EventLogger eventLogger,
      CaseService caseService) {
    this.rabbitTemplate = rabbitTemplate;
    this.uacQidServiceClient = uacQidServiceClient;
    this.uacQidLinkRepository = uacQidLinkRepository;
    this.eventLogger = eventLogger;
    this.caseService = caseService;
  }

  public void processCCSPropertyListed(ResponseManagementEvent ccsPropertyListedEvent) {
    CCSPropertyDTO ccsProperty = ccsPropertyListedEvent.getPayload().getCcsProperty();
    String caseId = ccsProperty.getCollectionCase().getId();

    Case ccsCase =
        caseService.saveCCSCase(
            caseId, ccsProperty.getSampleUnit(), actionPlanId, collectionExerciseId);
    //    UacDTO uac = questionnaireLinkedEvent.getPayload().getUac();
    //    String questionnaireId = uac.getQuestionnaireId();
    //    UacQidLink uacQidLink = uacService.findByQid(questionnaireId);
    //    Case caze;
    //
    //    if (isIndividualQuestionnaireType(questionnaireId)) {
    //      Case householdCase = caseService.getCaseByCaseId(UUID.fromString(uac.getCaseId()));
    //      caze = caseService.prepareIndividualResponseCaseFromParentCase(householdCase);
    //
    //      caseService.saveAndEmitCaseCreatedEvent(caze);
    //    } else {
    //      caze = caseService.getCaseByCaseId(UUID.fromString(uac.getCaseId()));
    //    }
    //
    //    // If UAC/QID has been receipted before case, update case
    //    if (!uacQidLink.isActive() && !caze.isReceiptReceived()) {
    //      caze.setReceiptReceived(true);
    //      caseService.saveAndEmitCaseUpdatedEvent(caze);
    //    }
    //
    //    uacQidLink.setCaze(caze);
    //    uacService.saveAndEmitUacUpdatedEvent(uacQidLink);
    //
    //    eventLogger.logUacQidEvent(
    //        uacQidLink,
    //        questionnaireLinkedEvent.getEvent().getDateTime(),
    //        QUESTIONNAIRE_LINKED,
    //        EventType.QUESTIONNAIRE_LINKED,
    //        questionnaireLinkedEvent.getEvent(),
    //        convertObjectToJson(uac));
  }
}
