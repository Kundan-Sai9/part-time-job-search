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
        String searchQuery = request.getParameter("search");
        boolean isSearchRequest = (searchQuery != null && !searchQuery.trim().isEmpty());

        if (isSearchRequest) {
            response.setContentType("application/json");
        } else {
            response.setContentType("text/html");
        }

        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) {
                out.println(isSearchRequest ? "{\"error\":\"DB connection failed\"}" : "<p>Error: DB connection failed</p>");
                return;
            }

            String sql = "SELECT job_id, title, company, location, salary, description, u.email AS posted_by FROM jobs j " +
                    "JOIN users u ON j.posted_by = u.user_id";

            PreparedStatement stmt;

            if (isSearchRequest) {
                sql += " WHERE LOWER(title) LIKE ? OR LOWER(company) LIKE ? OR LOWER(location) LIKE ?";
                stmt = conn.prepareStatement(sql);
                String searchPattern = "%" + searchQuery.toLowerCase() + "%";
                stmt.setString(1, searchPattern);
                stmt.setString(2, searchPattern);
                stmt.setString(3, searchPattern);
            } else {
                stmt = conn.prepareStatement(sql);
            }

            ResultSet rs = stmt.executeQuery();

            if (isSearchRequest) {
                out.println("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) out.println(",");
                    out.print("  {");
                    out.printf("\"job_id\": %d, \"title\": \"%s\", \"company\": \"%s\", \"location\": \"%s\", \"salary\": \"%s\", \"description\": \"%s\", \"posted_by\": %s",
                            rs.getInt("job_id"),
                            rs.getString("title").replace("\"", "\\\""),
                            rs.getString("company").replace("\"", "\\\""),
                            rs.getString("location").replace("\"", "\\\""),
                            rs.getString("salary"),
                            rs.getString("description").replace("\"", "\\\""),
                            rs.getString("posted_by")
                    );

                    out.print("}");
                    first = false;
                }
                out.println("\n]");
            } else {
                while (rs.next()) {
                    out.println("<div class='card'>");
                    out.println("<h1>" + rs.getString("title") + "</h1>");
                    out.println("<p><strong>Company:</strong> " + rs.getString("company") + "</p>");
                    out.println("<p><strong>Location:</strong> " + rs.getString("location") + "</p>");
                    out.println("<p><strong>Salary:</strong> $" + rs.getString("salary") + "</p>");
                    out.println("<p>" + rs.getString("description") + "</p>");
                    out.println("<button onclick='applyForJob(" + rs.getInt("job_id") + ")'>Apply</button>");
                    out.println("</div>");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            out.println(isSearchRequest ? "{\"error\":\"" + e.getMessage() + "\"}" : "<p>Error: " + e.getMessage() + "</p>");
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        

        try {
            int jobId = Integer.parseInt(request.getParameter("job_id"));
            int userId = Integer.parseInt(request.getParameter("user_id"));

            if (jobId == 0 || userId == 0) {
                out.println("{\"error\": \"Invalid input data.\"}");
                return;
            }

            Connection conn = DBConnection.getConnection();
            if (conn == null) {
                out.println("{\"error\": \"Database connection failed.\"}");
                return;
            }
            
            String checkSql = "SELECT COUNT(*) FROM applied_jobs WHERE user_id = ? AND job_id = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setInt(1, userId);
            checkStmt.setInt(2, jobId);
            ResultSet checkRs = checkStmt.executeQuery();
            if (checkRs.next() && checkRs.getInt(1) > 0) {
                out.println("{\"error\": \"You have already applied for this job.\"}");
                conn.close();
                return;
            }

            PreparedStatement jobStmt = conn.prepareStatement("SELECT title, company FROM jobs WHERE job_id = ?");
            jobStmt.setInt(1, jobId);
            ResultSet rs = jobStmt.executeQuery();

            if (!rs.next()) {
                out.println("{\"error\": \"Job not found.\"}");
                return;
            }

            String title = rs.getString("title");
            String company = rs.getString("company");

            PreparedStatement insertStmt = conn.prepareStatement(
                    "INSERT INTO applied_jobs (user_id, job_id, job_title, company, status, applied_at) " +
                    "VALUES (?, ?, ?, ?, 'Pending', SYSTIMESTAMP)");
            insertStmt.setInt(1, userId);
            insertStmt.setInt(2, jobId);
            insertStmt.setString(3, title);
            insertStmt.setString(4, company);
            insertStmt.executeUpdate();

            conn.close();
            out.println("{\"message\": \"Application submitted successfully!\"}");

        } catch (Exception e) {
            e.printStackTrace();
            out.println("{\"error\": \"Error submitting application.\"}");
        }
    }
}
