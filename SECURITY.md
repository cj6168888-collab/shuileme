# Security Policy

## Sensitive Data

Do not commit:

- API keys or tokens.
- Emergency contact phone numbers.
- Private sleep recordings.
- Local APK signing keys.

## DeepSeek Testing

DeepSeek API keys are for local testing only. Use `android-app/local.deepseek.properties`,
which is ignored by git. The APK build must not contain API keys.

## Reporting Security Issues

Open a private report through the repository owner if available. If private
reporting is not available, open a minimal issue without secrets or private user data.
