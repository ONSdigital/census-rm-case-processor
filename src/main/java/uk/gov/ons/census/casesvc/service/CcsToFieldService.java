package uk.gov.ons.census.casesvc.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.CcsToFwmt;
import uk.gov.ons.census.casesvc.model.entity.Case;

@Component
public class CcsToFieldService {
  private final RabbitTemplate rabbitTemplate;

  @Value("${queueconfig.outbound-field-exchange}")
  private String outboundExchange;

  @Value("${queueconfig.field-binding")
  private String fieldBinding;

  public CcsToFieldService(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  public void convertAndSendCCSToField(Case caze) {
    CcsToFwmt ccsFwmt = buildCcsToField(caze);
    rabbitTemplate.convertAndSend(outboundExchange, fieldBinding, ccsFwmt);
  }

  private CcsToFwmt buildCcsToField(Case caze) {
    CcsToFwmt ccsToField = new CcsToFwmt();
    ccsToField.setActionPlan(caze.getActionPlanId());
    ccsToField.setActionType("dummy");
    ccsToField.setAddressLine1(caze.getAddressLine1());
    ccsToField.setAddressLine2(caze.getAddressLine2());
    ccsToField.setAddressLine3(caze.getAddressLine3());
    ccsToField.setTownName(caze.getTownName());
    ccsToField.setPostcode(caze.getPostcode());
    ccsToField.setEstabType(caze.getEstabType());
    ccsToField.setLatitude(caze.getLatitude());
    ccsToField.setLongitude(caze.getLongitude());
    ccsToField.setCaseId(caze.getCaseId().toString());
    ccsToField.setCaseRef(Integer.toString(caze.getCaseRef()));
    ccsToField.setAddressType(caze.getAddressType());
    ccsToField.setFieldCoordinatorId(caze.getFieldCoordinatorId());
    ccsToField.setUndeliveredAsAddress(false);
    ccsToField.setSurveyName("CCS");
    ccsToField.setBlankQreReturned(false);

    return ccsToField;
  }
}
