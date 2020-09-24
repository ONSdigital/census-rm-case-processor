package uk.gov.ons.census.casesvc.utility;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import uk.gov.ons.census.casesvc.exception.ValidationException;
import uk.gov.ons.census.casesvc.model.dto.ModifiedAddress;

import java.util.Set;

@Component
public class AddressModificationValidator {

  private final Set<String> estabTypes;

  public AddressModificationValidator(@Value("${estabtypes}") Set<String> estabTypes) {
    this.estabTypes = estabTypes;
  }

  public void validate(ModifiedAddress modifiedAddress) {
    if (modifiedAddress.getAddressLine1() != null && !modifiedAddress.getAddressLine1().isPresent()) {
      throw new ValidationException("Mandatory address line 1 cannot be set to null");
    }

    if (modifiedAddress.getAddressLine1() != null
        && modifiedAddress.getAddressLine1().isPresent()
        && StringUtils.isEmpty(modifiedAddress.getAddressLine1().get().strip())) {
      throw new ValidationException("Mandatory address line 1 is empty");
    }

    if (modifiedAddress.getEstabType() != null && !modifiedAddress.getEstabType().isPresent()) {
      throw new ValidationException("Mandatory estab type cannot be set to null");
    }

    if (modifiedAddress.getEstabType() != null
        && modifiedAddress.getEstabType().isPresent()
        && !estabTypes.contains(modifiedAddress.getEstabType().get())) {
      throw new ValidationException("Estab Type not valid");
    }
  }
}
