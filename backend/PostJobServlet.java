package mini;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/PostJobServlet")
public class PostJobServlet extends HttpServlet {
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Change 1: Set content type to JSON instead of HTML
        response.setContentType("application/json"); // was "text/html"
        PrintWriter out = response.getWriter();

        // Check if user is logged in
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            // Change 2: Respond with JSON error instead of redirect
            out.print("{\"error\":true, \"message\":\"User not logged in.\"}");
            out.flush();
            return;
        }

        // Get user ID safely
        Object userIdObj = session.getAttribute("user_id");
        int userId = (userIdObj != null) ? Integer.parseInt(userIdObj.toString()) : 0;

        // Get form parameters
        String title = request.getParameter("title");
        String company = request.getParameter("company");
        String location = request.getParameter("location");
        String salaryStr = request.getParameter("salary");
        String description = request.getParameter("description");

        // Input validation
        if (title == null || title.trim().isEmpty() ||
            company == null || company.trim().isEmpty() ||
            location == null || location.trim().isEmpty() ||
            description == null || description.trim().isEmpty()) {
            
            // Change 2: JSON error instead of HTML
            out.print("{\"error\":true, \"message\":\"All fields are required.\"}");
            out.flush();
            return;
        }

        if (salaryStr == null || salaryStr.trim().isEmpty()) {
            // Change 2: JSON error
            out.print("{\"error\":true, \"message\":\"Salary is required.\"}");
            out.flush();
            return;
        }

        try {
            int salary = Integer.parseInt(salaryStr);

            Connection conn = DBConnection.getConnection();
            if (conn == null) {
                // Change 2: JSON error
                out.print("{\"error\":true, \"message\":\"Database connection failed.\"}");
                out.flush();
                return;
            }

            // Ensure table structure is correct
            String sql = "INSERT INTO jobs (title, company, location, salary, description, posted_by, job_status) VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, title);
            stmt.setString(2, company);
            stmt.setString(3, location);
            stmt.setInt(4, salary);
            stmt.setString(5, description);
            stmt.setInt(6, userId); 
            stmt.setString(7, "pending");

            int rows = stmt.executeUpdate();
            stmt.close();
            conn.close();

            if (rows > 0) {
                // Change 3: JSON success message instead of redirect
                out.print("{\"error\":false, \"message\":\"Job posted successfully!\"}");
            } else {
                // Change 2: JSON error
                out.print("{\"error\":true, \"message\":\"Failed to post job.\"}");
            }
            out.flush();
        } catch (NumberFormatException e) {
            // Change 2: JSON error
            out.print("{\"error\":true, \"message\":\"Invalid salary format.\"}");
            out.flush();
        } catch (Exception e) {
            // Change 2: JSON error
            out.print("{\"error\":true, \"message\":\"Error: " + e.getMessage() + "\"}");
            out.flush();
        }
    }
}
