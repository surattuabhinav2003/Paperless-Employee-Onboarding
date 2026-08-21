import { Navigate, Route, Routes, useParams } from 'react-router-dom'
import { HrLayout } from '../layouts/HrLayout'
import { PortalLayout } from '../layouts/PortalLayout'
import { CandidateDetailPage } from '../pages/hr/CandidateDetailPage'
import { CandidatesPage } from '../pages/hr/CandidatesPage'
import { DashboardPage } from '../pages/hr/DashboardPage'
import { LoginPage } from '../pages/hr/LoginPage'
import { OfferDetailPage } from '../pages/hr/OfferDetailPage'
import { OffersPage } from '../pages/hr/OffersPage'
import { PortalDetailsPage } from '../pages/portal/PortalDetailsPage'
import { PortalDocumentsPage } from '../pages/portal/PortalDocumentsPage'
import { PortalOfferPage } from '../pages/portal/PortalOfferPage'
import { PortalOverviewPage } from '../pages/portal/PortalOverviewPage'
import { PortalReviewPage } from '../pages/portal/PortalReviewPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { ProtectedRoute } from './ProtectedRoute'

/** The invitation email links to /upload/:token; it lands on the portal overview. */
function UploadEntryRedirect() {
  const { token } = useParams()
  return <Navigate to={`/portal/${token}`} replace />
}

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      {/* HR side */}
      <Route element={<ProtectedRoute />}>
        <Route element={<HrLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/candidates" element={<CandidatesPage />} />
          <Route path="/candidates/:candidateId" element={<CandidateDetailPage />} />
          <Route path="/offers" element={<OffersPage />} />
          <Route path="/offers/:candidateId" element={<OfferDetailPage />} />
        </Route>
      </Route>

      {/* Candidate side - one secure link for the whole journey */}
      <Route path="/upload/:token" element={<UploadEntryRedirect />} />
      <Route path="/portal/:token" element={<PortalLayout />}>
        <Route index element={<PortalOverviewPage />} />
        <Route path="details" element={<PortalDetailsPage />} />
        <Route path="documents" element={<PortalDocumentsPage />} />
        <Route path="review" element={<PortalReviewPage />} />
        <Route path="offer" element={<PortalOfferPage />} />
      </Route>

      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
