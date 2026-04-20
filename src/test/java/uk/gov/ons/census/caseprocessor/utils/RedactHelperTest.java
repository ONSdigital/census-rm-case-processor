package uk.gov.ons.census.caseprocessor.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.NewCase;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.model.dto.SmsRequest;

public class RedactHelperTest {
  @Test
  public void testRedactWorksForMap() {
    // GIVEN
    NewCase newCase = new NewCase();

    newCase.setSampleSensitive(Map.of("PHONE_NUMBER", "999999"));

    // WHEN
    // Cast is required for the test, but when we use this we only want Object anyway
    NewCase newCaseDeepCopy = (NewCase) RedactHelper.redact(newCase);

    // THEN
    assertThat(newCaseDeepCopy.getSampleSensitive()).isEqualTo(Map.of("PHONE_NUMBER", "REDACTED"));

    // Extra check to make sure the original object wasn't accidentally mutated
    assertThat(newCase.getSampleSensitive()).isEqualTo(Map.of("PHONE_NUMBER", "999999"));
  }

  @Test
  public void testRedactWorksForString() {
    // GIVEN

    SmsRequest smsRequest = new SmsRequest();

    smsRequest.setPhoneNumber("SUPER SECRET VALUE");

    PayloadDTO payloadDto = new PayloadDTO();
    payloadDto.setSmsRequest(smsRequest);

    EventDTO eventDto = new EventDTO();
    eventDto.setPayload(payloadDto);

    // WHEN
    // Cast is required for the test, but when we use this we only want Object anyway
    EventDTO eventDeepCopy = (EventDTO) RedactHelper.redact(eventDto);

    // THEN
    assertThat(eventDeepCopy.getPayload().getSmsRequest().getPhoneNumber()).isEqualTo("REDACTED");

    // Extra check to make sure the original object wasn't accidentally mutated
    assertThat(eventDto.getPayload().getSmsRequest().getPhoneNumber())
        .isEqualTo("SUPER SECRET VALUE");
  }
}
