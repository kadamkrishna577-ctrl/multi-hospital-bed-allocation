package com.group15.hospital.model.hierarchy;

import java.util.Objects;

// represents a ward in a hospital
public class Ward {
  private int wardId;
  private int hospitalId;
  private String wardName;
  private String wardType;
  private int floorNumber;

  public Ward() {

  }

  public Ward(int hospitalId, String wardName, String wardType, int floorNumber) {
    this.hospitalId = hospitalId;
    this.wardName = wardName;
    this.wardType = wardType;
    this.floorNumber = floorNumber;
  }

  public Ward(int wardId, int hospitalId, String wardName, String wardType, int floorNumber) {
    this.wardId = wardId;
    this.hospitalId = hospitalId;
    this.wardName = wardName;
    this.wardType = wardType;
    this.floorNumber = floorNumber;
  }

  public boolean isICU() {
    if (wardType == null) {
      return false;
    }
    return wardType.equalsIgnoreCase("ICU");
  }

  public boolean isIsolation() {
    if (wardType == null) {
      return false;
    }
    return wardType.equalsIgnoreCase("ISOLATION");
  }

  public int getWardId() {
    return wardId;
  }

  public void setWardId(int wardId) {
    this.wardId = wardId;
  }

  public int getHospitalId() {
    return hospitalId;
  }

  public void setHospitalId(int hospitalId) {
    this.hospitalId = hospitalId;
  }

  public String getWardName() {
    return wardName;
  }

  public void setWardName(String wardName) {
    this.wardName = wardName;
  }

  public String getWardType() {
    return wardType;
  }

  public void setWardType(String wardType) {
    this.wardType = wardType;
  }

  public int getFloorNumber() {
    return floorNumber;
  }

  public void setFloorNumber(int floorNumber) {
    this.floorNumber = floorNumber;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    Ward ward = (Ward) o;
    return wardId == ward.wardId && hospitalId == ward.hospitalId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(wardId, hospitalId);
  }
}
