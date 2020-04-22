package uk.gov.ons.census.casesvc.model.entity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

// The bidirectional relationships with other entities can cause stack overflows with the default
// toString
@ToString(onlyExplicitlyIncluded = true)
@Data
@Entity
@Table(indexes = {@Index(name = "qid_idx", columnList = "qid")})
public class UacQidLink {
  @Id private UUID id;

  @Column(name = "qid")
  private String qid;

  @Column private String uac;

  @ManyToOne private Case caze;

  @OneToMany(mappedBy = "uacQidLink")
  private List<Event> events;

  @Column private UUID batchId;

  @Column private boolean active;

  @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  private boolean ccsCase;

  @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  private boolean blankQuestionnaire;

  @Column(columnDefinition = "timestamp with time zone")
  @CreationTimestamp
  private OffsetDateTime createdDateTime;

  @Column(columnDefinition = "timestamp with time zone")
  @UpdateTimestamp
  private OffsetDateTime lastUpdated;
}
