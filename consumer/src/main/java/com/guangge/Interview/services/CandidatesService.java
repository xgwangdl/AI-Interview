package com.guangge.Interview.services;

import com.guangge.Interview.data.Candidates;
import com.guangge.Interview.data.CandidateStatus;
import com.guangge.Interview.record.CandidateRecord;
import com.guangge.Interview.repository.CandidatesRepository;
import com.guangge.Interview.util.Dto2Record;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class CandidatesService {

    /** 用户名允许中英文、数字与 _ . - ；口令长度下限沿用「邀请码」时代的习惯，但提高到 6 位。 */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\p{sc=Han}A-Za-z0-9_.-]+$");
    private static final int MIN_NAME_LENGTH = 2;
    private static final int MAX_NAME_LENGTH = 80;
    private static final int MIN_CODE_LENGTH = 6;
    private static final int MAX_CODE_LENGTH = 100;

    private final CandidatesRepository interviewerRepository;
    private final PasswordEncoder passwordEncoder;

    public CandidatesService(CandidatesRepository interviewerRepository, PasswordEncoder passwordEncoder) {
        this.interviewerRepository = interviewerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 登录：按用户名找账号，再校验口令。
     *
     * 历史数据里 invitation_code 存的是明文邀请码，所以这里同时兼容两种格式，
     * 并在明文校验通过后就地升级成 BCrypt 哈希——否则老账号永远是明文。
     */
    @Transactional
    public Candidates login(String name, String code) {
        String cleanName = name == null ? "" : name.trim();
        Candidates candidate = this.interviewerRepository.findFirstByName(cleanName)
                .orElseThrow(CandidatesService::invalidCredentials);
        if (!passwordMatches(code, candidate.getCode())) {
            throw invalidCredentials();
        }
        if (!isBcrypt(candidate.getCode())) {
            candidate.setCode(passwordEncoder.encode(code));
            interviewerRepository.save(candidate);
        }
        return candidate;
    }

    /** 注册新用户：用户名唯一、口令哈希存储，注册成功即可直接登录。 */
    @Transactional
    public Candidates register(String name, String code, String email) {
        String cleanName = name == null ? "" : name.trim();
        validateName(cleanName);
        validateCode(code);
        if (interviewerRepository.existsByName(cleanName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该用户名已被注册，请换一个");
        }
        Candidates candidate = new Candidates();
        candidate.setName(cleanName);
        candidate.setCode(passwordEncoder.encode(code));
        if (email != null && !email.isBlank()) {
            candidate.setEmail(email.trim());
        }
        candidate.setStatus(CandidateStatus.WAIT);
        try {
            return interviewerRepository.save(candidate);
        } catch (DataIntegrityViolationException exception) {
            // 并发注册同名账号时唯一索引会拦下来，转成明确的 409 而不是 500。
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该用户名已被注册，请换一个", exception);
        }
    }

    /** 已登录账号修改密码；登录令牌就是本次操作的授权凭证，写入时一律使用 BCrypt。 */
    @Transactional
    public void changePassword(String owner, String newPassword) {
        Candidates candidate = interviewerRepository.findFirstByName(owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "账号不存在"));
        validateCode(newPassword);
        candidate.setCode(passwordEncoder.encode(newPassword));
        interviewerRepository.save(candidate);
    }

    /**
     * 返回所有候选者信息
     * @return 抽选者信息
     */
    public List<CandidateRecord> getCandidates() {
        List<Candidates> interviewers = this.interviewerRepository.findAll(Sort.by("id").descending());
        return interviewers.stream().map(Dto2Record::toCandidateRecord).toList();
    }

    private boolean passwordMatches(String raw, String stored) {
        if (raw == null || stored == null || stored.isBlank()) return false;
        if (isBcrypt(stored)) return passwordEncoder.matches(raw, stored);
        // 历史明文口令：用常量时间比较，避免逐字符比较带来的时序差异。
        return MessageDigest.isEqual(
                raw.getBytes(StandardCharsets.UTF_8),
                stored.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBcrypt(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

    private static void validateName(String name) {
        if (name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "用户名长度需在 " + MIN_NAME_LENGTH + " 到 " + MAX_NAME_LENGTH + " 个字符之间");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "用户名只能包含中文、字母、数字与 _ . -");
        }
    }

    private static void validateCode(String code) {
        if (code == null || code.length() < MIN_CODE_LENGTH || code.length() > MAX_CODE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "密码长度需在 " + MIN_CODE_LENGTH + " 到 " + MAX_CODE_LENGTH + " 个字符之间");
        }
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "用户名或密码错误");
    }
}
