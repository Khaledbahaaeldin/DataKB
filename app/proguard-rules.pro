# Proguard / R8 configuration for Emberbyte release build

# Bouncy Castle Argon2: BackupCodec directly instantiates Argon2BytesGenerator and builds Argon2Parameters for password-based key derivation in encrypted backups.
-keep class org.bouncycastle.crypto.generators.Argon2BytesGenerator {
    public <init>();
    public void init(org.bouncycastle.crypto.params.Argon2Parameters);
    public int generateBytes(char[], byte[]);
}
-keep class org.bouncycastle.crypto.params.Argon2Parameters { *; }
-keep class org.bouncycastle.crypto.params.Argon2Parameters$Builder { *; }

# Glance: AppWidget instances and their compose content are loaded reflectively by the Glance framework.
-keep class io.github.khaledbahaaeldin.emberbyte.widget.HeroWidget { *; }
-keep class io.github.khaledbahaaeldin.emberbyte.widget.BentoWidget { *; }

# Room: Room database implementations and entity models are inspected reflectively by Room and KSP generated code.
-keep class * extends androidx.room.RoomDatabase
-keep class io.github.khaledbahaaeldin.emberbyte.data.db.**Entity { *; }

# WorkManager: Configuration and Worker classes instantiated reflectively by WorkManager framework.
-keep class androidx.work.Worker { *; }
-keep class androidx.work.CoroutineWorker { *; }
