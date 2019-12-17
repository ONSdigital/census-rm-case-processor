package uk.gov.ons.census.casesvc.model.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import java.time.OffsetDateTime;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;

@Data
@TypeDefs({@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)})
@Entity
public class Event {
  @Id private UUID id;

  @ToString.Exclude
  @ManyToOne
  private UacQidLink uacQidLink;

  @ToString.Exclude
  @ManyToOne
  private Case caze;

  @Column(columnDefinition = "timestamp with time zone")
  private OffsetDateTime eventDate;

  @Column private String eventDescription;

  @Column(columnDefinition = "timestamp with time zone")
  private OffsetDateTime rmEventProcessed;

  @Column
  @Enumerated(EnumType.STRING)
  private EventType eventType;

  @Column private String eventChannel;
  @Column private String eventSource;
  @Column private UUID eventTransactionId;

  @Type(type = "jsonb")
  @Column(columnDefinition = "jsonb")
  private String eventPayload;
}
