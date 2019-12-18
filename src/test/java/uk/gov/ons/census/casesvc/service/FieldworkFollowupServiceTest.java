package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.casesvc.model.dto.FieldWorkFollowup;
import uk.gov.ons.census.casesvc.model.entity.Case;

import java.lang.reflect.Field;

@RunWith(MockitoJUnitRunner.class)
public class FieldworkFollowupServiceTest {
  private static final String TEST_EXCHANGE = "TEST_EXCHANGE";
  private static final String TEST_BINDING = "TEST_BINDING";

  @Mock RabbitTemplate rabbitTemplate;

  @InjectMocks FieldworkFollowupService underTest;

  @Test
  public void testBuildAndSendFieldworkFollowup() {
    // Given
    Case expectedCase = getRandomCase();

    ReflectionTestUtils.setField(underTest, "actionFieldBinding", TEST_BINDING);
    ReflectionTestUtils.setField(underTest, "outboundExchange", TEST_EXCHANGE);

    // When
    underTest.buildAndSendFieldWorkFollowUp(expectedCase, "CENSUS", false);

    // Then
    ArgumentCaptor<FieldWorkFollowup> fieldWorkFollowupArgumentCaptor =
        ArgumentCaptor.forClass(FieldWorkFollowup.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(TEST_EXCHANGE), eq(TEST_BINDING), fieldWorkFollowupArgumentCaptor.capture());

    FieldWorkFollowup actualFieldWorkFollowup = fieldWorkFollowupArgumentCaptor.getValue();

    FieldWorkFollowup expectedFieldworkFollowup =
        getExpectedFieldworkFollowup(expectedCase, "CENSUS", false);

    assertThat(actualFieldWorkFollowup).isEqualTo(expectedFieldworkFollowup);
  }

  private FieldWorkFollowup getExpectedFieldworkFollowup(
      Case caze, String surveyName, boolean blankQuestionnaireReturned) {

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

    followup.setSurveyName(surveyName);
    followup.setBlankQreReturned(blankQuestionnaireReturned);

    return followup;
  }
}
