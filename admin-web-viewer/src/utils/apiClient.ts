import { fetchAuthSession } from 'aws-amplify/auth';

async function getIdToken(): Promise<string> {
  const session = await fetchAuthSession();
  const token = session.tokens?.idToken?.toString();
  if (!token) throw new Error('No id token in session. Please sign in again.');
  return token;
}

export async function authFetch(url: string, options: RequestInit = {}): Promise<Response> {
  const idToken = await getIdToken();
  return fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
      Authorization: `Bearer ${idToken}`,
    },
  });
}
