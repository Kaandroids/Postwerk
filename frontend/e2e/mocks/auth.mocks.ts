export const mockLoginResponse = {
  accessToken:
    'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiZW1haWwiOiJ0ZXN0QGV4YW1wbGUuY29tIiwiaWF0IjoxNzE2MDAwMDAwLCJleHAiOjk5OTk5OTk5OTl9.fake',
  refreshToken:
    'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwidHlwZSI6InJlZnJlc2giLCJpYXQiOjE3MTYwMDAwMDAsImV4cCI6OTk5OTk5OTk5OX0.fake',
};

// Registration no longer issues tokens — it returns a "verification required" envelope and the
// user must confirm their email before logging in.
export const mockRegisterResponse = {
  verificationRequired: true,
  email: 'new@example.com',
};

// The /auth/verify-email endpoint logs the user in (returns tokens) once the link is opened.
export const mockVerifyEmailResponse = {
  ...mockLoginResponse,
};

export const mockLoginError = {
  status: 401,
  message: 'Ungültige E-Mail oder Passwort',
};

export const mockRefreshResponse = {
  accessToken:
    'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiZW1haWwiOiJ0ZXN0QGV4YW1wbGUuY29tIiwiaWF0IjoxNzE2MDAwMDAwLCJleHAiOjk5OTk5OTk5OTl9.refreshed',
  refreshToken:
    'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwidHlwZSI6InJlZnJlc2giLCJpYXQiOjE3MTYwMDAwMDAsImV4cCI6OTk5OTk5OTk5OX0.refreshed',
};
