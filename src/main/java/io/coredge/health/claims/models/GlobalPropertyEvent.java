package io.coredge.health.claims.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GlobalPropertyEvent {

    private String key;
    private String value;
    private String action; // UPSERT or DELETE
}