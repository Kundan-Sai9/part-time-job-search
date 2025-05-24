package mini;

import java.io.*;
import java.sql.*;
import javax.servlet.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

@WebServlet("/AppliedJobsServlet")
public class AppliedJobsServlet extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user_id") == null) {
            response.sendRedirect("login.html");
            return;
        }

        String action = request.getParameter("action");

        try (Connection conn = DBConnection.getConnection()) {
            if ("viewApplications".equals(action)) {
                int jobId = Integer.parseInt(request.getParameter("jobId"));

                response.setContentType("application/json");
                PrintWriter out = response.getWriter();

                PreparedStatement stmt = conn.prepareStatement(
                    "SELECT aj.application_id, aj.job_id, aj.status, u.username " +
                    "FROM applied_jobs aj JOIN users u ON aj.user_id = u.user_id " +
                    "WHERE aj.job_id = ?");
                stmt.setInt(1, jobId);
                ResultSet rs = stmt.executeQuery();

                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    json.append("{");
                    json.append("\"id\":").append(rs.getInt("application_id")).append(",");
                    json.append("\"jobId\":").append(rs.getInt("job_id")).append(",");
                    json.append("\"status\":\"").append(rs.getString("status")).append("\",");
                    json.append("\"username\":\"").append(rs.getString("username")).append("\"");
                    json.append("}");
                    first = false;
                }
                json.append("]");
                out.print(json.toString());
                return;

            } else if ("acceptApplicant".equals(action)) {
                int appId = Integer.parseInt(request.getParameter("applicationId"));
                int jobId = Integer.parseInt(request.getParameter("jobId"));

                PreparedStatement checkAccepted = conn.prepareStatement(
                	    "SELECT COUNT(*) FROM applied_jobs WHERE job_id = ? AND status = 'Accepted'");
                	checkAccepted.setInt(1, jobId);
                	ResultSet rs = checkAccepted.executeQuery();
                	rs.next();

                	if (rs.getInt(1) > 0) {
                	    response.setContentType("application/json");
                	    response.setCharacterEncoding("UTF-8");
                	    PrintWriter out = response.getWriter();
                	    out.print("{\"error\": \"An applicant has already been accepted for this job.\"}");
                	    return;
                	}

                // Reject all others
                PreparedStatement rejectOthers = conn.prepareStatement(
                    "UPDATE applied_jobs SET status='Rejected' WHERE job_id=? AND application_id != ?");
                rejectOthers.setInt(1, jobId);
                rejectOthers.setInt(2, appId);
                rejectOthers.executeUpdate();

                // Accept the selected applicant
                PreparedStatement acceptOne = conn.prepareStatement(
                    "UPDATE applied_jobs SET status='Accepted' WHERE application_id=?");
                acceptOne.setInt(1, appId);
                acceptOne.executeUpdate();
                
                PreparedStatement updateJobStatus = conn.prepareStatement(
                     "UPDATE jobs SET job_status = 'approved' WHERE job_id = ?");
                updateJobStatus.setInt(1, jobId);
                updateJobStatus.executeUpdate();

                // Send JSON response instead of redirect
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                PrintWriter out = response.getWriter();
                out.print("{\"success\": true, \"message\": \"Applicant accepted\"}");
                return;
                
                
            }else if ("getAllPostedApplications".equals(action)) {
                int posterId = Integer.parseInt(session.getAttribute("user_id").toString());

                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                PrintWriter out = response.getWriter();

                PreparedStatement stmt = conn.prepareStatement(
                    "SELECT j.job_id, j.title, aj.application_id, aj.status, u.username " +
                    "FROM jobs j " +
                    "JOIN applied_jobs aj ON j.job_id = aj.job_id " +
                    "JOIN users u ON aj.user_id = u.user_id " +
                    "WHERE j.posted_by = ? " +
                    "ORDER BY j.job_id, aj.application_id");
                stmt.setInt(1, posterId);
                ResultSet rs = stmt.executeQuery();

                int currentJobId = -1;
                //String currentJobTitle = "";
                StringBuilder resultJson = new StringBuilder();
                resultJson.append("[");

                boolean firstJob = true;
                boolean firstApplicant = true;

                while (rs.next()) {
                    int jobId = rs.getInt("job_id");
                    String jobTitle = rs.getString("title");

                    if (jobId != currentJobId) {
                        if (!firstJob) {
                            resultJson.append("]}"); // Close previous job object
                        }

                        if (!firstJob) resultJson.append(",");
                        resultJson.append("{");
                        resultJson.append("\"job_id\":").append(jobId).append(",");
                        resultJson.append("\"job_title\":\"").append(jobTitle.replace("\"", "\\\"")).append("\",");
                        resultJson.append("\"applicants\":[");
                        firstApplicant = true;
                        firstJob = false;
                        currentJobId = jobId;
                    }

                    if (!firstApplicant) resultJson.append(",");
                    resultJson.append("{");
                    resultJson.append("\"application_id\":").append(rs.getInt("application_id")).append(",");
                    resultJson.append("\"username\":\"").append(rs.getString("username").replace("\"", "\\\"")).append("\",");
                    resultJson.append("\"status\":\"").append(rs.getString("status")).append("\"");
                    resultJson.append("}");
                    firstApplicant = false;
                }

                if (!firstJob) {
                    resultJson.append("]}"); // Close last job object
                }

                resultJson.append("]");
                out.print(resultJson.toString());
                return;
            }else if ("getApprovedJobs".equals(action)) {
                int userId = Integer.parseInt(session.getAttribute("user_id").toString());

                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                PrintWriter out = response.getWriter();

                PreparedStatement stmt = conn.prepareStatement(
                    "SELECT j.title, j.company, j.location, j.salary " +
                    "FROM applied_jobs aj " +
                    "JOIN jobs j ON aj.job_id = j.job_id " +
                    "WHERE aj.user_id = ? AND aj.status = 'Accepted' AND j.job_status = 'approved'");
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();

                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    json.append("{");
                    json.append("\"title\":\"").append(rs.getString("title").replace("\"", "\\\"")).append("\",");
                    json.append("\"company\":\"").append(rs.getString("company").replace("\"", "\\\"")).append("\",");
                    json.append("\"location\":\"").append(rs.getString("location").replace("\"", "\\\"")).append("\",");
                    json.append("\"salary\":").append(rs.getInt("salary"));
                    json.append("}");
                    first = false;
                }
                json.append("]");
                out.print(json.toString());
                return;
            }


            else {
                // Default: Show current user's applied jobs
                int userId = Integer.parseInt(session.getAttribute("user_id").toString());

                response.setContentType("text/html");
                PrintWriter out = response.getWriter();

                out.println("<html><head><title>Applied Jobs</title>");
                out.println("<style>");
                out.println("body { font-family: Arial, sans-serif; padding: 20px; background-color: #f9f9f9; }");
                out.println(".job-card { background: #fff; padding: 15px; margin-bottom: 20px; border-radius: 10px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }");
                out.println("h2 { color: #333; }");
                out.println("p { margin: 5px 0; }");
                out.println(".no-jobs { font-size: 18px; color: #666; }");
                out.println("</style></head><body>");
                out.println("<h2>Your Applied Jobs</h2>");

                PreparedStatement stmt = conn.prepareStatement(
                    "SELECT job_title, company, status, applied_at FROM applied_jobs WHERE user_id = ?");
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();

                boolean found = false;
                while (rs.next()) {
                    found = true;
                    out.println("<div class='job-card'>");
                    out.println("<h3>" + rs.getString("job_title") + "</h3>");
                    out.println("<p><strong>Company:</strong> " + rs.getString("company") + "</p>");
                    out.println("<p><strong>Status:</strong> " + rs.getString("status") + "</p>");
                    out.println("<p><strong>Applied At:</strong> " + rs.getTimestamp("applied_at") + "</p>");
                    out.println("</div>");
                }

                if (!found) {
                    out.println("<p class='no-jobs'>You haven't applied to any jobs yet.</p>");
                }

                out.println("</body></html>");
            }
        } catch (Exception e) {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            PrintWriter out = response.getWriter();
            String safeMessage = e.getMessage().replace("\"", "\\\"").replace("\n", "").replace("\r", "");
            out.print("{\"error\": \"" + safeMessage + "\"}");
        }

    }
}
