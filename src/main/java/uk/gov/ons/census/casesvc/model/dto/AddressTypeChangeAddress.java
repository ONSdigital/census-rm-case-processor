package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Optional;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL) // Only needed for marshalling (i.e. sending)
public class AddressTypeChangeAddress {
  private Optional<String> addressLine1;
  private Optional<String> addressLine2;
  private Optional<String> addressLine3;
  private Optional<String> estabType;
  private Optional<String> organisationName;
  private String addressType;
}
