# IDEA2IMPACT Unified Auth — Express + Prisma + React Web Companion Guide

This guide provides the exact backend (`IDEA2IMPACT-source` Express/Prisma on Render) and frontend (`IDEA2IMPACT-source` React/Vite on Vercel) code that pairs 1:1 with the Android unified authentication bridge implemented in this repository.

---

## 1. Prisma Schema Migration (`prisma/schema.prisma`)

Extend the existing `User` model without replacing or duplicating user accounts:

```prisma
model User {
  id            String   @id @default(cuid())
  email         String   @unique
  name          String
  passwordHash  String?  // Nullable so Google-only users do not require a dummy password
  role          String   @default("Creator")
  headline      String?  @default("Innovator on IDEA2IMPACT")
  bio           String?  @default("")
  avatarUrl     String?
  location      String?  @default("Global")
  skills        String[] @default([])
  verified      Boolean  @default(true)

  // Unified Firebase Auth identity bridge
  firebaseUid   String?  @unique
  authProviders String[] @default(["password"])
  emailVerified Boolean  @default(false)

  createdAt     DateTime @default(now())
  updatedAt     DateTime @updatedAt
}
```

Run migration:
```bash
npx prisma migrate dev --name add_unified_firebase_auth_fields
```

---

## 2. Firebase Admin Initialization (`server/src/lib/firebaseAdmin.ts`)

Store credentials exclusively in Render server-side environment variables (`FIREBASE_PROJECT_ID`, `FIREBASE_CLIENT_EMAIL`, `FIREBASE_PRIVATE_KEY` — NEVER prefix with `VITE_` or commit to Git):

```ts
import admin from 'firebase-admin';

if (!admin.apps.length) {
  const projectId = process.env.FIREBASE_PROJECT_ID;
  const clientEmail = process.env.FIREBASE_CLIENT_EMAIL;
  const privateKey = process.env.FIREBASE_PRIVATE_KEY?.replace(/\\n/g, '\n');

  if (projectId && clientEmail && privateKey) {
    admin.initializeApp({
      credential: admin.credential.cert({ projectId, clientEmail, privateKey }),
    });
  } else {
    console.warn('[firebaseAdmin] Credentials not configured; Firebase token exchange disabled.');
  }
}

export const firebaseAdminAuth = () => {
  if (!admin.apps.length) {
    throw new Error('Firebase Admin SDK is not configured on the server.');
  }
  return admin.auth();
};
```

---

## 3. Express Auth Routes (`server/src/routes/auth.ts`)

Add the two exchange routes and enrich `/login`, `/register`, and `/me`:

```ts
import express from 'express';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import { prisma } from '../lib/prisma';
import { firebaseAdminAuth } from '../lib/firebaseAdmin';
import { requireAuth } from '../middleware/auth';

const router = express.Router();
const JWT_SECRET = process.env.JWT_SECRET!;

function signExpressJwt(user: { id: string; email: string; role: string; firebaseUid: string | null }) {
  return jwt.sign(
    { sub: user.id, userId: user.id, email: user.email, role: user.role, firebaseUid: user.firebaseUid },
    JWT_SECRET,
    { expiresIn: '7d' }
  );
}

function sanitizeUser(u: any) {
  return {
    id: u.id,
    name: u.name,
    email: u.email,
    role: u.role,
    headline: u.headline ?? 'Innovator on IDEA2IMPACT',
    bio: u.bio ?? '',
    avatarUrl: u.avatarUrl ?? null,
    location: u.location ?? 'Global',
    skills: u.skills ?? [],
    verified: Boolean(u.verified),
    firebaseUid: u.firebaseUid ?? null,
    authProviders: u.authProviders ?? [],
    emailVerified: Boolean(u.emailVerified),
  };
}

/**
 * POST /api/auth/firebase-exchange
 * Exchanges a verified Firebase ID Token (from Android Credential Manager or Web Google Sign-In)
 * for an Express JWT. Automatically links to an existing email/password account ONLY when
 * Firebase confirms email_verified === true.
 */
router.post('/firebase-exchange', async (req, res) => {
  try {
    const idToken = typeof req.body?.idToken === 'string' ? req.body.idToken.trim() : '';
    if (!idToken) return res.status(400).json({ error: 'idToken is required.' });

    const decoded = await firebaseAdminAuth().verifyIdToken(idToken, true);
    const firebaseUid = decoded.uid;
    const normalizedEmail = (decoded.email || '').trim().toLowerCase();
    const isEmailVerified = Boolean(decoded.email_verified);
    const displayName = decoded.name || normalizedEmail.split('@')[0] || 'Innovator';
    const photoUrl = decoded.picture || null;

    if (!normalizedEmail) {
      return res.status(400).json({ error: 'Verified email address is required for account exchange.' });
    }

    // 1. Primary lookup by firebaseUid
    let user = await prisma.user.findUnique({ where: { firebaseUid } });
    let accountLinked = false;

    // 2. Auto-link by email ONLY if Firebase verified the email
    if (!user) {
      const existingByEmail = await prisma.user.findUnique({ where: { email: normalizedEmail } });
      if (existingByEmail) {
        if (!isEmailVerified) {
          return res.status(403).json({
            error: 'Email address must be verified before linking to an existing account.',
          });
        }
        const mergedProviders = Array.from(new Set([...(existingByEmail.authProviders || []), 'google']));
        user = await prisma.user.update({
          where: { id: existingByEmail.id },
          data: {
            firebaseUid,
            authProviders: mergedProviders,
            emailVerified: true,
            avatarUrl: existingByEmail.avatarUrl || photoUrl,
          },
        });
        accountLinked = true;
      } else {
        user = await prisma.user.create({
          data: {
            email: normalizedEmail,
            name: displayName,
            passwordHash: null,
            firebaseUid,
            authProviders: ['google'],
            emailVerified: isEmailVerified,
            avatarUrl: photoUrl,
          },
        });
      }
    }

    // Attach canonical PostgreSQL claims onto Firebase user (best-effort)
    await firebaseAdminAuth()
      .setCustomUserClaims(firebaseUid, { pgUserId: user.id, role: user.role })
      .catch(() => {});

    const token = signExpressJwt(user);
    return res.json({ token, accountLinked, user: sanitizeUser(user) });
  } catch (err: any) {
    console.error('[auth/firebase-exchange] failed:', err?.code || err?.message);
    return res.status(401).json({ error: 'Invalid or expired Firebase ID token.' });
  }
});

/**
 * POST /api/auth/firebase-custom-token
 * Requires an active Express Bearer JWT and mints a Firebase Custom Token so
 * email/password users receive a valid Firebase Auth session for Cloud Firestore.
 */
router.post('/firebase-custom-token', requireAuth, async (req: any, res) => {
  try {
    let user = await prisma.user.findUnique({ where: { id: req.user.id } });
    if (!user) return res.status(404).json({ error: 'User not found.' });

    if (!user.firebaseUid) {
      const canonicalUid = `i2i_${user.id}`;
      user = await prisma.user.update({
        where: { id: user.id },
        data: { firebaseUid: canonicalUid },
      });
    }

    const firebaseCustomToken = await firebaseAdminAuth().createCustomToken(user.firebaseUid!, {
      pgUserId: user.id,
      role: user.role,
    });
    return res.json({ firebaseCustomToken, firebaseUid: user.firebaseUid });
  } catch (err: any) {
    console.error('[auth/firebase-custom-token] failed:', err?.message);
    return res.status(503).json({ error: 'Unable to mint Firebase custom token right now.' });
  }
});

// In existing POST /api/auth/login and POST /api/auth/register:
// 1. Ensure user.firebaseUid is populated (defaulting to `i2i_${user.id}`).
// 2. Best-effort mint firebaseCustomToken via firebaseAdminAuth().createCustomToken(user.firebaseUid, { pgUserId: user.id, role: user.role }).
// 3. Return { token, firebaseCustomToken, user: sanitizeUser(user) }.
```

### CORS Allowlist (`server/src/index.ts`)
Never use wildcard `*` on authenticated routes. Explicitly allow your Vercel production/preview origins:
```ts
const allowedOrigins = [
  'https://idea2impact-yges.vercel.app',
  ...(process.env.CORS_ORIGINS?.split(',').map((s) => s.trim()).filter(Boolean) ?? []),
];
app.use(cors({
  origin: (origin, cb) => (!origin || allowedOrigins.includes(origin) ? cb(null, true) : cb(new Error('CORS blocked'))),
  allowedHeaders: ['Content-Type', 'Authorization', 'x-goog-api-key'],
}));
```

---

## 4. React Website In-Memory Bearer Auth Context (`src/context/AuthContext.tsx`)

Holds the Express JWT strictly in JavaScript memory (zero `localStorage` / `sessionStorage` writes) and uses Firebase Web Auth persistence (`onAuthStateChanged`) to silently re-exchange a fresh Express JWT on reload or HTTP `401`:

```tsx
import React, { createContext, useContext, useEffect, useRef, useState } from 'react';
import {
  getAuth,
  GoogleAuthProvider,
  onAuthStateChanged,
  signInWithCustomToken,
  signInWithPopup,
  signOut as firebaseSignOut,
} from 'firebase/auth';
import { firebaseApp } from '../lib/firebase';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'https://idea2impact-g8fe.onrender.com/api';

// Module-scoped in-memory JWT holder (never persisted in localStorage/sessionStorage)
let inMemoryAccessToken: string | null = null;
let inflightRefreshPromise: Promise<string | null> | null = null;

export function getInMemoryJwt(): string | null {
  return inMemoryAccessToken;
}

export async function refreshExpressJwtFromFirebase(forceRefresh = true): Promise<string | null> {
  if (inflightRefreshPromise) return inflightRefreshPromise;
  inflightRefreshPromise = (async () => {
    const fbUser = getAuth(firebaseApp).currentUser;
    if (!fbUser) return null;
    const idToken = await fbUser.getIdToken(forceRefresh);
    const res = await fetch(`${API_BASE}/auth/firebase-exchange`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ idToken }),
    });
    if (!res.ok) return null;
    const data = await res.json();
    inMemoryAccessToken = data.token ?? null;
    return inMemoryAccessToken;
  })().finally(() => {
    inflightRefreshPromise = null;
  });
  return inflightRefreshPromise;
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const buildHeaders = (token: string | null) => ({
    Accept: 'application/json',
    ...(init.headers || {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  });

  let res = await fetch(`${API_BASE}${path}`, { ...init, headers: buildHeaders(inMemoryAccessToken) });
  if (res.status === 401) {
    const freshToken = await refreshExpressJwtFromFirebase(true);
    if (freshToken) {
      res = await fetch(`${API_BASE}${path}`, { ...init, headers: buildHeaders(freshToken) });
    }
  }
  return res;
}
```

On `signInWithGoogle`: call `signInWithPopup(auth, new GoogleAuthProvider())` → `refreshExpressJwtFromFirebase(false)`.
On `loginWithEmail` / `registerWithEmail`: call `POST /api/auth/login` or `/register` → set `inMemoryAccessToken = data.token` → call `signInWithCustomToken(auth, data.firebaseCustomToken)` so both React Web and Android share the exact same Firestore UID and Express JWT!
On `signOut`: set `inMemoryAccessToken = null`, call `firebaseSignOut(auth)`, and clear React state.
