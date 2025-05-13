package mini;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/AuthServlet")
public class AuthServlet extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        String action = request.getParameter("action");

        if ("signup".equals(action)) {
            handleSignup(request, response, out);
        } else if ("login".equals(action)) {
            handleLogin(request, response, out);
        } else if ("logout".equals(action)) {
            handleLogout(request, response, out);
        } else if ("updateProfile".equals(action)) {
            handleProfileUpdate(request, response, out);
        } else if ("applyJob".equals(action)) {
            handleApplyJob(request, response, out);
        } else if ("getAppliedJobs".equals(action)) {
            handleGetAppliedJobs(request, response, out);
        } else if ("approveApplication".equals(action)) {
            handleApproveApplication(request, response, out);
        }else if ("dashboard".equals(request.getParameter("action"))) {
            handleDashboard(request, response, out);
        }else if ("getAllPostedApplications".equals(request.getParameter("action"))) {
            handleAllPostedApplications(request, response);
        }else {
            out.println("{\"error\": \"Invalid action.\"}");
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        HttpSession session = request.getSession(false);

        if (session != null && session.getAttribute("user_id") != null) {
            int userId = (int) session.getAttribute("user_id");

            try {
                Connection conn = DBConnection.getConnection();
                if (conn == null) {
                    out.println("{\"error\": \"Database connection failed.\"}");
                    return;
                }

                PreparedStatement stmt = conn.prepareStatement(
                        "SELECT full_name, username, email FROM users WHERE user_id = ?");
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    out.println("{ \"user_id\": " + userId +
                                ", \"full_name\": \"" + rs.getString("full_name") + "\"" +
                                ", \"username\": \"" + rs.getString("username") + "\"" +
                                ", \"email\": \"" + rs.getString("email") + "\" }");
                } else {
                    out.println("{\"error\": \"User not found.\"}");
                }
                conn.close();
            } catch (Exception e) {
                e.printStackTrace();
                out.println("{\"error\": \"Error fetching user info.\"}");
            }
        } else {
            out.println("{\"user_id\": null}");
        }
    }
    private void handleAllPostedApplications(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        if (session == null || session.getAttribute("user_id") == null) {
            out.println("[]");
            return;
        }

        int posterId = (int) session.getAttribute("user_id");

        try (Connection conn = DBConnection.getConnection()) {
            // Get all jobs posted by this user
            PreparedStatement jobStmt = conn.prepareStatement(
                "SELECT job_id, title, company FROM jobs WHERE posted_by = ?");
            jobStmt.setInt(1, posterId);
            ResultSet jobRs = jobStmt.executeQuery();

            StringBuilder result = new StringBuilder("[");
            boolean jobFirst = true;

            while (jobRs.next()) {
                int jobId = jobRs.getInt("job_id");
                String title = jobRs.getString("title");
                String company = jobRs.getString("company");

                if (!jobFirst) result.append(",");
                result.append("{")
                      .append("\"job_id\":").append(jobId).append(",")
                      .append("\"job_title\":\"").append(title).append("\",")
                      .append("\"company\":\"").append(company).append("\",");

                // Fetch applicants for this job
                PreparedStatement appStmt = conn.prepareStatement(
                    "SELECT aj.application_id, aj.user_id, aj.status, u.username " +
                    "FROM applied_jobs aj JOIN users u ON aj.user_id = u.user_id " +
                    "WHERE aj.job_id = ?");
                appStmt.setInt(1, jobId);
                ResultSet appRs = appStmt.executeQuery();

                result.append("\"applicants\":[");
                boolean appFirst = true;
                while (appRs.next()) {
                    if (!appFirst) result.append(",");
                    result.append("{")
                          .append("\"application_id\":").append(appRs.getInt("application_id")).append(",")
                          .append("\"user_id\":").append(appRs.getInt("user_id")).append(",")
                          .append("\"username\":\"").append(appRs.getString("username")).append("\",")
                          .append("\"status\":\"").append(appRs.getString("status")).append("\"")
                          .append("}");
                    appFirst = false;
                }
                result.append("]}");
                jobFirst = false;
            }

            result.append("]");
            out.println(result.toString());

        } catch (Exception e) {
            e.printStackTrace();
            out.println("[]");
        }
    }

    private void handleApproveApplication(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        String userIdParam = request.getParameter("user_id");
        String jobIdParam = request.getParameter("job_id");

        if (userIdParam == null || jobIdParam == null || userIdParam.isEmpty() || jobIdParam.isEmpty()) {
            out.println("{\"error\": \"Missing user_id or job_id.\"}");
            return;
        }

        int userId = Integer.parseInt(userIdParam);
        int jobId = Integer.parseInt(jobIdParam);

        try {
            Connection conn = DBConnection.getConnection();
            if (conn == null) {
                out.println("{\"error\": \"Database connection failed.\"}");
                return;
            }
            PreparedStatement rejectOthers = conn.prepareStatement(
                "UPDATE applied_jobs SET status = 'Rejected' WHERE job_id = ?"
            );
            rejectOthers.setInt(1, jobId);
            rejectOthers.executeUpdate();

            // Now, set selected applicant to Accepted
            PreparedStatement acceptOne = conn.prepareStatement(
                "UPDATE applied_jobs SET status = 'Accepted' WHERE job_id = ? AND user_id = ?"
            );
            acceptOne.setInt(1, jobId);
            acceptOne.setInt(2, userId);
            int updated = acceptOne.executeUpdate();

            if (updated > 0) {
                out.println("{\"message\": \"Applicant approved successfully.\"}");
            } else {
                out.println("{\"error\": \"Failed to approve applicant.\"}");
            }

            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
            out.println("{\"error\": \"Error during approval: " + e.getMessage() + "\"}");
        }
    }

    private void handleSignup(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        String fullName = request.getParameter("full_name");
        String username = request.getParameter("username");
        String email = request.getParameter("email");
        String password = request.getParameter("password");

        try {
            Connection conn = DBConnection.getConnection();
            if (conn == null) {
                out.println("{\"error\": \"Database connection failed.\"}");
                return;
            }

            // Check if email or username is already registered
            PreparedStatement checkUser = conn.prepareStatement(
                    "SELECT * FROM users WHERE email = ? OR username = ?");
            checkUser.setString(1, email);
            checkUser.setString(2, username);
            ResultSet rs = checkUser.executeQuery();

            if (rs.next()) {
                out.println("{\"error\": \"Username or Email already taken.\"}");
                return;
            }

            // Insert new user
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO users (full_name, username, email, password) VALUES (?, ?, ?, ?)");
            stmt.setString(1, fullName);
            stmt.setString(2, username);
            stmt.setString(3, email);
            stmt.setString(4, password);
            stmt.executeUpdate();
            conn.close();

            out.println("{\"message\": \"Signup successful! You can now log in.\"}");
        } catch (Exception e) {
            e.printStackTrace();
            out.println("{\"error\": \"Error signing up: " + e.getMessage() + "\"}");
        }
    }

    private void handleLogin(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        String userInput = request.getParameter("user_input"); // Can be email or username
        String password = request.getParameter("password");

        try {
            Connection conn = DBConnection.getConnection();
            if (conn == null) {
                out.println("{\"error\": \"Database connection failed.\"}");
                return;
            }

            // Check for either username or email
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT user_id, full_name, username, email, password FROM users WHERE email = ? OR username = ?");
            stmt.setString(1, userInput);
            stmt.setString(2, userInput);
            ResultSet rs = stmt.executeQuery(); 	

            if (rs.next()) {
                String storedPassword = rs.getString("password");
                if (storedPassword.equals(password)) {
                    HttpSession session = request.getSession();
                    session.setAttribute("user_id", rs.getInt("user_id"));
                    session.setAttribute("full_name", rs.getString("full_name"));
                    session.setAttribute("username", rs.getString("username"));
                    session.setAttribute("email", rs.getString("email"));

                    out.println("{\"message\": \"Login successful!\", \"user_id\": " + rs.getInt("user_id") + "}");
                } else {
                    out.println("{\"error\": \"Invalid password.\"}");
                }
            } else {
                out.println("{\"error\": \"User not found.\"}");
            }
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
            out.println("{\"error\": \"Error logging in: " + e.getMessage() + "\"}");
        }
    }
    private void handleApplyJob(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        String userIdParam = request.getParameter("user_id");
        String jobIdParam = request.getParameter("job_id");

        System.out.println("Received user_id: " + userIdParam);
        System.out.println("Received job_id: " + jobIdParam);

        // Ensure parameters are not null
        if (userIdParam == null || jobIdParam == null || userIdParam.isEmpty() || jobIdParam.isEmpty()) {
            out.println("{\"error\": \"Missing user_id or job_id.\"}");
            return;
        }

        int userId = Integer.parseInt(userIdParam);
        int jobId = Integer.parseInt(jobIdParam);

        try {
            Connection conn = DBConnection.getConnection();
            if (conn == null) {
                out.println("{\"error\": \"Database connection failed.\"}");
                System.out.println("Database connection failed.");
                return;
            }

            // Check if user already applied
            PreparedStatement checkStmt = conn.prepareStatement(
                "SELECT * FROM applied_jobs WHERE user_id = ? AND job_id = ?"
            );
            checkStmt.setInt(1, userId);
            checkStmt.setInt(2, jobId);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                out.println("{\"error\": \"You have already applied for this job.\"}");
                conn.close();
                return;
            }

            // Fetch job details
            PreparedStatement jobStmt = conn.prepareStatement(
                "SELECT title, company FROM jobs WHERE job_id = ?"
            );
            jobStmt.setInt(1, jobId);
            ResultSet jobRs = jobStmt.executeQuery();

            if (jobRs.next()) {
                String title = jobRs.getString("title");
                String company = jobRs.getString("company");

                // Insert into applied_jobs
                PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO applied_jobs (user_id, job_id, job_title, company, status) VALUES (?, ?, ?, ?, ?)"
                );
                stmt.setInt(1, userId);
                stmt.setInt(2, jobId);
                stmt.setString(3, title);
                stmt.setString(4, company);
                stmt.setString(5, "Pending");

                int rowsInserted = stmt.executeUpdate();
                System.out.println("Rows inserted: " + rowsInserted);

                if (rowsInserted > 0) {
                    out.println("{\"message\": \"Applied successfully!\"}");
                } else {
                    out.println("{\"error\": \"Failed to apply for job.\"}");
                }

                conn.close();
            } else {
                out.println("{\"error\": \"Job not found.\"}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            out.println("{\"error\": \"Error applying for job: " + e.getMessage() + "\"}");
            System.out.println("Exception: " + e.getMessage());
        }
    }

    private void handleDashboard(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("user_id") == null) {
            out.println("{\"error\": \"Unauthorized access.\"}");
            return;
        }

        int userId = (int) session.getAttribute("user_id");

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) {
                out.println("{\"error\": \"Database connection failed.\"}");
                return;
            }

            // Determine role
            PreparedStatement checkPoster = conn.prepareStatement("SELECT COUNT(*) FROM jobs WHERE posted_by = ?");
            checkPoster.setInt(1, userId);
            ResultSet roleRs = checkPoster.executeQuery();

            boolean isPoster = false;
            if (roleRs.next() && roleRs.getInt(1) > 0) {
                isPoster = true;
            }

            if (isPoster) {
                PreparedStatement jobStmt = conn.prepareStatement("SELECT COUNT(*) FROM jobs WHERE posted_by = ?");
                jobStmt.setInt(1, userId);
                ResultSet jobsRs = jobStmt.executeQuery();
                jobsRs.next();
                int jobsPosted = jobsRs.getInt(1);

                PreparedStatement appsStmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM applied_jobs aj JOIN jobs j ON aj.job_id = j.job_id WHERE j.posted_by = ?");
                appsStmt.setInt(1, userId);
                ResultSet appsRs = appsStmt.executeQuery();
                appsRs.next();
                int totalApplicants = appsRs.getInt(1);

                PreparedStatement accStmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM applied_jobs aj JOIN jobs j ON aj.job_id = j.job_id " +
                    "WHERE j.posted_by = ? AND aj.status = 'Accepted'");
                accStmt.setInt(1, userId);
                ResultSet accRs = accStmt.executeQuery();
                accRs.next();
                int totalAccepted = accRs.getInt(1);

                out.printf("{\"role\":\"poster\",\"jobs_posted\":%d,\"total_applicants\":%d,\"total_accepted\":%d}",
                        jobsPosted, totalApplicants, totalAccepted);

            } else {
                PreparedStatement appliedStmt = conn.prepareStatement(
                        "SELECT status, COUNT(*) FROM applied_jobs WHERE user_id = ? GROUP BY status");
                appliedStmt.setInt(1, userId);
                ResultSet rs = appliedStmt.executeQuery();

                int pending = 0, accepted = 0, rejected = 0, total = 0;
                while (rs.next()) {
                    String status = rs.getString("status");
                    int count = rs.getInt(2);
                    total += count;
                    if ("Pending".equalsIgnoreCase(status)) pending = count;
                    else if ("Accepted".equalsIgnoreCase(status)) accepted = count;
                    else if ("Rejected".equalsIgnoreCase(status)) rejected = count;
                }

                out.printf("{\"role\":\"applicant\",\"applied\":%d,\"pending\":%d,\"accepted\":%d,\"rejected\":%d}",
                        total, pending, accepted, rejected);
            }

        } catch (Exception e) {
            e.printStackTrace();
            out.println("{\"error\": \"Error generating dashboard data.\"}");
        }
    }


    private void handleGetAppliedJobs(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        String userIdParam = request.getParameter("user_id");
        System.out.println("Fetching applied jobs for user_id: " + userIdParam);

        if (userIdParam == null || userIdParam.isEmpty()) {
            out.println("{\"error\": \"User ID is required.\"}");
            System.out.println("Error: User ID is missing.");
            return;
        }

        int userId = Integer.parseInt(userIdParam);
        
        try {
            Connection conn = DBConnection.getConnection();
            if (conn == null) {
                out.println("{\"error\": \"Database connection failed.\"}");
                return;
            }

            PreparedStatement stmt = conn.prepareStatement(
                "SELECT job_id, job_title, company, status FROM applied_jobs WHERE user_id = ?"
            );
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            StringBuilder json = new StringBuilder("{ \"jobs\": [");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                json.append("{ \"job_id\": ").append(rs.getInt("job_id"))
                    .append(", \"title\": \"").append(rs.getString("job_title"))
                    .append("\", \"company\": \"").append(rs.getString("company"))
                    .append("\", \"status\": \"").append(rs.getString("status"))
                    .append("\" }");
                first = false;
            }
            json.append("] }");

            System.out.println("Applied Jobs JSON Response: " + json.toString()); // Debug Log

            out.println(json.toString());
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
            out.println("{\"error\": \"Error fetching applied jobs.\"}");
            System.out.println("Exception: " + e.getMessage());
        }
    }


    private void handleProfileUpdate(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        int userId = Integer.parseInt(request.getParameter("user_id"));
        String newFullName = request.getParameter("full_name");
        String newUsername = request.getParameter("username");
        String newEmail = request.getParameter("email");

        try {
            Connection conn = DBConnection.getConnection();
            if (conn == null) {
                out.println("{\"error\": \"Database connection failed.\"}");
                return;
            }

            // Update user details
            PreparedStatement stmt = conn.prepareStatement(
                "UPDATE users SET full_name = ?, username = ?, email = ? WHERE user_id = ?");
            stmt.setString(1, newFullName);
            stmt.setString(2, newUsername);
            stmt.setString(3, newEmail);
            stmt.setInt(4, userId);

            int rowsUpdated = stmt.executeUpdate();
            conn.close();

            if (rowsUpdated > 0) {
                out.println("{\"message\": \"Profile updated successfully!\"}");
            } else {
                out.println("{\"error\": \"Failed to update profile.\"}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            out.println("{\"error\": \"Error updating profile: " + e.getMessage() + "\"}");
        }
    }

    private void handleLogout(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        out.println("{\"message\": \"Logged out successfully.\"}");
    }
}
