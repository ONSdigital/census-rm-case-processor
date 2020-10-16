package uk.gov.ons.census.casesvc.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.model.dto.ModifiedAddress;

@RunWith(MockitoJUnitRunner.class)
public class AddressModificationValidatorTest {

  @Spy private static Set<String> estabTypes;

  static {
    estabTypes = new HashSet<>();
    estabTypes.add("HOSPITAL");
    estabTypes.add("HOUSEHOLD");
  }

  @InjectMocks private AddressModificationValidator underTest;

  @Test
  public void testValidateWithAllFieldsPresentValid() {
    // Given
    ModifiedAddress modifiedAddress = new ModifiedAddress();
    modifiedAddress.setAddressLine1(Optional.of("Flat 1"));
    modifiedAddress.setAddressLine2(Optional.of("27 Foo street"));
    modifiedAddress.setAddressLine3(Optional.of("Park Drive"));
    modifiedAddress.setEstabType(Optional.of("HOUSEHOLD"));
    modifiedAddress.setOrganisationName(Optional.of("Quaint B&B"));
    modifiedAddress.setAddressType("HH");

    // When, then no exception
    underTest.validate(modifiedAddress, "RH");
  }

  @Test
  public void testValidateWithMinimumFieldsPresentValid() {
    // Given
    ModifiedAddress modifiedAddress = new ModifiedAddress();
    modifiedAddress.setAddressType("HH");

    // When, then no exception
    underTest.validate(modifiedAddress, "RH");
  }

  @Test
  public void testValidateWithDeletedFieldsValid() {
    // Given
    ModifiedAddress modifiedAddress = new ModifiedAddress();
    modifiedAddress.setAddressLine2(Optional.empty());
    modifiedAddress.setAddressLine3(Optional.empty());
    modifiedAddress.setOrganisationName(Optional.empty());

    // When, then no exception
    underTest.validate(modifiedAddress, "RH");
  }

  @Test
  public void testValidateAddressLine1Null() {
    // Given
    ModifiedAddress modifiedAddress = new ModifiedAddress();

    // empty optional is what a null json field unmarshalls to
    modifiedAddress.setAddressLine1(Optional.empty());

    // When, then
    checkValidationFailureThrown(modifiedAddress);
  }

  @Test
  public void testValidateAddressLine1Empty() {
    // Given
    ModifiedAddress modifiedAddress = new ModifiedAddress();
    modifiedAddress.setAddressLine1(Optional.of(" "));

    // When, then
    checkValidationFailureThrown(modifiedAddress);
  }

  @Test
  public void testValidateEstabTypeInvalid() {
    // Given
    ModifiedAddress modifiedAddress = new ModifiedAddress();
    modifiedAddress.setEstabType(Optional.of("SPACE STATION"));

    // When, then
    checkValidationFailureThrown(modifiedAddress);
  }

  @Test
  public void testProcessMessageEstabTypeNull() {
    // Given
    ModifiedAddress modifiedAddress = new ModifiedAddress();
    modifiedAddress.setEstabType(Optional.empty());

    // When, then
    checkValidationFailureThrown(modifiedAddress);
  }

  // ****************** HERE BE DRAGONS!!!! *******************
  // See comment elsewhere deliberately repeated here, because of kludginess spreading through
  // codebase.
  //
  // Because of reasons of almost unimaginable awfulness, we are forced to accept this
  // late-breaking kludge, which allows Contact Centre to use "OTHER" as the estab type
  // instead of one of the vast number of specific ones which are allowed.
  //
  // See this card for more details: https://trello.com/c/ZUuPMYuQ
  //
  // This test proves that we allow OTHER estab type from CC without throwing an exception, and it
  // exists purely to document this highly undesirable behaviour, although functionally it does
  // nothing, and a programmer without this knowledge would  no doubt optimise it away.
  //
  // Sorry.
  @Test
  public void testOtherEstabTypeFromContactCentreIsAllowed() {
    // Given
    ModifiedAddress modifiedAddress = new ModifiedAddress();
    modifiedAddress.setAddressType("HH");
    modifiedAddress.setEstabType(Optional.of("OTHER"));

    // When, then no exception
    underTest.validate(modifiedAddress, "CC");
  }

  // See "here be dragons" comments elsewhere. Sorry.
  @Test
  public void testOtherEstabTypeNotFromContactCentreIsNotAllowed() {
    // Given
    ModifiedAddress modifiedAddress = new ModifiedAddress();
    modifiedAddress.setAddressType("HH");
    modifiedAddress.setEstabType(Optional.of("OTHER"));

    // When, then exception
    checkValidationFailureThrown(modifiedAddress);
  }

  private void checkValidationFailureThrown(ModifiedAddress modifiedAddress) {
    try {
      underTest.validate(modifiedAddress, "RH");
      fail("Expected validation failure RuntimeException not thrown");
    } catch (RuntimeException e) {
      assertThat(e.getMessage()).startsWith("Validation Failure: ");
    }
  }
}
