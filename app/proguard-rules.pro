# ThiefAlertSeQure release hardening.
# Manifest-declared Android components are retained by the Android Gradle Plugin.
# Add narrow keep rules only when verified reflection/serialization requires them.

# Preserve runtime annotations and generic signatures used by Android/Kotlin libraries.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Do not preserve source filenames or line-number tables in distributed APKs.
# Keep R8 mapping files private; never attach them to public release artifacts.
