package io.coredge.health.claims.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@AllArgsConstructor
public class ClaimStatusUpdateResult {
    private List<String> success;
    private List<String> failed;
}