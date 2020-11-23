package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class RefusalDTO {
  private RefusalTypeDTO type;
  private String agentId;
  private String callId;
  private boolean isHouseholder;

  //  need to define getter here or else isHouseholder flag will default to householder in event
  // JSON and it wont work
  public boolean getIsHouseholder() {
    return this.isHouseholder;
  }

  private CollectionCase collectionCase;
  private Contact contact;
  private Address address;
}
