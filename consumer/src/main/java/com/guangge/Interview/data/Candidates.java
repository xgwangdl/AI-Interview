package com.guangge.Interview.data;

import jakarta.persistence.*;

import java.sql.Date;
import java.time.Instant;

@Entity
@Table(name = "candidates")
public class Candidates {
    @Id
    // 主键由 Flyway 建的 interviewer_id_seq 提供；改成 SEQUENCE 才能让 JPA 正常插入，
    // IDENTITY 依赖的是 serial/identity 列，而这张表的 id 只是 DEFAULT nextval(...)。
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "interviewer_id_seq")
    @SequenceGenerator(name = "interviewer_id_seq", sequenceName = "interviewer_id_seq", allocationSize = 1)
    private Long id;

    // 登录名
    @Column(name = "candidate_name", nullable = false, unique = true)
    private String name;

    // 口令（BCrypt 哈希）。老数据可能是明文邀请码，登录成功后会就地升级成哈希。
    @Column(name = "invitation_code", nullable = false)
    private String code;

    // 简历
    @Column(name = "cv", nullable = true)
    private String cv;

    // 邮箱
    @Column(name = "email", nullable = true)
    private String email;

    // 出生日期
    @Column(name = "birth", nullable = true)
    private Date birth;

    // 状态 1.待定 2.通知面试 3.面试完毕
    @Column(name = "status", nullable = true)
    @Convert(converter = CandidateStatusConverter.class)
    private CandidateStatus status;

    @Column(name = "picture_url", nullable = true)
    private String pictureUrl;

    // 个人页面主题色（green / blue / amber），为空表示用默认色。
    @Column(name = "theme", nullable = true, length = 32)
    private String theme;

    // 展示昵称。登录名 candidate_name 继续作为数据归属键，不允许在资料页修改。
    @Column(name = "display_name", length = 40)
    private String displayName;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "font_scale", nullable = false, length = 16)
    private String fontScale;

    @Column(name = "reduce_motion", nullable = false)
    private boolean reduceMotion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCv() {
        return cv;
    }

    public void setCv(String cv) {
        this.cv = cv;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Date getBirth() {
        return birth;
    }

    public void setBirth(Date birth) {
        this.birth = birth;
    }

    public CandidateStatus getStatus() {
        return status;
    }

    public void setStatus(CandidateStatus status) {
        this.status = status;
    }

    public String getPictureUrl() {
        return pictureUrl;
    }

    public void setPictureUrl(String pictureUrl) {
        this.pictureUrl = pictureUrl;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getFontScale() {
        return fontScale;
    }

    public void setFontScale(String fontScale) {
        this.fontScale = fontScale;
    }

    public boolean isReduceMotion() {
        return reduceMotion;
    }

    public void setReduceMotion(boolean reduceMotion) {
        this.reduceMotion = reduceMotion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @PrePersist
    void applyProfileDefaults() {
        if (displayName == null || displayName.isBlank()) {
            displayName = name;
        }
        if (timezone == null || timezone.isBlank()) {
            timezone = "Asia/Shanghai";
        }
        if (fontScale == null || fontScale.isBlank()) {
            fontScale = "standard";
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
