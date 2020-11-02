package uk.gov.ons.census.casesvc.utility;

import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import uk.gov.ons.census.casesvc.model.dto.ModifiedAddress;

@Component
public class AddressModificationValidator {

  private final Set<String> estabTypes;

  public AddressModificationValidator(@Value("${estabtypes}") Set<String> estabTypes) {
    this.estabTypes = estabTypes;
  }

  public void validate(ModifiedAddress modifiedAddress, String eventChannel) {
    if (modifiedAddress.getAddressLine1() != null
        && !modifiedAddress.getAddressLine1().isPresent()) {
      throw new RuntimeException(
          "Validation Failure: Mandatory address line 1 cannot be set to null");
    }

    if (modifiedAddress.getAddressLine1() != null
        && modifiedAddress.getAddressLine1().isPresent()
        && StringUtils.isEmpty(modifiedAddress.getAddressLine1().get().strip())) {
      throw new RuntimeException("Validation Failure: Mandatory address line 1 is empty");
    }

    if (modifiedAddress.getEstabType() != null && !modifiedAddress.getEstabType().isPresent()) {
      throw new RuntimeException("Validation Failure: Mandatory estab type cannot be set to null");
    }

    if (modifiedAddress.getEstabType() != null && modifiedAddress.getEstabType().isPresent()) {

      if ("CC".equals(eventChannel) && "OTHER".equals(modifiedAddress.getEstabType().get())) {
        // ****************** HERE BE DRAGONS!!!! *******************
        // Because of reasons of almost unimaginable awfulness, we are forced to accept this
        // late-breaking kludge, which allows Contact Centre to use "OTHER" as the estab type
        // instead of one of the vast number of specific ones which are allowed.
        //
        // See this card for more details: https://trello.com/c/ZUuPMYuQ
        //
        // This clause allows OTHER estab type from CC without throwing an exception, and it
        // exists purely to document this highly undesirable behaviour, although functionally
        // it does nothing, and a programmer without this knowledge would no doubt optimise it away.
        //
        // Sorry.
      } else if (!estabTypes.contains(modifiedAddress.getEstabType().get())) {
        throw new RuntimeException("Validation Failure: Estab Type not valid");
      }
    }
  }
}
