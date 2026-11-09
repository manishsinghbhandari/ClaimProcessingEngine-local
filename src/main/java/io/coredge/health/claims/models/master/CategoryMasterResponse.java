package io.coredge.health.claims.models.master;

import lombok.Data;

@Data
public class CategoryMasterResponse {
  private Long id;
  private String code;
  private String categoryName;
}
