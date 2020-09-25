package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.Optional;
import lombok.Data;

@Data
@JsonInclude(Include.NON_NULL) // Only needed for marshalling (i.e. sending)
public class ModifiedAddress {
  private Optional<String> addressLine1;
  private Optional<String> addressLine2;
  private Optional<String> addressLine3;
  private Optional<String> estabType;
  private Optional<String> organisationName;
  private String addressType; // Only used for ADDRESS_TYPE_CHANGED
}
