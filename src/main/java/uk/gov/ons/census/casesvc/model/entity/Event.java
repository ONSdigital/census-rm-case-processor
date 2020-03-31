package uk.gov.ons.census.casesvc.model.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import java.time.OffsetDateTime;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;

// The bidirectional relationships with other entities can cause stack overflows with the default
// toString
@ToString(onlyExplicitlyIncluded = true)
@Data
@TypeDefs({@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)})
@Entity
@Table(
    indexes = {
      @Index(name = "event_type_idx", columnList = "event_type"),
      @Index(name = "rm_event_processed_idx", columnList = "rm_event_processed")
    })
public class Event {
  @Id private UUID id;

  @ManyToOne private UacQidLink uacQidLink;

  @ManyToOne private Case caze;

  @Column(columnDefinition = "timestamp with time zone")
  private OffsetDateTime eventDate;

  @Column private String eventDescription;

  @Column(name = "rm_event_processed", columnDefinition = "timestamp with time zone")
  private OffsetDateTime rmEventProcessed;

  @Column(name = "event_type")
  @Enumerated(EnumType.STRING)
  private EventType eventType;

  @Column private String eventChannel;
  @Column private String eventSource;
  @Column private UUID eventTransactionId;

  @Type(type = "jsonb")
  @Column(columnDefinition = "jsonb")
  private String eventPayload;

  @Column(columnDefinition = "Timestamp with time zone")
  private OffsetDateTime messageTimestamp;
}
