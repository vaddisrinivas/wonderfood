Outcome: COMPLETE
Commit: NONE
Acceptance rows: C19/E04/C25 Sheets V4 request-builder and focused fake proof only
Changed files:
- app/src/main/java/com/wonderfood/app/sync/GoogleSheetsGateway.kt
- app/src/test/java/com/wonderfood/app/sync/GoogleSheetsGatewayTest.kt
- .planning/2026-07-20-wonderfood-105-accelerated-release-campa/worker-reports/sheets-v4.md
Tests:
- FAIL: ./gradlew :app:testDebugUnitTest --tests com.wonderfood.app.sync.GoogleSheetsGatewayTest
  Reason: task ambiguous; flavors are testFossDebugUnitTest and testPlayDebugUnitTest.
- PASS: ./gradlew :app:testPlayDebugUnitTest --tests com.wonderfood.app.sync.GoogleSheetsGatewayTest
- PASS: git diff --check -- app/src/main/java/com/wonderfood/app/sync/GoogleSheetsGateway.kt app/src/test/java/com/wonderfood/app/sync/GoogleSheetsGatewayTest.kt .planning/2026-07-20-wonderfood-105-accelerated-release-campa/worker-reports/sheets-v4.md
Summary:
- Relation fields now get strict ONE_OF_RANGE validation backed by stable label named ranges.
- Bootstrap repairs named ranges, native tables, developer metadata, and warning protections instead of duplicating them.
- Spreadsheet GET now requests namedRanges, sheet developerMetadata, protectedRanges, and tables.
- Native tables carry typed column properties and update existing table ranges.
- V4 export repairs existing table ranges to the row counts written from WorkspaceGraphProjection.
Blocker: NONE in assigned fake/request-builder scope; coordinator still needs live Google Sheets OAuth/workbook proof.
Coordinator action: Run live Sheets proof in Chrome against a fresh V4 workbook and update acceptance matrix with current evidence.
