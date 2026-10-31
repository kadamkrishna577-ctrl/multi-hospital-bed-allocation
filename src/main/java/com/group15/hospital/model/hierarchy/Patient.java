package com.group15.hospital.model.hierarchy;

import com.group15.hospital.model.enums.DietType;
import com.group15.hospital.model.enums.TriageLevel;

import java.time.LocalDateTime;
import java.util.Objects;

// represents a patient in the hospital system
public class Patient {

    private long patientId;
    private String fullName;
    private int age;
    private String gender;

    // triage urgency level
    private TriageLevel triageLevel;

    // bed assigned to patient
    private Long assignedBedId;

    // diet type requirement
    private DietType dietType;

    // timestamps for admission and discharge
    private LocalDateTime admissionTime;
    private LocalDateTime dischargeTime;
    private boolean discharged;

    // default constructor
    public Patient() {
        this.triageLevel = TriageLevel.LEVEL_5;
        this.dietType = DietType.REGULAR;
        this.admissionTime = LocalDateTime.now();
        this.discharged = false;
    }

    // constructor for creating a new patient
    public Patient(String fullName, int age, String gender, TriageLevel triageLevel, DietType dietType) {
        this.fullName = fullName;
        this.age = age;
        this.gender = gender;
        this.triageLevel = (triageLevel != null) ? triageLevel : TriageLevel.LEVEL_5;
        this.dietType = (dietType != null) ? dietType : DietType.REGULAR;
        this.admissionTime = LocalDateTime.now();
        this.discharged = false;
        this.assignedBedId = null;
    }

    // full constructor with all fields
    public Patient(long patientId, String fullName, int age, String gender,
                   TriageLevel triageLevel, Long assignedBedId, DietType dietType,
                   LocalDateTime admissionTime, LocalDateTime dischargeTime, boolean discharged) {
        this.patientId = patientId;
        this.fullName = fullName;
        this.age = age;
        this.gender = gender;
        this.triageLevel = (triageLevel != null) ? triageLevel : TriageLevel.LEVEL_5;
        this.assignedBedId = assignedBedId;
        this.dietType = (dietType != null) ? dietType : DietType.REGULAR;
        this.admissionTime = (admissionTime != null) ? admissionTime : LocalDateTime.now();
        this.dischargeTime = dischargeTime;
        this.discharged = discharged;
    }

    // checks if patient is critical
    public boolean isCritical() {
        return this.triageLevel == TriageLevel.LEVEL_1;
    }

    // checks if patient has an assigned bed
    public boolean isAdmitted() {
        return this.assignedBedId != null && !this.discharged;
    }

    // assigns bed to patient
    public void assignBed(long bedId) {
        this.assignedBedId = bedId;
        this.discharged = false;
        if (this.admissionTime == null) {
            this.admissionTime = LocalDateTime.now();
        }
    }

    // discharges patient and clears bed
    public void discharge() {
        this.discharged = true;
        this.dischargeTime = LocalDateTime.now();
        this.assignedBedId = null;
    }

    public long getPatientId() {
        return patientId;
    }

    public void setPatientId(long patientId) {
        this.patientId = patientId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public TriageLevel getTriageLevel() {
        return triageLevel;
    }

    public void setTriageLevel(TriageLevel triageLevel) {
        this.triageLevel = (triageLevel != null) ? triageLevel : TriageLevel.LEVEL_5;
    }

    public Long getAssignedBedId() {
        return assignedBedId;
    }

    public void setAssignedBedId(Long assignedBedId) {
        this.assignedBedId = assignedBedId;
    }

    public DietType getDietType() {
        return dietType;
    }

    public void setDietType(DietType dietType) {
        this.dietType = (dietType != null) ? dietType : DietType.REGULAR;
    }

    public LocalDateTime getAdmissionTime() {
        return admissionTime;
    }

    public void setAdmissionTime(LocalDateTime admissionTime) {
        this.admissionTime = admissionTime;
    }

    public LocalDateTime getDischargeTime() {
        return dischargeTime;
    }

    public void setDischargeTime(LocalDateTime dischargeTime) {
        this.dischargeTime = dischargeTime;
    }

    public boolean isDischarged() {
        return discharged;
    }

    public void setDischarged(boolean discharged) {
        this.discharged = discharged;
    }

    @Override
    public String toString() {
        String bedInfo = (assignedBedId != null) ? "Bed #" + assignedBedId : "Unassigned";
        return fullName + " (Age: " + age + ", Triage: " + triageLevel + ", Diet: " + dietType + ", " + bedInfo + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Patient patient = (Patient) o;
        return patientId == patient.patientId &&
               age == patient.age &&
               Objects.equals(fullName, patient.fullName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(patientId, fullName, age);
    }
}
