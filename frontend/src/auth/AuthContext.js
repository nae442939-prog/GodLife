import { createContext } from 'react'

// 값: { user, status: 'loading' | 'authed' | 'anon', login, signup, logout }
export const AuthContext = createContext(null)
