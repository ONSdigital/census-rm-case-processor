package uk.gov.ons.census.casesvc.utility;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.RefusalType;

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

  @Test
  public void testUnInvalidateAddressDoNotSendBackToField() {
    assertThat(FieldworkHelper.shouldSendUnInvalidatedAddressCaseToField(null, "FIELD")).isFalse();
  }

  @Test
  public void testUnInvalidateAddressDoNotSendToNorthernIrelandCommunalEstablishments() {
    Case caze = new Case();
    caze.setCaseType("CE");
    caze.setRegion("N");
    assertThat(FieldworkHelper.shouldSendUnInvalidatedAddressCaseToField(caze, "NOT_FIELD"))
        .isFalse();
  }

  @Test
  public void testUnInvalidateAddressDoNotSendToRefusedCase() {
    Case caze = new Case();
    caze.setCaseType("HH");
    caze.setRegion("E");
    caze.setRefusalReceived(RefusalType.HARD_REFUSAL);
    assertThat(FieldworkHelper.shouldSendUnInvalidatedAddressCaseToField(caze, "NOT_FIELD"))
        .isFalse();
  }

  @Test
  public void testUnInvalidateAddressDoNotSendToTransientPersons() {
    Case caze = new Case();
    caze.setCaseType("HH");
    caze.setRegion("E");
    caze.setEstabType("TRANSIENT PERSONS");
    assertThat(FieldworkHelper.shouldSendUpdatedCaseToField(caze, "NOT_FIELD")).isFalse();
  }
}
