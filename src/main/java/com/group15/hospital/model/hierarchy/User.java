package com.group15.hospital.model.hierarchy;

import com.group15.hospital.model.enums.UserRole;

import java.time.LocalDateTime;
import java.util.Objects;

// represents a user or staff member in the system
public class User {

    private int userId;
    private String username;
    private String passwordHash;
    private String fullName;
    private UserRole role;

    // hospital id if user belongs to a specific hospital
    private Integer hospitalId;

    private LocalDateTime createdAt;

    public User() {
        this.role = UserRole.NURSE;
        this.createdAt = LocalDateTime.now();
    }

    public User(String username, String passwordHash, String fullName, UserRole role, Integer hospitalId) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = (role != null) ? role : UserRole.NURSE;
        this.hospitalId = hospitalId;
        this.createdAt = LocalDateTime.now();
    }

    public User(int userId, String username, String passwordHash, String fullName, 
                UserRole role, Integer hospitalId, LocalDateTime createdAt) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = (role != null) ? role : UserRole.NURSE;
        this.hospitalId = hospitalId;
        this.createdAt = (createdAt != null) ? createdAt : LocalDateTime.now();
    }

    // checks if user is admin
    public boolean isAdmin() {
        return this.role == UserRole.ADMIN;
    }

    // checks if user is doctor or nurse
    public boolean isMedicalStaff() {
        return this.role == UserRole.DOCTOR || this.role == UserRole.NURSE;
    }

    // checks if user has admin privileges
    public boolean hasAdminPrivileges() {
        return this.role != null && this.role.hasAdministrativeAccess();
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = (role != null) ? role : UserRole.NURSE;
    }

    public Integer getHospitalId() {
        return hospitalId;
    }

    public void setHospitalId(Integer hospitalId) {
        this.hospitalId = hospitalId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        String hospInfo = (hospitalId != null) ? "Hospital #" + hospitalId : "Network-Wide";
        return fullName + " (" + username + ") [" + role + " - " + hospInfo + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return userId == user.userId && Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, username);
    }
}
