package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.casesvc.model.dto.CcsToFwmt;
import uk.gov.ons.census.casesvc.model.entity.Case;

@RunWith(MockitoJUnitRunner.class)
public class CcsToFieldServiceTest {

  @Mock RabbitTemplate rabbitTemplate;

  @InjectMocks CcsToFieldService underTest;

  @Value("${queueconfig.outbound-field-exchange}")
  private String outboundExchange;

  @Test
  public void testCcsToFwmt() {
    // Given
    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);

    ReflectionTestUtils.setField(underTest, "fieldBinding", "Action.Field.binding");

    // When
    underTest.convertAndSendCCSToField(caze);

    // Then
    ArgumentCaptor<CcsToFwmt> ccsToFwmtArgumentCaptor = ArgumentCaptor.forClass(CcsToFwmt.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(outboundExchange), eq("Action.Field.binding"), ccsToFwmtArgumentCaptor.capture());

    CcsToFwmt actualCcsFwmtCase = ccsToFwmtArgumentCaptor.getValue();
    CcsToFwmt expectedCcsFwmtCase = getExpectedCcsFwmt(caze);

    assertThat(actualCcsFwmtCase).isEqualTo(expectedCcsFwmtCase);
  }

  private CcsToFwmt getExpectedCcsFwmt(Case caze) {
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
    ccsToField.setSurveyName(caze.getSurvey());
    ccsToField.setBlankQreReturned(false);
    ccsToField.setHandDelivery(caze.isHandDelivery());

    return ccsToField;
  }
}
