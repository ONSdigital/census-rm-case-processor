package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class Receipt {
    private long caseRef;
    private String caseId;
    private String inboundChannel;
    private LocalDateTime responseDateTime;
    private String partyId;
}
