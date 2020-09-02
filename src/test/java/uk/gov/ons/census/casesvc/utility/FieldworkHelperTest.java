package uk.gov.ons.census.casesvc.utility;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import uk.gov.ons.census.casesvc.model.entity.Case;

public class FieldworkHelperTest {
  @Test
  public void testDoNotSendBackToField() {
    assertThat(FieldworkHelper.shouldSendUpdatedCaseToField(null, "FIELD")).isFalse();
  }

  @Test
  public void testDoNotSendToNorthernIrelandCommunalEstablishments() {
    Case caze = new Case();
    caze.setCaseType("CE");
    caze.setRegion("N");
    assertThat(FieldworkHelper.shouldSendUpdatedCaseToField(caze, "NOT_FIELD")).isFalse();
  }

  @Test
  public void testDoNotSendToTransientPersons() {
    Case caze = new Case();
    caze.setCaseType("HH");
    caze.setRegion("E");
    caze.setEstabType("TRANSIENT PERSONS");
    assertThat(FieldworkHelper.shouldSendUpdatedCaseToField(caze, "NOT_FIELD")).isFalse();
  }

  @Test
  public void testDoNotSendToMigrantWorkers() {
    Case caze = new Case();
    caze.setCaseType("HH");
    caze.setRegion("E");
    caze.setEstabType("MIGRANT WORKERS");
    assertThat(FieldworkHelper.shouldSendUpdatedCaseToField(caze, "NOT_FIELD")).isFalse();
  }
}
