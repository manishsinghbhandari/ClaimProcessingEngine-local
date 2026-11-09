package io.coredge.health.claims.rest.bms;

import io.coredge.health.claims.db.entity.BMS.JobGroup;
import io.coredge.health.claims.service.BMS.JobGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static io.coredge.health.claims.constants.Constants.EndPoints.*;

@RestController
@CrossOrigin(origins = "*", maxAge = 3600)
@RequestMapping(BMS_JOB_GROUPS)
@RequiredArgsConstructor
public class JobGroupController {

    private final JobGroupService jobGroupService;

    @GetMapping
    public List<JobGroup> getAllJobGroups() {
        return jobGroupService.findAll();
    }

    @GetMapping(BMS_JOB_GROUPS_ID)
    public ResponseEntity<JobGroup> getJobGroupById(@PathVariable Long id) {
        return jobGroupService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}