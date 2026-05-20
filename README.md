# SonyRebuild

SonyRebuild is an Android application project built with Kotlin, Jetpack Compose, and Gradle.

## Project Structure

- `app/` - Android application module.
- `gradle/` - Gradle wrapper and version catalog files.
- `build.gradle.kts` - Root Gradle build configuration.
- `settings.gradle.kts` - Gradle project settings.

## Requirements

- JDK 17
- Android SDK with the configured compile SDK installed
- Android Studio or the included Gradle wrapper

## Common Commands

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat testDebugUnitTest assembleDebug :app:compileDebugAndroidTestKotlin
```

## Appearance Settings

The app persists UI appearance settings through DataStore. The Appearance screen
keeps color mode separate from the visual theme style:

- Color mode controls whether the app renders as Light, Dark, or Follow system.
- Theme style controls the Material or MIUIX-like surface treatment.
- The resolved color mode drives the Material color scheme, MIUIX mode, and
  system bar icon contrast.
- About page acrylic and haze effects follow the global UI effects setting and
  render-effect support, independent of whether glass card rendering is active.

Text and icon colors in shared surfaces should be derived from
`MaterialTheme.colorScheme`, especially for headers, cards, and nested settings
pages, so dark mode does not inherit stale light-theme content colors.

## Notes

Local machine settings, build outputs, IDE caches, and signing keys are intentionally excluded from version control.
