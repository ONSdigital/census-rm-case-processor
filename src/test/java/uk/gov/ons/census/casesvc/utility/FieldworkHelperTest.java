package uk.gov.ons.census.casesvc.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getCaseThatWillPassFieldWorkHelper;

import org.junit.Test;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.RefusalType;

public class FieldworkHelperTest {

  @Test
  public void testCaseCanGoToField() {
    Case caze = getCaseThatWillPassFieldWorkHelper();
    assertThat(FieldworkHelper.shouldSendCaseToField(caze)).isTrue();
  }

  @Test
  public void doNotSendIfEstabTypeTransientPersons() {
    Case caze = getCaseThatWillPassFieldWorkHelper();
    caze.setEstabType("TRANSIENT PERSONS");
    assertThat(FieldworkHelper.shouldSendCaseToField(caze)).isFalse();
  }

  @Test
  public void doNotSendIfEstabTypeIsEmpty() {
    Case caze = getCaseThatWillPassFieldWorkHelper();
    caze.setEstabType("");
    assertThat(FieldworkHelper.shouldSendCaseToField(caze)).isFalse();
  }

  @Test
  public void doNotSendToNorthernIrelandCommunalEstablishments() {
    Case caze = getCaseThatWillPassFieldWorkHelper();
    caze.setCaseType("CE");
    caze.setRegion("N");
    assertThat(FieldworkHelper.shouldSendCaseToField(caze)).isFalse();
  }

  @Test
  public void doNotSendInvalidCaseToField() {
    Case caze = getCaseThatWillPassFieldWorkHelper();
    caze.setAddressInvalid(true);
    assertThat(FieldworkHelper.shouldSendCaseToField(caze)).isFalse();
  }

  @Test
  public void doNotSendCazeMarkedExtraRefused() {
    Case caze = getCaseThatWillPassFieldWorkHelper();
    caze.setRefusalReceived(RefusalType.EXTRAORDINARY_REFUSAL);
    assertThat(FieldworkHelper.shouldSendCaseToField(caze)).isFalse();
  }

  @Test
  public void doNotSendToHArdRefusedCase() {
    Case caze = getCaseThatWillPassFieldWorkHelper();
    caze.setRefusalReceived(RefusalType.HARD_REFUSAL);
    assertThat(FieldworkHelper.shouldSendCaseToField(caze)).isFalse();
  }

  @Test
  public void doNotSendHICase() {
    Case caze = getCaseThatWillPassFieldWorkHelper();
    caze.setCaseType("HI");
    assertThat(FieldworkHelper.shouldSendCaseToField(caze)).isFalse();
  }

  @Test
  public void doNotSendWhereWhereFieldOfficerIdIsEmptyAndCazeTypeIsCE() {
    Case caze = getCaseThatWillPassFieldWorkHelper();
    caze.setFieldOfficerId("");
    caze.setCaseType("CE");
    assertThat(FieldworkHelper.shouldSendCaseToField(caze)).isFalse();
  }

  @Test
  public void doNotSendWhereWhereFieldOfficerIdIsEmptyAndCazeTypeIsSPG() {
    Case caze = getCaseThatWillPassFieldWorkHelper();
    caze.setFieldOfficerId("");
    caze.setCaseType("SPG");
    assertThat(FieldworkHelper.shouldSendCaseToField(caze)).isFalse();
  }

  @Test
  public void doNotSendIfFieldCoordinatorIdIsEmpty() {
    Case caze = getCaseThatWillPassFieldWorkHelper();
    caze.setFieldCoordinatorId("");
    assertThat(FieldworkHelper.shouldSendCaseToField(caze)).isFalse();
  }

  @Test
  public void doNotSendIfLatisEmpty() {
    Case caze = getCaseThatWillPassFieldWorkHelper();
    caze.setLatitude("");
    assertThat(FieldworkHelper.shouldSendCaseToField(caze)).isFalse();
  }

  @Test
  public void doNotSendIfLongisEmpty() {
    Case caze = getCaseThatWillPassFieldWorkHelper();
    caze.setLongitude("");
    assertThat(FieldworkHelper.shouldSendCaseToField(caze)).isFalse();
  }

  @Test
  public void doNotSendIfEstabUprnIsEmptyAndCaseTypeIsCE() {
    Case caze = getCaseThatWillPassFieldWorkHelper();
    caze.setEstabUprn("");
    caze.setCaseType("CE");
    assertThat(FieldworkHelper.shouldSendCaseToField(caze)).isFalse();
  }

  //  Do Not send if Case is marked as receipted AND case is not a CE E
  @Test
  public void sendIfReceiptedAndCaseNotACE_E() {
    Case caze = getCaseThatWillPassFieldWorkHelper();
    caze.setReceiptReceived(true);
    caze.setCaseType("CE");
    caze.setAddressLevel("E");
    assertThat(FieldworkHelper.shouldSendCaseToField(caze)).isTrue();
  }

  //  Do Not send if Case is marked as receipted AND case is not a CE E
  @Test
  public void doNotSendIfReceiptedAndCaseNotACE_E() {
    Case caze = getCaseThatWillPassFieldWorkHelper();
    caze.setReceiptReceived(true);
    caze.setCaseType("CE");
    caze.setAddressLevel("U");
    assertThat(FieldworkHelper.shouldSendCaseToField(caze)).isFalse();
  }

  //    Do Not send if  CE E case is marked as receipted AND AR >= ER
  @Test
  public void doNotSendIfCE_E_And_AR_EqualToOrGreaterThan_ER() {
    Case caze = getCaseThatWillPassFieldWorkHelper();
    caze.setReceiptReceived(true);
    caze.setCaseType("CE");
    caze.setAddressLevel("E");
    caze.setCeExpectedCapacity(5);
    caze.setCeActualResponses(5);
    assertThat(FieldworkHelper.shouldSendCaseToField(caze)).isFalse();
  }

  //    Do Not send if  CE E case is marked as receipted AND AR >= ER
  @Test
  public void dotSendIfCE_E_And_ARLessThan_ER() {
    Case caze = getCaseThatWillPassFieldWorkHelper();
    caze.setReceiptReceived(true);
    caze.setCaseType("CE");
    caze.setAddressLevel("E");
    caze.setCeExpectedCapacity(5);
    caze.setCeActualResponses(4);
    assertThat(FieldworkHelper.shouldSendCaseToField(caze)).isTrue();
  }
}
