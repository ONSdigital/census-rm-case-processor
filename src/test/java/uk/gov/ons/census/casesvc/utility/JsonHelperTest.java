package uk.gov.ons.census.casesvc.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.util.Optional;
import org.junit.Test;
import uk.gov.ons.census.casesvc.model.dto.ModifiedAddress;

public class JsonHelperTest {

  @Test
  public void testMissingJsonFieldsAreExcludedWhenMarshalling() {
    ModifiedAddress modifiedAddress = new ModifiedAddress();
    assertThat(convertObjectToJson(modifiedAddress)).isEqualTo("{}");
  }

  @Test
  public void testPresentButEmptyJsonFieldsMarshalToNulls() {
    ModifiedAddress modifiedAddress = new ModifiedAddress();
    modifiedAddress.setAddressLine2(Optional.empty());
    assertThat(convertObjectToJson(modifiedAddress)).isEqualTo("{\"addressLine2\":null}");
  }
}
