package io.coredge.health.claims.db.entity;


import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import org.hibernate.envers.Audited;

@MappedSuperclass
@Audited
@Data
public abstract class AbstractBaseEntity {

}
