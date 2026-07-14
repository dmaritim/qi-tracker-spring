package com.qitracker.service;

import com.qitracker.domain.OrgUnit;
import com.qitracker.domain.User;
import com.qitracker.domain.UserRole;
import com.qitracker.dto.OrgUnitDtos.OrgUnitRequest;
import com.qitracker.dto.OrgUnitDtos.OrgUnitResponse;
import com.qitracker.exception.ApiException;
import com.qitracker.repository.OrgUnitRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrgUnitService {

    private final OrgUnitRepository orgUnitRepository;

    public OrgUnitService(OrgUnitRepository orgUnitRepository) {
        this.orgUnitRepository = orgUnitRepository;
    }

    @Transactional(readOnly = true)
    public List<OrgUnitResponse> listAll() {
        return orgUnitRepository.findAllByOrderByLevelAscNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public OrgUnitResponse create(User requester, OrgUnitRequest req) {
        requireAdmin(requester);
        validate(req);
        OrgUnit unit = OrgUnit.builder()
            .name(req.name().trim())
            .shortName(req.shortName())
            .code(req.code())
            .parent(resolveParent(null, req.parentUuid()))
            .level(req.level())
            .build();
        unit = orgUnitRepository.save(unit);
        return toResponse(unit);
    }

    @Transactional
    public OrgUnitResponse update(String uuid, User requester, OrgUnitRequest req) {
        requireAdmin(requester);
        validate(req);
        OrgUnit unit = getOrThrow(uuid);
        unit.setName(req.name().trim());
        unit.setShortName(req.shortName());
        unit.setCode(req.code());
        unit.setParent(resolveParent(uuid, req.parentUuid()));
        unit.setLevel(req.level());
        unit = orgUnitRepository.save(unit);
        return toResponse(unit);
    }

    @Transactional
    public void delete(String uuid, User requester) {
        requireAdmin(requester);
        OrgUnit unit = getOrThrow(uuid);
        try {
            orgUnitRepository.delete(unit);
            orgUnitRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict(
                "Can't delete \"" + unit.getName() + "\" — it has child org units and/or projects still pointing at it. " +
                "Move or remove those first.");
        }
    }

    private void requireAdmin(User requester) {
        if (requester.getRole() != UserRole.ADMIN) throw ApiException.forbidden("Admins only.");
    }

    private OrgUnit getOrThrow(String uuid) {
        return orgUnitRepository.findById(uuid).orElseThrow(() -> ApiException.notFound("Org unit not found."));
    }

    private OrgUnit resolveParent(String selfUuid, String parentUuid) {
        if (parentUuid == null || parentUuid.isBlank()) return null;
        if (parentUuid.equals(selfUuid)) throw ApiException.badRequest("An org unit can't be its own parent.");
        OrgUnit parent = orgUnitRepository.findById(parentUuid)
            .orElseThrow(() -> ApiException.badRequest("Selected parent org unit doesn't exist."));

        // Cycle check: walk up from the proposed parent — if we hit `self`, this would create a loop.
        if (selfUuid != null) {
            OrgUnit cursor = parent;
            int guard = 0;
            while (cursor != null && guard < 100) {
                if (cursor.getUuid().equals(selfUuid)) {
                    throw ApiException.badRequest("That would make this org unit its own ancestor — pick a different parent.");
                }
                cursor = cursor.getParent();
                guard++;
            }
        }
        return parent;
    }

    private void validate(OrgUnitRequest req) {
        if (req.name() == null || req.name().isBlank()) throw ApiException.badRequest("Name is required.");
    }

    private OrgUnitResponse toResponse(OrgUnit u) {
        return new OrgUnitResponse(
            u.getUuid(), u.getName(), u.getShortName(), u.getCode(),
            u.getParent() == null ? null : u.getParent().getUuid(),
            u.getParent() == null ? null : u.getParent().getName(),
            u.getLevel(), u.getCreatedAt()
        );
    }
}