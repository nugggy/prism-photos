import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { Library } from '../plex/model'
import type { PlexServer } from '../plex/resources'

export interface ActiveConnection {
  baseUrl: string
  kind: 'local' | 'remote' | 'relay' | 'manual'
}

export interface SessionState {
  accountToken: string | null
  username: string | null
  servers: PlexServer[]
  selectedServerId: string | null
  serverToken: string | null // access token scoped to the selected server
  machineId: string | null
  activeConnection: ActiveConnection | null
  libraries: Library[]
  selectedSectionKey: string | null

  setAccount: (token: string, username: string) => void
  setServers: (servers: PlexServer[]) => void
  selectServer: (serverId: string, serverToken: string, machineId: string) => void
  setActiveConnection: (connection: ActiveConnection | null) => void
  setMachineId: (machineId: string) => void
  setLibraries: (libraries: Library[]) => void
  selectSection: (sectionKey: string) => void
  signOut: () => void
  isSignedIn: () => boolean
}

export const useSessionStore = create<SessionState>()(
  persist(
    (set, get) => ({
      accountToken: null,
      username: null,
      servers: [],
      selectedServerId: null,
      serverToken: null,
      machineId: null,
      activeConnection: null,
      libraries: [],
      selectedSectionKey: null,

      setAccount: (token, username) => set({ accountToken: token, username }),
      setServers: (servers) => set({ servers }),
      selectServer: (serverId, serverToken, machineId) =>
        set({ selectedServerId: serverId, serverToken, machineId, libraries: [], selectedSectionKey: null }),
      setActiveConnection: (connection) => set({ activeConnection: connection }),
      setMachineId: (machineId) => set({ machineId }),
      setLibraries: (libraries) => set({ libraries }),
      selectSection: (sectionKey) => set({ selectedSectionKey: sectionKey }),
      signOut: () =>
        set({
          accountToken: null,
          username: null,
          servers: [],
          selectedServerId: null,
          serverToken: null,
          machineId: null,
          activeConnection: null,
          libraries: [],
          selectedSectionKey: null,
        }),
      isSignedIn: () => !!get().accountToken || !!get().serverToken,
    }),
    { name: 'prism.session' },
  ),
)
