import { Navigate, Route, Routes, useParams } from 'react-router-dom'
import { HrLayout } from '../layouts/HrLayout'
import { PortalLayout } from '../layouts/PortalLayout'
import { AdminPage } from '../pages/hr/AdminPage'
import { CandidateDetailPage } from '../pages/hr/CandidateDetailPage'
import { CandidatesPage } from '../pages/hr/CandidatesPage'
import { DashboardPage } from '../pages/hr/DashboardPage'
import { LoginPage } from '../pages/hr/LoginPage'
import { NocDetailPage } from '../pages/hr/NocDetailPage'
import { NocPage } from '../pages/hr/NocPage'
import { OfferDetailPage } from '../pages/hr/OfferDetailPage'
import { OffersPage } from '../pages/hr/OffersPage'
import { PortalDetailsPage } from '../pages/portal/PortalDetailsPage'
import { PortalDocumentsPage } from '../pages/portal/PortalDocumentsPage'
import { PortalOfferPage } from '../pages/portal/PortalOfferPage'
import { PortalOverviewPage } from '../pages/portal/PortalOverviewPage'
import { PortalReviewPage } from '../pages/portal/PortalReviewPage'
import { NocSignPage } from '../pages/NocSignPage'
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
          <Route path="/noc" element={<NocPage />} />
          <Route path="/noc/record/:nocId" element={<NocDetailPage />} />
          {/* The server refuses these APIs to non-admins; hiding the page as
              well just avoids showing a screen that cannot load. */}
          <Route path="/admin" element={<AdminPage />} />
          {/* The settings used to be two sidebar entries; anyone holding an old
              link still lands in the right place. */}
          <Route path="/admin/*" element={<Navigate to="/admin" replace />} />
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

      {/* NDA + NOC recipients are not candidates and never sign in: the token
          in the link is the whole credential, so this sits on its own. */}
      <Route path="/noc/:token" element={<NocSignPage />} />

      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
