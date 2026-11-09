package io.coredge.health.claims.db.entity.BMS.common;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.BMS.Status;
import io.coredge.health.claims.db.entity.permission.User;
import jakarta.persistence.*;
import lombok.Builder;
import java.util.Date;
import org.apache.commons.lang3.StringUtils;

@MappedSuperclass
public abstract class AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private Status status;

   // @ManyToOne(fetch = FetchType.LAZY, optional = false)
    //@JoinColumn(name = "created_by", nullable = false)
    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

   // @ManyToOne(fetch = FetchType.LAZY)
    //@JoinColumn(name = "updated_by")
    @Column(name = "updated_by")
    private Long updatedBy;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at")
    private Date updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = new Date();
        this.updatedAt = new Date();

       // if (StringUtils.isNotBlank(HealthIdContextHolder.userId())) {
            this.createdBy = 2L;
            this.updatedBy = 2L;
            Status ActiveStatus = new Status();
            ActiveStatus.setId(Constants.Statuses.ACTIVE_ID);
            this.status = ActiveStatus;
        //}

    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = new Date();

        // if (StringUtils.isNotBlank(HealthIdContextHolder.userId())) {
            //this.updatedBy = HealthIdContextHolder.userId();
            this.updatedBy = 2L;
        //}
    }
}
