package io.coredge.health.claims.rest.bms;

import io.coredge.health.claims.db.entity.BMS.SchemeMember;
import io.coredge.health.claims.dto.SchemeMemberDTO;
import io.coredge.health.claims.dto.SchemeMemberListDTO;
import io.coredge.health.claims.service.BMS.SchemeMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

import static io.coredge.health.claims.constants.Constants.EndPoints.*;

@RestController
@CrossOrigin(origins = "*", maxAge = 3600)
@RequestMapping(BMS_SCHEME_MEMBER)
@RequiredArgsConstructor
public class SchemeMemberController {

    private final SchemeMemberService schemeMemberService;

    @PostMapping
    public ResponseEntity<SchemeMember> create(@RequestBody SchemeMemberDTO dto) {
        return ResponseEntity.ok(schemeMemberService.create(dto));
    }

    @GetMapping(BMS_SCHEME_MEMBER_ID)
    public ResponseEntity<SchemeMember> getById(@PathVariable Long id) {
        Optional<SchemeMember> member = schemeMemberService.findById(id);
        return member.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<Page<SchemeMemberListDTO>> list(Pageable pageable) {
        return ResponseEntity.ok(schemeMemberService.findAllDTO(pageable));
    }

    @PutMapping(BMS_SCHEME_MEMBER_UPDATE_ID)
    public ResponseEntity<SchemeMember> update(@PathVariable Long id, @RequestBody SchemeMemberDTO dto) {
        return ResponseEntity.ok(schemeMemberService.update(id, dto));
    }

    @DeleteMapping(BMS_SCHEME_MEMBER_DELETE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        schemeMemberService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(BMS_SCHEME_MEMBER_PRINCIPAL_DEPENDENT_WITH_LIMITS)
    public ResponseEntity<?> getPrincipalAndDependentsWithLimits(@PathVariable Long id) {
        var result = schemeMemberService.getPrincipalAndDependentsWithLimits(id);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }
}