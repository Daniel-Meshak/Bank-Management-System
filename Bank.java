
package org.example;

import java.math.BigDecimal;
import java.sql.*;
import java.util.Scanner;

public class Bank {

    private final Scanner sc;

    public Bank(Scanner sc) {
        this.sc = sc;
    }

    private int readInt(String message) {
        while (true) {
            System.out.print(message);
            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    private BigDecimal readAmount() {
        while (true) {
            System.out.print("Enter amount: ");
            try {
                BigDecimal amount = new BigDecimal(sc.nextLine().trim());
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    return amount;
                }
                System.out.println("Amount must be greater than zero.");
            } catch (NumberFormatException e) {
                System.out.println("Enter a valid amount.");
            }
        }
    }

    public void createAccount() {
        try {
            System.out.print("Enter account holder name: ");
            String name = sc.nextLine().trim();

            if (name.isEmpty()) {
                System.out.println("Name cannot be empty.");
                return;
            }

            System.out.print("Create a 4-digit PIN: ");
            String pin = sc.nextLine().trim();

            if (!pin.matches("\\d{4}")) {
                System.out.println("PIN must contain exactly 4 digits.");
                return;
            }

            String hashedPin = PinUtil.hashPin(pin);

            String sql = "INSERT INTO accounts (holder_name, pin, balance) VALUES (?, ?, ?)";

            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, name);
                ps.setString(2, hashedPin);
                ps.setBigDecimal(3, BigDecimal.ZERO);

                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        System.out.println("Account created successfully!");
                        System.out.println("Your account number: " + rs.getInt(1));
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("Error creating account: " + e.getMessage());
        }
    }

    private int authenticate() {
        int accountNumber = readInt("Enter account number: ");

        System.out.print("Enter your 4-digit PIN: ");
        String pin = sc.nextLine().trim();

        if (!pin.matches("\\d{4}")) {
            System.out.println("Invalid PIN format.");
            return -1;
        }

        String sql = "SELECT pin FROM accounts WHERE account_number = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, accountNumber);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && PinUtil.verifyPin(pin, rs.getString("pin"))) {
                    return accountNumber;
                }
            }

        } catch (Exception e) {
            System.out.println("Authentication error: " + e.getMessage());
        }

        System.out.println("Invalid account number or PIN.");
        return -1;
    }

    public void deposit() {
        int accountNumber = authenticate();
        if (accountNumber == -1) return;

        BigDecimal amount = readAmount();

        String updateSql =
                "UPDATE accounts SET balance = balance + ? WHERE account_number = ?";
        String historySql =
                "INSERT INTO transactions (account_number, transaction_type, amount) VALUES (?, ?, ?)";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);

            try (PreparedStatement update = con.prepareStatement(updateSql);
                 PreparedStatement history = con.prepareStatement(historySql)) {

                update.setBigDecimal(1, amount);
                update.setInt(2, accountNumber);
                update.executeUpdate();

                history.setInt(1, accountNumber);
                history.setString(2, "DEPOSIT");
                history.setBigDecimal(3, amount);
                history.executeUpdate();

                con.commit();
                System.out.println("Deposit successful!");
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }

        } catch (SQLException e) {
            System.out.println("Deposit failed: " + e.getMessage());
        }
    }

    public void withdraw() {
        int accountNumber = authenticate();
        if (accountNumber == -1) return;

        BigDecimal amount = readAmount();

        String updateSql =
                "UPDATE accounts SET balance = balance - ? WHERE account_number = ? AND balance >= ?";
        String historySql =
                "INSERT INTO transactions (account_number, transaction_type, amount) VALUES (?, ?, ?)";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);

            try (PreparedStatement update = con.prepareStatement(updateSql);
                 PreparedStatement history = con.prepareStatement(historySql)) {

                update.setBigDecimal(1, amount);
                update.setInt(2, accountNumber);
                update.setBigDecimal(3, amount);

                int rows = update.executeUpdate();

                if (rows == 0) {
                    con.rollback();
                    System.out.println("Insufficient balance.");
                    return;
                }

                history.setInt(1, accountNumber);
                history.setString(2, "WITHDRAW");
                history.setBigDecimal(3, amount);
                history.executeUpdate();

                con.commit();
                System.out.println("Withdrawal successful!");
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }

        } catch (SQLException e) {
            System.out.println("Withdrawal failed: " + e.getMessage());
        }
    }

    public void checkBalance() {
        int accountNumber = authenticate();
        if (accountNumber == -1) return;

        String sql = "SELECT balance FROM accounts WHERE account_number = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, accountNumber);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.println("Current balance: Rs. " +
                            rs.getBigDecimal("balance"));
                }
            }

        } catch (SQLException e) {
            System.out.println("Error checking balance: " + e.getMessage());
        }
    }

    public void transactionHistory() {
        int accountNumber = authenticate();
        if (accountNumber == -1) return;

        String sql = "SELECT transaction_type, amount, transaction_date " +
                "FROM transactions WHERE account_number = ? " +
                "ORDER BY transaction_id DESC";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, accountNumber);

            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("\n===== TRANSACTION HISTORY =====");

                boolean found = false;

                while (rs.next()) {
                    found = true;
                    System.out.println(
                            rs.getString("transaction_type") +
                                    " | Rs. " + rs.getBigDecimal("amount") +
                                    " | " + rs.getTimestamp("transaction_date")
                    );
                }

                if (!found) {
                    System.out.println("No transactions found.");
                }
            }

        } catch (SQLException e) {
            System.out.println("Error loading history: " + e.getMessage());
        }
    }
}
