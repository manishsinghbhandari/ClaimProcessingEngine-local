package io.coredge.health.claims.db.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "countyPopulation")
public class CountyPopulation {
    @Id
    private String id;
    private String county;
    private Long population;
    private String month;
}

