package com.group15.hospital.dao;

import com.group15.hospital.config.DatabaseConnection;
import com.group15.hospital.model.enums.UserRole;
import com.group15.hospital.model.hierarchy.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

// user dao class
public class UserDAO {

    private static final Logger LOGGER = Logger.getLogger(UserDAO.class.getName());

    private final DatabaseConnection db = DatabaseConnection.getInstance();

    // in-memory store for fallback
    private final ConcurrentHashMap<String, User> userByUsernameStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, User> userByIdStore = new ConcurrentHashMap<>();
    private final AtomicInteger idSequence = new AtomicInteger(10);

    // sql queries
    private static final String SQL_INSERT =
            "INSERT INTO users (username, password_hash, full_name, role, hospital_id, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String SQL_SELECT_BY_USERNAME =
            "SELECT user_id, username, password_hash, full_name, role, hospital_id, created_at " +
            "FROM users WHERE username = ?";

    private static final String SQL_SELECT_BY_ID =
            "SELECT user_id, username, password_hash, full_name, role, hospital_id, created_at " +
            "FROM users WHERE user_id = ?";

    private static final String SQL_SELECT_ALL =
            "SELECT user_id, username, password_hash, full_name, role, hospital_id, created_at " +
            "FROM users ORDER BY user_id ASC";

    public UserDAO() {
        // seed sample users
        seedUser(1, "admin", "admin123", "Chief Administrator", UserRole.ADMIN, null);
        seedUser(2, "doctor", "doc123", "Dr. Aakash Roy (Cardiology)", UserRole.DOCTOR, 1);
        seedUser(3, "nurse", "nurse123", "Sister Mary (ICU Charge)", UserRole.NURSE, 1);
        seedUser(4, "paramedic", "para123", "Alex Turner (Ambulance Lead)", UserRole.PARAMEDIC, 1);
        seedUser(5, "cleaner", "clean123", "Sunil Yadav (Sanitization)", UserRole.CLEANING_STAFF, 1);
    }

    private void seedUser(int id, String username, String password, String name, UserRole role, Integer hospId) {
        User u = new User(id, username, password, name, role, hospId, LocalDateTime.now());
        userByUsernameStore.put(username.toLowerCase(), u);
        userByIdStore.put(id, u);
    }

    // authenticates user with username and password
    public Optional<User> authenticate(String username, String password) {
        if (username == null || password == null) {
            return Optional.empty();
        }

        Optional<User> userOpt = findByUsername(username.trim());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Verify password (plain / hash check)
            if (password.equals(user.getPasswordHash())) {
                return Optional.of(user);
            }
        }
        return Optional.empty();
    }

    // finds user by username
    public Optional<User> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }

        String search = username.trim();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_USERNAME)) {

            ps.setString(1, search);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User user = mapRowToUser(rs);
                    cacheUser(user);
                    return Optional.of(user);
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB query failed for username=" + username + ". Using fallback: " + e.getMessage());
        }

        return Optional.ofNullable(userByUsernameStore.get(search.toLowerCase()));
    }

    // finds user by id
    public Optional<User> findById(int userId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ID)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User user = mapRowToUser(rs);
                    cacheUser(user);
                    return Optional.of(user);
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB query failed for userId=" + userId + ". Using fallback: " + e.getMessage());
        }

        return Optional.ofNullable(userByIdStore.get(userId));
    }

    // saves new user
    public User save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, (user.getUsername() != null) ? user.getUsername() : "");
            ps.setString(2, (user.getPasswordHash() != null) ? user.getPasswordHash() : "");
            ps.setString(3, (user.getFullName() != null) ? user.getFullName() : "Staff Member");
            ps.setString(4, (user.getRole() != null) ? user.getRole().name() : UserRole.NURSE.name());

            if (user.getHospitalId() != null) {
                ps.setInt(5, user.getHospitalId());
            } else {
                ps.setNull(5, Types.INTEGER);
            }

            Timestamp ts = (user.getCreatedAt() != null)
                    ? Timestamp.valueOf(user.getCreatedAt())
                    : Timestamp.valueOf(LocalDateTime.now());
            ps.setTimestamp(6, ts);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        user.setUserId(rs.getInt(1));
                    }
                }
            }
            cacheUser(user);
            return user;

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to insert user into DB, writing to memory store: " + e.getMessage());
            int newId = idSequence.incrementAndGet();
            user.setUserId(newId);
            cacheUser(user);
            return user;
        }
    }

    // returns all registered users
    public List<User> findAll() {
        List<User> list = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                User u = mapRowToUser(rs);
                list.add(u);
                cacheUser(u);
            }
            if (!list.isEmpty()) {
                return list;
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB query failed for findAll users. Using fallback: " + e.getMessage());
        }

        return new ArrayList<>(userByIdStore.values());
    }

    private void cacheUser(User user) {
        if (user != null && user.getUsername() != null) {
            userByUsernameStore.put(user.getUsername().toLowerCase(), user);
            userByIdStore.put(user.getUserId(), user);
        }
    }

    private User mapRowToUser(ResultSet rs) throws SQLException {
        int id = rs.getInt("user_id");
        String username = rs.getString("username");
        String pass = rs.getString("password_hash");
        String name = rs.getString("full_name");
        UserRole role = UserRole.parseDatabase(rs.getString("role"));

        int hospVal = rs.getInt("hospital_id");
        Integer hospId = rs.wasNull() ? null : hospVal;

        Timestamp ts = rs.getTimestamp("created_at");
        LocalDateTime createdAt = (ts != null) ? ts.toLocalDateTime() : LocalDateTime.now();

        return new User(id, username, pass, name, role, hospId, createdAt);
    }
}
