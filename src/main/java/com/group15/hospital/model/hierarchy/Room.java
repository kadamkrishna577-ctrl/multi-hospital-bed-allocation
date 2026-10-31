package com.group15.hospital.model.hierarchy;

import java.util.Objects;

// represents a room inside a ward
public class Room {

    private int roomId;
    private int wardId;
    private String roomNumber;
    
    // true if room has negative pressure for isolation
    private boolean negativePressure;

    public Room() {
    }

    public Room(int wardId, String roomNumber, boolean negativePressure) {
        this.wardId = wardId;
        this.roomNumber = roomNumber;
        this.negativePressure = negativePressure;
    }

    public Room(int roomId, int wardId, String roomNumber, boolean negativePressure) {
        this.roomId = roomId;
        this.wardId = wardId;
        this.roomNumber = roomNumber;
        this.negativePressure = negativePressure;
    }

    public boolean isNegativePressure() {
        return negativePressure;
    }

    // --- Getters & Setters ---

    public int getRoomId() {
        return roomId;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }

    public int getWardId() {
        return wardId;
    }

    public void setWardId(int wardId) {
        this.wardId = wardId;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public void setNegativePressure(boolean negativePressure) {
        this.negativePressure = negativePressure;
    }

    @Override
    public String toString() {
        String type = negativePressure ? "Isolation (Negative Pressure)" : "Standard";
        return "Room " + roomNumber + " [" + type + ", Ward #" + wardId + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Room room = (Room) o;
        return roomId == room.roomId &&
               wardId == room.wardId &&
               Objects.equals(roomNumber, room.roomNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roomId, wardId, roomNumber);
    }
}
