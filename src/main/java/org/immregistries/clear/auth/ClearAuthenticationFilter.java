package org.immregistries.clear.auth;

import java.io.IOException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.immregistries.clear.ClearConfig;

public class ClearAuthenticationFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No-op
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (!ClearConfig.AUTH_ENABLED || isPublicPath(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        SessionUser sessionUser = ClearAuthSessionSupport.getSessionUser(httpRequest);
        if (sessionUser != null && sessionUser.isAllowed()) {
            chain.doFilter(request, response);
            return;
        }

        ClearAuthSessionSupport.saveOriginalRequest(httpRequest);
        ClearAuthSessionSupport.redirectToHubLogin(httpRequest, httpResponse);
    }

    @Override
    public void destroy() {
        // No-op
    }

    private boolean isPublicPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        return "/login".equals(path)
                || "/logout".equals(path)
                || "/access-denied".equals(path)
                || "/favicon.ico".equals(path)
                || path.startsWith("/css/")
                || path.startsWith("/images/");
    }
}
