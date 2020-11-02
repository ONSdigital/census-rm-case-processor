package uk.gov.ons.census.casesvc.utility;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacCreatedDTO;

public class RedactHelperTest {
  @Test
  public void testRedactWorks() {
    // GIVEN
    ResponseManagementEvent rme = new ResponseManagementEvent();
    PayloadDTO payload = new PayloadDTO();
    UacCreatedDTO uacCreated = new UacCreatedDTO();
    uacCreated.setUac("this value needs to be redacted");
    payload.setUacQidCreated(uacCreated);
    rme.setPayload(payload);

    // WHEN
    // Cast is required for the test, but when we use this we only want Object anyway
    ResponseManagementEvent rmeDeepCopy = (ResponseManagementEvent) RedactHelper.redact(rme);

    // THEN
    assertThat(rmeDeepCopy.getPayload().getUacQidCreated().getUac()).isEqualTo("REDACTED");

    // Extra check to make sure the original object wasn't accidentally mutated
    assertThat(rme.getPayload().getUacQidCreated().getUac())
        .isEqualTo("this value needs to be redacted");
  }
}
