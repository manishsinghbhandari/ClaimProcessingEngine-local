package io.coredge.health.claims.models.AI;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.coredge.health.claims.utils.AI.ScoreDetailDeserializer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@JsonDeserialize(using = ScoreDetailDeserializer.class)
public class ScoreDetail {
    private String score;
    private List<String> remarks;

}
