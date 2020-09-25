package uk.gov.ons.census.casesvc.utility;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.RefusalType;

public class FieldworkHelperTest {

  @Test
  public void testDoNotSendBackToField() {
    assertThat(FieldworkHelper.shouldSendCaseToField(null, "FIELD")).isFalse();
  }

  @Test
  public void testDoNotSendToNorthernIrelandCommunalEstablishments() {
    Case caze = new Case();
    caze.setCaseType("CE");
    caze.setRegion("N");
    assertThat(FieldworkHelper.shouldSendCaseToField(caze, "NOT_FIELD")).isFalse();
  }

  @Test
  public void testDoNotSendToTransientPersons() {
    Case caze = new Case();
    caze.setCaseType("HH");
    caze.setRegion("E");
    caze.setEstabType("TRANSIENT PERSONS");
    assertThat(FieldworkHelper.shouldSendCaseToField(caze, "NOT_FIELD")).isFalse();
  }

  @Test
  public void testDoNotSendToRefusedCase() {
    Case caze = new Case();
    caze.setCaseType("HH");
    caze.setRegion("E");
    caze.setEstabType("HOUSEHOLD");
    caze.setRefusalReceived(RefusalType.HARD_REFUSAL);
    assertThat(FieldworkHelper.shouldSendCaseToField(caze, "NOT_FIELD")).isFalse();
  }
}
