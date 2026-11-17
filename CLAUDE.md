# Claude Code Instructions

This project is a hybrid monorepo consisting of an Android app, an AWS CDK backend, and a React web viewer. You are acting as a senior full-stack engineer and AWS architect.

## Project Structure & Roles
- `/android-app`: Native Android app using Kotlin and Jetpack Compose.
- `/backend`: Serverless infrastructure using AWS CDK (TypeScript).
- `/admin-web-viewer`: Frontend dashboard using React (Vite/TypeScript).

## Environment Context
- The user is working on Windows + WSL2 (Ubuntu).
- **CRITICAL**: Do NOT run Gradle builds or execute Android tests from the WSL2 terminal, as the file system boundary makes it extremely slow. Android tasks are handled manually by the user in Android Studio on Windows.
- You can run Node.js, AWS CLI, and CDK commands freely within the WSL2 terminal.

## Build & Deploy Commands
- **Backend (CDK)**:
  - Directory: `cd backend`
  - Install: `npm install`
  - Deploy: `npx cdk deploy --require-approval never`
- **Admin Web Viewer**:
  - Directory: `cd admin-web-viewer`
  - Install: `npm install`
  - Build: `npm run build`
  - Deploy: `aws s3 sync dist/ s3://<BUCKET_NAME>/` (Ask user for bucket name if unknown)

## Tech Stack & Code Style
- **Android**: 
  - Use Kotlin Coroutines for async tasks.
  - UI is strictly Jetpack Compose (no XML layouts).
  - Use `OkHttp` for networking.
- **AWS CDK**: 
  - Use CDK v2 (`aws-cdk-lib`).
  - Lambda functions run Node.js 18.x (`@aws-sdk/client-dynamodb` v3).
- **React**:
  - Functional components, React Hooks (`useState`, `useEffect`).
  - TypeScript strict mode.

## Development Workflow
When asked to implement a new feature (e.g., adding Cognito authentication or modifying DynamoDB schema):
1. Plan the architecture changes across all 3 directories.
2. Modify the CDK backend first and deploy.
3. Update the React frontend to match API changes.
4. Update the Android Kotlin code.
