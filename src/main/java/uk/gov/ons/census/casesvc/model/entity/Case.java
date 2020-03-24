package uk.gov.ons.census.casesvc.model.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.*;

@Data
@Entity
@TypeDefs({@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)})
@Table(
    name = "cases",
    indexes = {
        @Index(name = "cases_case_ref_idx", columnList = "case_ref"),
        @Index(name = "lsoa_idx", columnList = "lsoa")
    })
public class Case {

  @Id private UUID caseId;

  // This incrementing column allows us to generate a pseudorandom unique (non-colliding) caseRef
  @Column(columnDefinition = "serial")
  @Generated(GenerationTime.INSERT)
  private int secretSequenceNumber;

  @Column(name = "case_ref")
  private Long caseRef;

  @Column private String arid;

  @Column private String estabArid;

  @Column private String uprn;

  @Column private String caseType;

  @Column private String addressType;

  @Column private String estabType;

  @Column private String addressLevel;

  @Column private String abpCode;

  @Column private String organisationName;

  @Column private String addressLine1;

  @Column private String addressLine2;

  @Column private String addressLine3;

  @Column private String townName;

  @Column private String postcode;

  @Column private String latitude;

  @Column private String longitude;

  @Column private String oa;

  @Column(name = "lsoa")
  private String lsoa;

  @Column private String msoa;

  @Column private String lad;

  @Column private String region;

  @Column private String htcWillingness;

  @Column private String htcDigital;

  @Column private String fieldCoordinatorId;

  @Column private String fieldOfficerId;

  @Column private String treatmentCode;

  @Column private Integer ceExpectedCapacity;

  @Column private Integer ceActualResponses;

  @Column private String collectionExerciseId;

  @Column private String actionPlanId;

  @Column(nullable = false)
  private String survey;

  @Column(columnDefinition = "timestamp with time zone")
  private OffsetDateTime createdDateTime;

  @OneToMany(mappedBy = "caze")
  @ToString.Exclude
  List<UacQidLink> uacQidLinks;

  @ToString.Exclude
  @OneToMany(mappedBy = "caze")
  List<Event> events;

  @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  private boolean receiptReceived;

  @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  private boolean refusalReceived;

  @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  private boolean addressInvalid;

  @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  private boolean undeliveredAsAddressed;

  @Column(columnDefinition = "timestamp with time zone")
  @UpdateTimestamp
  private OffsetDateTime lastUpdated;

  @Column(columnDefinition = "BOOLEAN DEFAULT false")
  private boolean handDelivery;

  @Type(type = "jsonb")
  @Column(columnDefinition = "jsonb")
  private CaseMetadata metadata;
}
