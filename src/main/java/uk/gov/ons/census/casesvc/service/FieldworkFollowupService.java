package uk.gov.ons.census.casesvc.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.FieldWorkFollowup;
import uk.gov.ons.census.casesvc.model.entity.Case;

@Component
public class FieldworkFollowupService {

  private final RabbitTemplate rabbitTemplate;

  @Value("${queueconfig.outbound-field-exchange}")
  private String outboundExchange;

  @Value("${queueconfig.field-binding}")
  private String actionFieldBinding;

  public FieldworkFollowupService(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  public void buildAndSendFieldWorkFollowUp(Case caze) {
    FieldWorkFollowup followup = buildFieldworkFollowup(caze);
    rabbitTemplate.convertAndSend(outboundExchange, actionFieldBinding, followup);
  }

  private FieldWorkFollowup buildFieldworkFollowup(Case caze) {

    FieldWorkFollowup followup = new FieldWorkFollowup();
    followup.setAddressLine1(caze.getAddressLine1());
    followup.setAddressLine2(caze.getAddressLine2());
    followup.setAddressLine3(caze.getAddressLine3());
    followup.setTownName(caze.getTownName());
    followup.setPostcode(caze.getPostcode());
    followup.setEstabType(caze.getEstabType());
    followup.setOrganisationName(caze.getOrganisationName());
    followup.setArid(caze.getArid());
    followup.setUprn(caze.getUprn());
    followup.setOa(caze.getOa());
    followup.setArid(caze.getArid());
    followup.setLatitude(caze.getLatitude());
    followup.setLongitude(caze.getLongitude());
    followup.setActionPlan(caze.getActionPlanId());
    followup.setActionType("dummy");
    followup.setCaseId(caze.getCaseId().toString());
    followup.setCaseRef(Integer.toString(caze.getCaseRef()));
    followup.setAddressType(caze.getAddressType());
    followup.setAddressLevel(caze.getAddressLevel());
    followup.setTreatmentCode(caze.getTreatmentCode());
    followup.setFieldOfficerId(caze.getFieldOfficerId());
    followup.setFieldCoordinatorId(caze.getFieldCoordinatorId());
    followup.setCeExpectedCapacity(caze.getCeExpectedCapacity());
    followup.setUndeliveredAsAddress(caze.isUndeliveredAsAddressed());

    // TODO: set surveyName, undeliveredAsAddress and blankQreReturned from caze
    followup.setSurveyName("CENSUS");
    followup.setBlankQreReturned(true);

    // TODO: ccsQuestionnaireUrl, ceDeliveryReqd,
    // ceCE1Complete, ceActualResponses

    return followup;
  }
}
