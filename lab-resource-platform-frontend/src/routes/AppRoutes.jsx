
import { Routes, Route } from 'react-router-dom';

import RootLayout from '../layouts/RootLayout';
import HomePage from '../pages/HomePage';
import DashboardPage from '../pages/DashboardPage';
import LoginPage from '../pages/LoginPage';
import RegisterPage from '../pages/RegisterPage';
import ForgotPasswordPage from '../pages/ForgotPasswordPage';
import ResetPasswordPage from '../pages/ResetPasswordPage';
import OAuthCallbackPage from '../pages/OAuthCallbackPage';
import NotFoundPage from '../pages/NotFoundPage';

export const AppRoutes = () => {
  return (
    <Routes>
      <Route path="/" element={<RootLayout />}>
        <Route index element={<HomePage />} />

        <Route path="dashboard" element={<DashboardPage />} />

        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />

        <Route
          path="forgot-password"
          element={<ForgotPasswordPage />}
        />

        <Route
          path="reset-password"
          element={<ResetPasswordPage />}
        />

        {/* OAuth callback routes */}
        <Route
          path="oauth/callback"
          element={<OAuthCallbackPage />}
        />

        <Route
          path="oauth2/callback"
          element={<OAuthCallbackPage />}
        />

        <Route
          path="auth/callback"
          element={<OAuthCallbackPage />}
        />

        {/* Catch-all route */}
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
};

export default AppRoutes;
