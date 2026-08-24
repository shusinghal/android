# Lazy Location Indexing Implementation Plan

This plan introduces high-performance, lazy location indexing for the Maps section. It ensures that the main gallery remains instant while enabling location-based features only when the user interacts with the Maps tab.

## User Review Required

> [!IMPORTANT]
> A "Safety Toggle" will be added to `SharedPreferences` (defaulting to `true`). This allows you to disable the location indexing feature entirely if any unexpected issues arise.

> [!NOTE]
> Location indexing requires the `ACCESS_MEDIA_LOCATION` permission. The app will request this permission only when the user navigates to the Maps section.

## Proposed Changes

### Core Data & Indexing

#### [MODIFY] [MediaDao.kt](file:///C:/Users/HP/Documents/Gallery%20app/GPT2/app/src/main/java/com/memorycurator/app/data/local/MediaDao.kt)
- Add `getMediaMissingLocation()` to find photos that haven't been processed for EXIF location.
- Add `updateLocation(id, lat, lon)` for targeted metadata enrichment.

#### [MODIFY] [MediaIndexer.kt](file:///C:/Users/HP/Documents/Gallery%20app/GPT2/app/src/main/java/com/memorycurator/app/data/media/MediaIndexer.kt)
- Implement `enrichLocationMetadata()`:
    - Checks the safety toggle.
    - Iterates through "missing" photos.
    - Uses `MediaStore.setRequireOriginal` and `ExifInterface` to safely extract coordinates.
    - Uses individual `try-catch` blocks per photo to ensure stability.

---

### Features & UI

#### [MODIFY] [AlbumsViewModel.kt](file:///C:/Users/HP/Documents/Gallery%20app/GPT2/app/src/main/java/com/memorycurator/app/feature/albums/ui/AlbumsViewModel.kt)
- Add `performLocationIndexing()` to bridge the UI and the `MediaIndexer`.
- Expose a state to track indexing progress.

#### [MODIFY] [MapsScreen.kt](file:///C:/Users/HP/Documents/Gallery%20app/GPT2/app/src/main/java/com/memorycurator/app/feature/maps/ui/MapsScreen.kt)
- Add a `LaunchedEffect` to trigger indexing when the screen becomes visible.
- Implement permission handling for `ACCESS_MEDIA_LOCATION`.

---

### Infrastructure

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/HP/Documents/Gallery%20app/GPT2/app/src/main/AndroidManifest.xml)
- Add `<uses-permission android:name="android.permission.ACCESS_MEDIA_LOCATION" />`.

## Verification Plan

### Automated Tests
- N/A (Manual verification on device is required for MediaStore/EXIF interactions).

### Manual Verification
1. **Gallery Speed**: Verify that the main Gallery and Timeline sections still load instantly without any "Indexing" delays.
2. **Maps Tab**: Navigate to the Maps tab.
    - Confirm the permission dialog for "Location access for media" appears.
    - Verify that locations are extracted and photos begin appearing in the "Locations" list.
3. **Safety Toggle**: Manually flip the `location_indexing_enabled` flag in code/prefs and verify the Maps tab skips indexing.
