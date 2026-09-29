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
              { path: '/photos', element: <Photos /> },
              { path: '/albums', element: <Albums /> },
              { path: '/albums/:albumId', element: <AlbumDetail /> },
              { path: '/my-albums/:id', element: <MyAlbumDetail /> },
              { path: '/favourites', element: <Favourites /> },
              { path: '/search', element: <Search /> },
              { path: '/locked', element: <Locked /> },
              { path: '/settings', element: <Settings /> },
            ],
          },
          { path: '/view/:source/:index', element: <Viewer /> },
          { path: '/edit/:id', element: <Editor /> },
        ],
      },
    ],
  },
  { path: '/', element: <Navigate to="/photos" replace /> },
  { path: '*', element: <Navigate to="/photos" replace /> },
])
