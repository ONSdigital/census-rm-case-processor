package uk.gov.ons.census.casesvc.utility;

import java.util.Set;
import org.springframework.util.StringUtils;
import uk.gov.ons.census.casesvc.model.dto.ModifiedAddress;

public class AddressModificationValidator {

  public static void validateAddressModification(
      Set<String> validEstabTypes, ModifiedAddress address) {
    if (address.getAddressLine1() != null && !address.getAddressLine1().isPresent()) {
      throw new RuntimeException("Mandatory address line 1 cannot be set to null");
    }

    if (address.getAddressLine1() != null
        && address.getAddressLine1().isPresent()
        && StringUtils.isEmpty(address.getAddressLine1().get())) {
      throw new RuntimeException("Mandatory address line 1 is empty");
    }

    if (address.getEstabType() != null && !address.getEstabType().isPresent()) {
      throw new RuntimeException("Mandatory estab type cannot be set to null");
    }

    if (address.getEstabType() != null
        && address.getEstabType().isPresent()
        && !validEstabTypes.contains(address.getEstabType().get())) {
      throw new RuntimeException("Estab Type not valid");
    }
  }
}
