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
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        // Check if user is logged in
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            response.sendRedirect("login.html");
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
            
            out.println("<p style='color:red;'>All fields are required.</p>");
            return;
        }

        if (salaryStr == null || salaryStr.trim().isEmpty()) {
            out.println("<p style='color:red;'>Salary is required.</p>");
            return;
        }

        try {
            int salary = Integer.parseInt(salaryStr);

            Connection conn = DBConnection.getConnection();
            if (conn == null) {
                out.println("<p style='color:red;'>Database connection failed.</p>");
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
                response.sendRedirect("home.html"); // Redirect after success
            } else {
                out.println("<p style='color:red;'>Failed to post job.</p>");
            }
        } catch (NumberFormatException e) {
            out.println("<p style='color:red;'>Invalid salary format.</p>");
        } catch (Exception e) {
            out.println("<p style='color:red;'>Error: " + e.getMessage() + "</p>");
        }
    }
}
