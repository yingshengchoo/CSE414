package scheduler.model;

import scheduler.db.ConnectionManager;
import scheduler.util.Util;

import java.sql.*;
import java.util.Arrays;

public class Patient {
    private final String username;
    private final byte[] salt;
    private final byte[] hash;

    private Patient(PatientBuilder builder) {
        this.username = builder.username;
        this.salt = builder.salt;
        this.hash = builder.hash;
    }

    private Patient(PatientGetter getter) {
        this.username = getter.username;
        this.salt = getter.salt;
        this.hash = getter.hash;
    }

    // Getters
    public String getUsername() {
        return username;
    }

    public byte[] getSalt() {
        return salt;
    }

    public byte[] getHash() {
        return hash;
    }

    public void saveToDB() throws SQLException {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String addPatient = "INSERT INTO Patients VALUES (? , ?, ?); ";
        try {
            PreparedStatement statement = con.prepareStatement(addPatient);
            statement.setString(1, this.username);
            statement.setBytes(2, this.salt);
            statement.setBytes(3, this.hash);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLException();
        } finally {
            cm.closeConnection();
        }
    }

    public void viewAppointments() throws SQLException {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        String getAppointments = "SELECT ID, Vaccine, Time, Caregiver FROM Appointments WHERE Patient = ?; ";
        try {
            PreparedStatement statement = con.prepareStatement(getAppointments);
            statement.setString(1, this.username);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                System.out.println("id: " + rs.getInt(1) + ", Vaccine: " + rs.getString(2)
                    + ", Time: " + rs.getDate(3) + ", Caregiver: " + rs.getString(4));
            }
        } catch (SQLException e) {
            throw new SQLException();
        } finally {
            cm.closeConnection();
        }
    }

    public void reserve(Date d, Vaccine vaccine) throws SQLException {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        String checkAvailability = "SELECT Username FROM Availabilities WHERE Time = ?; ";
        String createAppointment = "INSERT INTO Appointments (Vaccine, Time, Patient, Caregiver) " +
                "VALUES (? , ?, ?, ?); ";
        try{
            PreparedStatement statement1 = con.prepareStatement(checkAvailability);
            statement1.setDate(1, d);
            ResultSet rs = statement1.executeQuery();
            if (!rs.next()) {
                System.out.println("Failed to schedule Appointment. The given date is unavailable. " +
                        "Please try again.");
            } else {
                try {
                    vaccine.decreaseAvailableDoses(1);
                } catch (SQLException e) {
                    System.out.println("Error occurred when creating appointment");
                    e.printStackTrace();
                }
                PreparedStatement statement2 = con.prepareStatement(createAppointment);
                statement2.setString(1, vaccine.getVaccineName());
                statement2.setDate(2, d);
                statement2.setString(3, this.username);
                statement2.setString(4, rs.getString("Username"));
                statement2.executeUpdate();
                System.out.println("Appointment Reserved!");
            }
        } catch (SQLException e) {
            throw new SQLException();
        } finally {
            cm.closeConnection();
        }
    }

    public static class PatientBuilder {
        private final String username;
        private final byte[] salt;
        private final byte[] hash;

        public PatientBuilder(String username, byte[] salt, byte[] hash) {
            this.username = username;
            this.salt = salt;
            this.hash = hash;
        }

        public Patient build() {
            return new Patient(this);
        }
    }

    public static class PatientGetter {
        private final String username;
        private final String password;
        private byte[] salt;
        private byte[] hash;

        public PatientGetter(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public Patient get() throws SQLException {
            ConnectionManager cm = new ConnectionManager();
            Connection con = cm.createConnection();

            String getPatient = "SELECT Salt, Hash FROM Patients WHERE Username = ?; ";
            try {
                PreparedStatement statement = con.prepareStatement(getPatient);
                statement.setString(1, this.username);
                ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    byte[] salt = resultSet.getBytes("Salt");
                    // we need to call Util.trim() to get rid of the paddings,
                    // try to remove the use of Util.trim() and you'll see :)
                    byte[] hash = Util.trim(resultSet.getBytes("Hash"));
                    // check if the password matches
                    byte[] calculatedHash = Util.generateHash(password, salt);
                    if (!Arrays.equals(hash, calculatedHash)) {
                        return null;
                    } else {
                        this.salt = salt;
                        this.hash = hash;
                        return new Patient(this);
                    }
                }
                return null;
            } catch (SQLException e) {
                throw new SQLException();
            } finally {
                cm.closeConnection();
            }
        }
    }
}
