package io.coredge.health.claims.enums;

public enum FundType {

    ECCIF("eccif" , 1),
    SHIF("shif" , 0),
    SHA("sha" , 0),
    PMSF("pmsf" , 2);

    public final String name;
    public final Integer order;

    FundType(String name, Integer order){ this.name=name;
        this.order = order;
    }

}
