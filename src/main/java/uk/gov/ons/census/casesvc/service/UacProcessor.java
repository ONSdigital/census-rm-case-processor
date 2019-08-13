package uk.gov.ons.census.casesvc.service;

import java.util.Optional;
import java.util.UUID;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.client.UacQidServiceClient;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacDTO;
import uk.gov.ons.census.casesvc.model.dto.UacQidDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.utility.EventHelper;
import uk.gov.ons.census.casesvc.utility.Sha256Helper;

@Component
public class UacProcessor {
  private static final Logger log = LoggerFactory.getLogger(UacProcessor.class);
  private static final String UAC_UPDATE_ROUTING_KEY = "event.uac.update";

  private final UacQidLinkRepository uacQidLinkRepository;
  private final RabbitTemplate rabbitTemplate;
  private final UacQidServiceClient uacQidServiceClient;
  private final CaseRepository caseRepository;

  @Value("${queueconfig.case-event-exchange}")
  private String outboundExchange;

  public UacProcessor(
      UacQidLinkRepository uacQidLinkRepository,
      RabbitTemplate rabbitTemplate,
      UacQidServiceClient uacQidServiceClient,
      CaseRepository caseRepository) {
    this.rabbitTemplate = rabbitTemplate;
    this.uacQidServiceClient = uacQidServiceClient;
    this.uacQidLinkRepository = uacQidLinkRepository;
    this.caseRepository = caseRepository;
  }

  public UacQidLink generateAndSaveUacQidLink(Case caze, int questionnaireType) {
    return generateAndSaveUacQidLink(caze, questionnaireType, null);
  }

  public UacQidLink generateAndSaveUacQidLink(Case caze, int questionnaireType, UUID batchId) {
    UacQidDTO uacQid = uacQidServiceClient.generateUacQid(questionnaireType);
    return createAndSaveUacQidLink(caze, batchId, uacQid.getUac(), uacQid.getQid());
  }

  private UacQidLink createAndSaveUacQidLink(
      Case linkedCase, UUID batchId, String uac, String qid) {
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setUac(uac);
    uacQidLink.setCaze(linkedCase);
    uacQidLink.setBatchId(batchId);
    uacQidLink.setActive(true);
    uacQidLink.setQid(qid);

    uacQidLinkRepository.save(uacQidLink);
    return uacQidLink;
  }

  public PayloadDTO emitUacUpdatedEvent(UacQidLink uacQidLink, Case caze) {
    return emitUacUpdatedEvent(uacQidLink, caze, true);
  }

  public PayloadDTO emitUacUpdatedEvent(UacQidLink uacQidLink, Case caze, boolean active) {
    EventDTO eventDTO = EventHelper.createEventDTO(EventType.UAC_UPDATED);

    UacDTO uac = new UacDTO();
    uac.setQuestionnaireId(uacQidLink.getQid());
    uac.setUacHash(Sha256Helper.hash(uacQidLink.getUac()));
    uac.setUac(uacQidLink.getUac());
    uac.setActive(active);

    if (caze != null) {
      uac.setCaseId(caze.getCaseId().toString());
      uac.setCaseType(caze.getAddressType());
      uac.setCollectionExerciseId(caze.getCollectionExerciseId());
      uac.setRegion(caze.getRegion());
    }

    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setUac(uac);
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    responseManagementEvent.setEvent(eventDTO);
    responseManagementEvent.setPayload(payloadDTO);

    rabbitTemplate.convertAndSend(
        outboundExchange, UAC_UPDATE_ROUTING_KEY, responseManagementEvent);

    return payloadDTO;
  }

  public void ingestUacCreatedEvent(ResponseManagementEvent uacCreatedEvent) {
    Optional<Case> linkedCase =
        caseRepository.findByCaseId(uacCreatedEvent.getPayload().getUacQidCreated().getCaseId());
    if (linkedCase.isEmpty()) {
      log.with("caseId", uacCreatedEvent.getPayload().getUacQidCreated().getCaseId())
          .with("transactionId", uacCreatedEvent.getEvent().getTransactionId())
          .error("Cannot find case for UAC created event");
      throw new RuntimeException("No case found matching UAC created event");
    }
    UacQidLink uacQidLink =
        createAndSaveUacQidLink(
            linkedCase.get(),
            null,
            uacCreatedEvent.getPayload().getUacQidCreated().getUac(),
            uacCreatedEvent.getPayload().getUacQidCreated().getQid());

    emitUacUpdatedEvent(uacQidLink, linkedCase.get());
  }
}
