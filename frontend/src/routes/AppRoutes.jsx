import { Suspense, lazy } from 'react'
import { Navigate, Route, Routes, useParams } from 'react-router-dom'
import { LoadingState } from '../components/ui/Spinner'
import { LoginPage } from '../pages/hr/LoginPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { ProtectedRoute } from './ProtectedRoute'

/*
 * Every screen past the front door is split into its own chunk.
 *
 * Loaded together, they are a single large download that has to arrive, parse
 * and execute before the sign-in form will even paint - and most of that weight
 * is screens the person signing in is not going to open. The sign-in page and
 * the not-found page stay eager: one is the first thing almost every visit
 * needs, and the other has to work when nothing else does.
 *
 * A candidate opening their portal link never downloads the HR console, and an
 * HR user never downloads the portal.
 */
const HrLayout = lazy(() => import('../layouts/HrLayout').then((m) => ({ default: m.HrLayout })))
const PortalLayout = lazy(() => import('../layouts/PortalLayout').then((m) => ({ default: m.PortalLayout })))

const DashboardPage = lazy(() => import('../pages/hr/DashboardPage').then((m) => ({ default: m.DashboardPage })))
const CandidatesPage = lazy(() => import('../pages/hr/CandidatesPage').then((m) => ({ default: m.CandidatesPage })))
const CandidateDetailPage = lazy(() => import('../pages/hr/CandidateDetailPage').then((m) => ({ default: m.CandidateDetailPage })))
const OffersPage = lazy(() => import('../pages/hr/OffersPage').then((m) => ({ default: m.OffersPage })))
const OfferDetailPage = lazy(() => import('../pages/hr/OfferDetailPage').then((m) => ({ default: m.OfferDetailPage })))
const NocPage = lazy(() => import('../pages/hr/NocPage').then((m) => ({ default: m.NocPage })))
const NocDetailPage = lazy(() => import('../pages/hr/NocDetailPage').then((m) => ({ default: m.NocDetailPage })))
const AdminPage = lazy(() => import('../pages/hr/AdminPage').then((m) => ({ default: m.AdminPage })))

const PortalOverviewPage = lazy(() => import('../pages/portal/PortalOverviewPage').then((m) => ({ default: m.PortalOverviewPage })))
const PortalDetailsPage = lazy(() => import('../pages/portal/PortalDetailsPage').then((m) => ({ default: m.PortalDetailsPage })))
const PortalDocumentsPage = lazy(() => import('../pages/portal/PortalDocumentsPage').then((m) => ({ default: m.PortalDocumentsPage })))
const PortalReviewPage = lazy(() => import('../pages/portal/PortalReviewPage').then((m) => ({ default: m.PortalReviewPage })))
const PortalOfferPage = lazy(() => import('../pages/portal/PortalOfferPage').then((m) => ({ default: m.PortalOfferPage })))

const NocSignPage = lazy(() => import('../pages/NocSignPage').then((m) => ({ default: m.NocSignPage })))

/**
 * Emails link to /upload/:token, which is the candidate's front door.
 *
 * <p>Anything after the token is carried across: the offer email points at
 * /upload/:token/offer so the candidate lands on the letter itself rather than
 * the overview, and without the splat that deeper link matched no route at all
 * and fell through to Not Found.
 */
function UploadEntryRedirect() {
  const { token, '*': rest } = useParams()
  return <Navigate to={`/portal/${token}${rest ? `/${rest}` : ''}`} replace />
}

export function AppRoutes() {
  return (
    /* One boundary around the lot rather than one per route: a chunk arrives in
       a few milliseconds on any normal connection, so what matters is that the
       gap is never a blank page. */
    <Suspense fallback={<div className="cf-route-loading"><LoadingState label="Loading" /></div>}>
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
        <Route path="/upload/:token/*" element={<UploadEntryRedirect />} />
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
    </Suspense>
  )
}
