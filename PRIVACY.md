# Privacy

WonderFood is local-first. Core food records are stored in the app's private Android
database. The repository contains generic test fixtures only and bundles no user data.

Data leaves the device only after a user-triggered action:

- Sending a prompt or receipt to a configured AI provider sends the visible request
  plus relevant in-app food context needed for that request.
- Health Connect access uses Android's permission flow and only the declared record types.
- Google Drive backup uploads an encrypted archive to the app-data folder.
- CSV export, Android sharing, and command links expose the content the user selects.

Android automatic cloud backup and device-transfer backup are disabled. Provider keys
are encrypted with Android Keystore. Clearing app data removes local records and settings.

Project operators deploying a fork must publish a privacy policy matching their chosen
AI providers, OAuth configuration, telemetry, distribution channel, and jurisdiction.

