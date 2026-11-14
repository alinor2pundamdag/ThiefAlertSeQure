# ThiefAlertSeQure release hardening.
# Android manifest components are retained by the Android Gradle Plugin.
# Add narrowly-scoped keep rules only when a verified reflection/serialization
# requirement or a failing minified-release test demonstrates the need.

# Preserve runtime annotations and generic signatures used by Android/Kotlin
# libraries while allowing implementation classes and members to be renamed.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Do not preserve source filenames or line-number tables in distributed APKs.
# Keep the mapping file as a private CI artifact; never publish it with the APK.
