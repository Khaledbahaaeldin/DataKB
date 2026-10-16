# Proguard / R8 configuration for Emberbyte release build
# Contract 12.3: Keep ONLY rules proven necessary by a failing run.
# Room and WorkManager include their own consumer rules automatically.
# Bouncy Castle and Glance are directly referenced in bytecode; R8 traces them without explicit keep rules.
