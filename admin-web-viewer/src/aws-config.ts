import { Amplify } from 'aws-amplify';

const userPoolId = import.meta.env.VITE_USER_POOL_ID ?? '';
const userPoolClientId = import.meta.env.VITE_USER_POOL_CLIENT_ID ?? '';

if (!userPoolId || !userPoolClientId) {
  console.warn(
    '[aws-config] VITE_USER_POOL_ID or VITE_USER_POOL_CLIENT_ID is not set. ' +
    'Copy admin-web-viewer/.env.example to .env.local and run scripts/sync-env.sh.'
  );
}

Amplify.configure({
  Auth: {
    Cognito: {
      userPoolId,
      userPoolClientId,
    },
  },
});
