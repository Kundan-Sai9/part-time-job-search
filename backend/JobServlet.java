package mini;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.*;
import java.sql.*;

@WebServlet("/JobServlet")
public class JobServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        String action = request.getParameter("action");

        // ✅ Get jobs posted by a specific user
        if ("getJobsByUser".equals(action)) {
            int userId = Integer.parseInt(request.getParameter("user_id"));

            try (Connection conn = DBConnection.getConnection()) {
                PreparedStatement stmt = conn.prepareStatement("SELECT * FROM jobs WHERE posted_by = ?");
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();

                out.println("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) out.println(",");
                    out.printf("{\"job_id\":%d,\"title\":\"%s\",\"company\":\"%s\",\"location\":\"%s\",\"salary\":\"%s\",\"description\":\"%s\",\"posted_by\":%d}",
                        rs.getInt("job_id"),
                        escapeJson(rs.getString("title")),
                        escapeJson(rs.getString("company")),
                        escapeJson(rs.getString("location")),
                        escapeJson(rs.getString("salary")),
                        escapeJson(rs.getString("description")),
                        rs.getInt("posted_by")
                    );
                    first = false;
                }
                out.println("]");

            } catch (Exception e) {
                e.printStackTrace();
                out.println("{\"error\": \"Error loading jobs.\"}");
            }
            return;
        }

        // ✅ Search or Get All Jobs
        String searchQuery = request.getParameter("search");
        boolean isSearchRequest = (searchQuery != null && !searchQuery.trim().isEmpty());

        try (Connection conn = DBConnection.getConnection()) {
            String sql = "SELECT j.job_id, j.title, j.company, j.location, j.salary, j.description, j.posted_by " +
                         "FROM jobs j " +
                         "WHERE NOT EXISTS (SELECT 1 FROM applied_jobs a WHERE a.job_id = j.job_id AND a.status = 'Accepted')";

            PreparedStatement stmt;

            if (isSearchRequest) {
                sql += " AND (LOWER(title) LIKE ? OR LOWER(company) LIKE ? OR LOWER(location) LIKE ? OR LOWER(salary) LIKE ?)";
                stmt = conn.prepareStatement(sql);
                String pattern = "%" + searchQuery.toLowerCase() + "%";
                for (int i = 1; i <= 4; i++) {
                    stmt.setString(i, pattern);
                }
            } else {
                stmt = conn.prepareStatement(sql);
            }

            ResultSet rs = stmt.executeQuery();

            out.println("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) out.println(",");
                out.printf("{\"job_id\": %d, \"title\": \"%s\", \"company\": \"%s\", \"location\": \"%s\", \"salary\": \"%s\", \"description\": \"%s\", \"posted_by\": %d}",
                    rs.getInt("job_id"),
                    escapeJson(rs.getString("title")),
                    escapeJson(rs.getString("company")),
                    escapeJson(rs.getString("location")),
                    escapeJson(rs.getString("salary")),
                    escapeJson(rs.getString("description")),
                    rs.getInt("posted_by")
                );
                first = false;
            }
            out.println("]");

        } catch (Exception e) {
            e.printStackTrace();
            out.println("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        String action = request.getParameter("action");

        // ✅ Update a posted job
        if ("updateJob".equals(action)) {
            try {
                int jobId = Integer.parseInt(request.getParameter("job_id"));
                String title = request.getParameter("title");
                String company = request.getParameter("company");
                String location = request.getParameter("location");
                String salary = request.getParameter("salary");
                String description = request.getParameter("description");

                try (Connection conn = DBConnection.getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE jobs SET title=?, company=?, location=?, salary=?, description=? WHERE job_id=?");
                    stmt.setString(1, title);
                    stmt.setString(2, company);
                    stmt.setString(3, location);
                    stmt.setString(4, salary);
                    stmt.setString(5, description);
                    stmt.setInt(6, jobId);

                    int rows = stmt.executeUpdate();
                    if (rows > 0) {
                        out.println("{\"message\": \"Job updated successfully.\"}");
                    } else {
                        out.println("{\"error\": \"Job not found or not updated.\"}");
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                out.println("{\"error\": \"Failed to update job.\"}");
            }
            return;
        }

        // ✅ Apply to job
        try {
            int jobId = Integer.parseInt(request.getParameter("job_id"));
            int userId = Integer.parseInt(request.getParameter("user_id"));

            if (jobId == 0 || userId == 0) {
                out.println("{\"error\": \"Invalid input data.\"}");
                return;
            }

            try (Connection conn = DBConnection.getConnection()) {
                // 🔒 Check if user is the poster
                PreparedStatement posterCheck = conn.prepareStatement("SELECT posted_by, title, company FROM jobs WHERE job_id = ?");
                posterCheck.setInt(1, jobId);
                ResultSet rs = posterCheck.executeQuery();

                if (!rs.next()) {
                    out.println("{\"error\": \"Job not found.\"}");
                    return;
                }

                int posterId = rs.getInt("posted_by");
                if (posterId == userId) {
                    out.println("{\"error\": \"You cannot apply to your own job.\"}");
                    return;
                }

                String title = rs.getString("title");
                String company = rs.getString("company");

                // Check if already applied
                PreparedStatement checkStmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM applied_jobs WHERE user_id = ? AND job_id = ?");
                checkStmt.setInt(1, userId);
                checkStmt.setInt(2, jobId);
                ResultSet checkRs = checkStmt.executeQuery();

                if (checkRs.next() && checkRs.getInt(1) > 0) {
                    out.println("{\"error\": \"You have already applied for this job.\"}");
                    return;
                }

                // Insert application
                PreparedStatement insertStmt = conn.prepareStatement(
                        "INSERT INTO applied_jobs (user_id, job_id, job_title, company, status, applied_at) " +
                        "VALUES (?, ?, ?, ?, 'Pending', SYSTIMESTAMP)");
                insertStmt.setInt(1, userId);
                insertStmt.setInt(2, jobId);
                insertStmt.setString(3, title);
                insertStmt.setString(4, company);
                insertStmt.executeUpdate();

                out.println("{\"message\": \"Application submitted successfully!\"}");
            }

        } catch (Exception e) {
            e.printStackTrace();
            out.println("{\"error\": \"Error submitting application.\"}");
        }
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
