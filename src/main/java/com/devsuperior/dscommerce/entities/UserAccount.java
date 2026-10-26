package com.devsuperior.dscommerce.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "tb_user_account", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_account_name", columnNames = "name"),
        @UniqueConstraint(name = "uk_user_account_email_key", columnNames = "email_key")
})
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String name;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = true, length = 240)
    private String fullName;

    @Column(name = "email", nullable = true, length = 508)
    private String email;

    @Column(name = "email_key", nullable = true, length = 1016)
    private byte[] emailKey;

    @Column(name = "phone", nullable = true, length = 16)
    private String phone;

    @Column(name = "birth_date", nullable = true)
    private LocalDate birthDate;

    @ElementCollection(fetch = FetchType.LAZY)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "tb_user_account_role",
            joinColumns = @JoinColumn(name = "user_account_id", nullable = false),
            foreignKey = @ForeignKey(name = "fk_user_account_role_account"),
            uniqueConstraints = @UniqueConstraint(name = "uk_user_account_role",
                    columnNames = {"user_account_id", "role"}))
    @Column(name = "role", nullable = false)
    private Set<UserRole> roles = new HashSet<>();

    protected UserAccount() {
    }

    public UserAccount(Long id, String name, String passwordHash) {
        this.id = id;
        this.name = name;
        this.passwordHash = passwordHash;
    }

    /** New account values; the service assigns CLIENT through the existing roles collection. */
    public UserAccount(String name, String passwordHash, String fullName, String email, byte[] emailKey,
                       String phone, LocalDate birthDate) {
        this(null, name, passwordHash);
        this.fullName = fullName;
        this.email = email;
        this.emailKey = emailKey == null ? null : emailKey.clone();
        this.phone = phone;
        this.birthDate = birthDate;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }

    @JsonIgnore
    public String getFullName() {
        return fullName;
    }

    @JsonIgnore
    public String getEmail() {
        return email;
    }

    @JsonIgnore
    public byte[] getEmailKey() {
        return emailKey == null ? null : emailKey.clone();
    }

    @JsonIgnore
    public String getPhone() {
        return phone;
    }

    @JsonIgnore
    public LocalDate getBirthDate() {
        return birthDate;
    }

    @JsonIgnore
    public Set<UserRole> getRoles() {
        return roles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserAccount that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
