package com.guangge.Interview.cohort;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CohortService {

    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CohortRepository cohorts;
    private final CohortMemberRepository members;

    public CohortService(CohortRepository cohorts, CohortMemberRepository members) {
        this.cohorts = cohorts;
        this.members = members;
    }

    @Transactional
    public CohortView create(String ownerId, CohortRequest request) {
        CohortEntity cohort = new CohortEntity();
        cohort.setId(UUID.randomUUID());
        cohort.setName(request.name().strip());
        cohort.setInviteCode(newCode());
        cohort.setOwnerId(ownerId);
        cohort.setCreatedAt(Instant.now());
        cohorts.save(cohort);
        addMember(cohort, ownerId, "coach");
        return view(cohort, "coach");
    }

    @Transactional
    public CohortView join(String ownerId, String inviteCode) {
        CohortEntity cohort = cohorts.findByInviteCodeIgnoreCase(inviteCode.strip())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "邀请码无效"));
        if (cohort.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "这个小组已归档");
        }
        CohortMemberEntity member = members.findByCohortIdAndOwnerId(cohort.getId(), ownerId).orElse(null);
        if (member == null) member = addMember(cohort, ownerId, "learner");
        return view(cohort, member.getRole());
    }

    @Transactional(readOnly = true)
    public List<CohortView> list(String ownerId) {
        return members.findAllByOwnerIdOrderByJoinedAtDesc(ownerId).stream()
                .map(member -> cohorts.findById(member.getCohortId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(cohort -> view(cohort, role(cohort.getId(), ownerId)))
                .toList();
    }

    @Transactional(readOnly = true)
    public CohortView get(String ownerId, UUID cohortId) {
        CohortEntity cohort = requireCohort(cohortId);
        String role = requireMember(cohortId, ownerId).getRole();
        return view(cohort, role);
    }

    @Transactional
    public void setScoreSharing(String ownerId, UUID cohortId, boolean shareScores) {
        CohortMemberEntity member = requireMember(cohortId, ownerId);
        member.setShareScores(shareScores);
        members.save(member);
    }

    @Transactional
    public void archive(String ownerId, UUID cohortId) {
        requireCoach(cohortId, ownerId);
        CohortEntity cohort = requireCohort(cohortId);
        cohort.setArchivedAt(Instant.now());
        cohorts.save(cohort);
    }

    @Transactional(readOnly = true)
    public List<CohortMemberEntity> memberEntities(UUID cohortId) {
        return members.findAllByCohortIdOrderByJoinedAtAsc(cohortId);
    }

    @Transactional(readOnly = true)
    public CohortMemberEntity requireMember(UUID cohortId, String ownerId) {
        requireCohort(cohortId);
        return members.findByCohortIdAndOwnerId(cohortId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "你不在这个小组里"));
    }

    @Transactional(readOnly = true)
    public void requireCoach(UUID cohortId, String ownerId) {
        if (!"coach".equals(requireMember(cohortId, ownerId).getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有助教可以执行这个操作");
        }
    }

    private CohortEntity requireCohort(UUID cohortId) {
        return cohorts.findById(cohortId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "小组不存在"));
    }

    private CohortMemberEntity addMember(CohortEntity cohort, String ownerId, String role) {
        CohortMemberEntity member = new CohortMemberEntity();
        member.setCohortId(cohort.getId());
        member.setOwnerId(ownerId);
        member.setRole(role);
        member.setShareScores(false);
        member.setJoinedAt(Instant.now());
        return members.save(member);
    }

    private CohortView view(CohortEntity cohort, String role) {
        List<CohortView.Member> memberViews = members.findAllByCohortIdOrderByJoinedAtAsc(cohort.getId()).stream()
                .map(member -> new CohortView.Member(member.getOwnerId(), member.getRole(),
                        member.isShareScores(), member.getJoinedAt()))
                .toList();
        return new CohortView(cohort.getId(), cohort.getName(), cohort.getInviteCode(), role,
                memberViews.size(), cohort.getCreatedAt(), memberViews);
    }

    private String role(UUID cohortId, String ownerId) {
        return requireMember(cohortId, ownerId).getRole();
    }

    private static String newCode() {
        char[] code = new char[8];
        for (int index = 0; index < code.length; index++) code[index] = CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)];
        return new String(code);
    }
}
