package io.coredge.health.claims.db.entity.utilization;

import java.util.Date;

public interface BenefitsMasterView{
    String getInterventionCode();
    String getCode();
    Date getDateFrom();
    Date getDateTill();
}