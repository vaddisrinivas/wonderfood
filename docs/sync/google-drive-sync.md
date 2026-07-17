# Google Drive sync setup

WonderFood sync is local-first:

- SQLite remains the single app data store.
- Google Drive is only the backup/restore transport.
- The app uses Drive `appDataFolder`, not full Drive access.
- AI provider API keys are excluded from cloud backup and stay on-device.

## Google Cloud setup

1. Open Google Cloud Console.
2. Create or pick a project for WonderFood.
3. Enable the Google Drive API.
4. Configure OAuth consent screen for WonderFood.
5. Add this non-sensitive Drive scope:

   `https://www.googleapis.com/auth/drive.appdata`

6. Create an Android OAuth client:

   - Package name: `com.wonderfood.app`
   - Debug SHA-1: `91:30:EA:BE:10:CA:97:31:15:E8:81:B1:20:39:EE:D6:AA:D1:E6:04`

7. Create a Web OAuth client.
8. For local testing, paste the Web client ID on the phone:

   WonderFood → Settings → Data → Google sync → Web OAuth client ID

9. For production/release builds, copy the Web client ID into:

   `app/src/main/res/values/google_auth.xml`

   Replace:

   `TODO_ADD_GOOGLE_WEB_CLIENT_ID`

## Runtime flow

1. User opens Settings → Data.
2. User taps “Sign in with Google”.
3. User taps “Back up now” or “Restore”.
4. Android asks for the narrow Drive app-data permission when needed.
5. WonderFood uploads/downloads a `.wfcloudbackup` SQLite snapshot in Drive `appDataFolder`.

## Notes

- The backup is protected by the user’s Google account and Drive app-data isolation.
- Manual encrypted local backup still exists for passphrase-based offline restore.
- For release builds, create another Android OAuth client with the release signing SHA-1.
