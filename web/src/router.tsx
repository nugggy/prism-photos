import { createHashRouter, Navigate } from 'react-router-dom'
import { Layout } from './components/Layout'
import { RequireConnection, RequireLibrary } from './components/Guards'
import { SignIn } from './pages/SignIn'
import { ServerPicker } from './pages/ServerPicker'
import { Photos } from './pages/Photos'
import { Albums } from './pages/Albums'
import { AlbumDetail } from './pages/AlbumDetail'
import { MyAlbumDetail } from './pages/MyAlbumDetail'
import { Favourites } from './pages/Favourites'
import { Search } from './pages/Search'
import { Locked } from './pages/Locked'
import { Settings } from './pages/Settings'
import { Viewer } from './pages/Viewer'
import { Editor } from './pages/Editor'

const viewerRoute = { path: 'view/:itemId', element: <Viewer /> }

export const router = createHashRouter([
  { path: '/signin', element: <SignIn /> },
  {
    element: <RequireConnection />,
    children: [
      { path: '/servers', element: <ServerPicker /> },
      {
        element: <RequireLibrary />,
        children: [
          {
            element: <Layout />,
            children: [
              // List pages render the viewer through an <Outlet> on top of themselves, so the
              // grid stays mounted (scroll position, search query and results survive).
              { path: '/photos', element: <Photos />, children: [viewerRoute] },
              { path: '/albums', element: <Albums /> },
              { path: '/albums/:albumId', element: <AlbumDetail />, children: [viewerRoute] },
              { path: '/my-albums/:id', element: <MyAlbumDetail />, children: [viewerRoute] },
              { path: '/favourites', element: <Favourites />, children: [viewerRoute] },
              { path: '/search', element: <Search />, children: [viewerRoute] },
              { path: '/locked', element: <Locked />, children: [viewerRoute] },
              { path: '/settings', element: <Settings /> },
            ],
          },
          // Standalone viewer for links with no list context: shows the one item.
          { path: '/view/:itemId', element: <Viewer /> },
          // Links from older builds identified items by list position, which cannot be resolved.
          { path: '/view/:source/:index', element: <Navigate to="/photos" replace /> },
          { path: '/edit/:id', element: <Editor /> },
        ],
      },
    ],
  },
  { path: '/', element: <Navigate to="/photos" replace /> },
  { path: '*', element: <Navigate to="/photos" replace /> },
])
