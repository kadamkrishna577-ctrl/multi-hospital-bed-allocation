package com.group15.hospital.model.hierarchy;

import java.util.Objects;

// this is the file which represents a hospital...
public class Hospital {
  // unique primary key from the db
  private int hospitalId;
  private String name;
  private String address;
  private double latitude;
  private double longitude;
  private String contactPhone;
  private int totalBeds;

  // empty constructor needed by the DAO and framework when creating empty object
  // which will be then populated by the getters and setters
  public Hospital() {

  }

  // full constructor with the hospitalID needed for the DAO to create the
  // NEW Hospital with the ID from the db
  public Hospital(int hospitalID, String name, String address, double latitude, double longitude, String contactPhone,
      int totalBeds) {
    this.address = address;
    this.contactPhone = contactPhone;
    this.hospitalId = hospitalID;
    this.latitude = latitude;
    this.longitude = longitude;
    this.totalBeds = totalBeds;
    this.name = name;
  }

  // smaller so that the AUTO INCREMENT functions in the db can take
  // effect while making these
  public Hospital(String name, String address, double latitude, double longitude, String contactPhone, int totalBeds) {
    this.name = name;
    this.address = address;
    this.latitude = latitude;
    this.longitude = longitude;
    this.contactPhone = contactPhone;
    this.totalBeds = totalBeds;
  }

  // Haversine formula to compute great-circle distance in kilometers
  public double calculateDistanceTo(double targetLatitude, double targetLongitude) {
    if (Double.isNaN(targetLatitude) || Double.isNaN(targetLongitude) ||
        Double.isNaN(this.latitude) || Double.isNaN(this.longitude)) {
      return Double.MAX_VALUE;
    }

    double earthRadiusKm = 6371.0;

    double latDifference = Math.toRadians(targetLatitude - this.latitude);
    double lonDifference = Math.toRadians(targetLongitude - this.longitude);

    double hospitalLatInRad = Math.toRadians(this.latitude);
    double targetLatInRad = Math.toRadians(targetLatitude);

    double sinHalfLat = Math.sin(latDifference / 2.0);
    double sinHalfLon = Math.sin(lonDifference / 2.0);

    double a = (sinHalfLat * sinHalfLat)
        + (Math.cos(hospitalLatInRad) * Math.cos(targetLatInRad) * sinHalfLon * sinHalfLon);

    // Clamp floating point errors to prevent Math.sqrt of negative values
    a = Math.min(1.0, Math.max(0.0, a));

    double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

    return earthRadiusKm * c;
  }

  public int getHospitalId() {
    return hospitalId;
  }

  public int getHospitalID() {
    return hospitalId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    Hospital hospital = (Hospital) o;
    return hospitalId == hospital.hospitalId && Objects.equals(name, hospital.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(hospitalId, name);
  }

  public void setHospitalId(int hospitalId) {
    this.hospitalId = hospitalId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public double getLatitude() {
    return latitude;
  }

  public void setLatitude(double latitude) {
    this.latitude = latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public void setLongitude(double longitude) {
    this.longitude = longitude;
  }

  public String getContactPhone() {
    return contactPhone;
  }

  public void setContactPhone(String contactPhone) {
    this.contactPhone = contactPhone;
  }

  public int getTotalBeds() {
    return totalBeds;
  }

  public void setTotalBeds(int totalBeds) {
    this.totalBeds = totalBeds;
  }
}
