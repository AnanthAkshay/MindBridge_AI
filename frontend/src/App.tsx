import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AppLayout } from "./layouts/AppLayout";
import { DashboardPage } from "./pages/DashboardPage";
import { ChatPage } from "./pages/ChatPage";
import { OnboardingPage } from "./pages/OnboardingPage";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { AnalyticsDashboardPage } from "./pages/AnalyticsDashboardPage";
import { SessionHistoryPage } from "./pages/SessionHistoryPage";
import { TherapistDashboardPage } from "./pages/TherapistDashboardPage";

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Public: Onboarding */}
        <Route path="/onboarding" element={<OnboardingPage />} />

        {/* Protected: Dashboard */}
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <AppLayout>
                <DashboardPage />
              </AppLayout>
            </ProtectedRoute>
          }
        />

        {/* Protected: Chat */}
        <Route
          path="/chat"
          element={
            <ProtectedRoute>
              <AppLayout>
                <ChatPage />
              </AppLayout>
            </ProtectedRoute>
          }
        />

        <Route
          path="/analytics"
          element={
            <ProtectedRoute>
              <AppLayout>
                <AnalyticsDashboardPage />
              </AppLayout>
            </ProtectedRoute>
          }
        />

        <Route
          path="/history"
          element={
            <ProtectedRoute>
              <AppLayout>
                <SessionHistoryPage />
              </AppLayout>
            </ProtectedRoute>
          }
        />

        <Route
          path="/therapist"
          element={
            <ProtectedRoute>
              <AppLayout>
                <TherapistDashboardPage />
              </AppLayout>
            </ProtectedRoute>
          }
        />

        {/* Catch-all */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
