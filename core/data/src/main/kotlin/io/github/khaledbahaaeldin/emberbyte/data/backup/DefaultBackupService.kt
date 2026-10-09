package io.github.khaledbahaaeldin.emberbyte.data.backup

import io.github.khaledbahaaeldin.emberbyte.data.export.ExportService
import io.github.khaledbahaaeldin.emberbyte.data.plan.PlanValidation
import io.github.khaledbahaaeldin.emberbyte.engine.model.BudgetPeriod
import io.github.khaledbahaaeldin.emberbyte.engine.model.EmberbyteError
import io.github.khaledbahaaeldin.emberbyte.engine.model.Outcome
import io.github.khaledbahaaeldin.emberbyte.engine.usage.AppMeta
import io.github.khaledbahaaeldin.emberbyte.engine.usage.HourlyUsage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipException
import kotlinx.coroutines.CancellationException

class DefaultBackupService(
    private val export: ExportService,
    private val restore: RestoreStore,
    private val resolver: PackageResolver,
    private val params: BackupCodec.Params = BackupCodec.Params(),
) : BackupService {

    companion object {
        private const val MAX_BACKUP_BYTES = 512 * 1024 * 1024L
    }

    override suspend fun createBackup(out: OutputStream, passphrase: CharArray): Outcome<Unit> = try {
        if (passphrase.size < 8) {
            Outcome.Failure(EmberbyteError.Invalid("passphrase", "Use at least 8 characters"))
        } else {
            val compressed = ByteArrayOutputStream()
            GZIPOutputStream(compressed).use { gzip ->
                when (val r = export.exportJson(gzip)) {
                    is Outcome.Failure -> return r
                    is Outcome.Success -> Unit
                }
            }
            out.write(BackupCodec.seal(compressed.toByteArray(), passphrase, params))
            out.flush()
            Outcome.Success(Unit)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(EmberbyteError.Storage(e.message ?: "Backup failed"))
    } finally {
        passphrase.fill('\u0000')
    }

    override suspend fun restoreBackup(input: InputStream, passphrase: CharArray): Outcome<RestoreSummary> = try {
        val buffer = ByteArray(8192)
        val out = ByteArrayOutputStream()
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > MAX_BACKUP_BYTES) {
                return Outcome.Failure(EmberbyteError.Invalid("file", "The file is too large"))
            }
            out.write(buffer, 0, read)
        }
        val sealed = out.toByteArray()

        val decompressed = BackupCodec.open(sealed, passphrase)

        val gzipStream = try {
            GZIPInputStream(ByteArrayInputStream(decompressed))
        } catch (_: ZipException) {
            throw BackupFormatException("file", "The backup is damaged")
        }

        gzipStream.use { stream ->
            val parsed = BackupParser.parse(stream)

            if (parsed.contractVersion > 1) {
                return Outcome.Failure(EmberbyteError.Invalid("file", "This backup was made by a newer version of Emberbyte"))
            }

            val planIds = HashSet<Long>()
            for (p in parsed.plans) {
                if (!planIds.add(p.plan.id)) {
                    return Outcome.Failure(EmberbyteError.Invalid("file", "Duplicate plan id: ${p.plan.id}"))
                }
                if (PlanValidation.plan(p.plan) != null) {
                    return Outcome.Failure(EmberbyteError.Invalid("file", "The backup contains an invalid plan: ${p.plan.name}"))
                }
                for (rule in p.alertRules) {
                    if (rule.thresholdPercent !in 1..100) {
                        return Outcome.Failure(EmberbyteError.Invalid("file", "The backup contains an invalid alert threshold: ${rule.thresholdPercent}"))
                    }
                }
                for (addOn in p.addOns) {
                    if (addOn.planId != p.plan.id) {
                        return Outcome.Failure(EmberbyteError.Invalid("file", "The backup contains an invalid add-on: planId mismatch"))
                    }
                    if (PlanValidation.addOn(addOn) != null) {
                        return Outcome.Failure(EmberbyteError.Invalid("file", "The backup contains an invalid plan: ${p.plan.name}"))
                    }
                }
                for (freeRule in p.freeRules) {
                    if (freeRule.planId != p.plan.id) {
                        return Outcome.Failure(EmberbyteError.Invalid("file", "The backup contains an invalid free rule: planId mismatch"))
                    }
                    if (PlanValidation.freeRule(freeRule) != null) {
                        return Outcome.Failure(EmberbyteError.Invalid("file", "The backup contains an invalid plan: ${p.plan.name}"))
                    }
                }
            }

            val budgetPackages = HashSet<String>()
            for (b in parsed.budgets) {
                if (!budgetPackages.add(b.packageName)) {
                    return Outcome.Failure(EmberbyteError.Invalid("file", "Duplicate budget for package: ${b.packageName}"))
                }
                if (b.packageName.isBlank() || b.limitBytes !in 1L..1_000_000_000_000_000L || b.warnAtPercent !in 1..99) {
                    return Outcome.Failure(EmberbyteError.Invalid("file", "The backup contains an invalid budget: ${b.packageName}"))
                }
                if (b.period == BudgetPeriod.CYCLE && b.planId != null && b.planId !in planIds) {
                    return Outcome.Failure(EmberbyteError.Invalid("file", "The backup contains a budget referencing missing plan: ${b.planId}"))
                }
            }

            val sanitizedPlans = parsed.plans.map { p ->
                p.copy(
                    addOns = p.addOns.map { it.copy(planId = p.plan.id) },
                    freeRules = p.freeRules.map { it.copy(planId = p.plan.id) },
                    alertRules = p.alertRules.map { it.copy(planId = p.plan.id) },
                )
            }
            val sanitizedBudgets = parsed.budgets.map { b ->
                if (b.period == BudgetPeriod.DAILY) b.copy(planId = null) else b
            }

            var insertedCount = 0
            var skippedCount = 0
            val metaByUid = LinkedHashMap<Int, AppMeta>()
            val resolveCache = mutableMapOf<String, AppMeta?>()

            val usageSequence = sequence {
                for (row in parsed.usage) {
                    if (row.packageName.startsWith("uid:")) {
                        val uid = row.packageName.removePrefix("uid:").toIntOrNull()
                        if (uid != null) {
                            insertedCount++
                            yield(HourlyUsage(row.hourStart, uid, row.network, row.subscriptionId, row.rxBytes, row.txBytes))
                        } else {
                            skippedCount++
                        }
                    } else {
                        val meta = resolveCache.getOrPut(row.packageName) {
                            resolver.resolve(row.packageName)
                        }
                        if (meta != null) {
                            metaByUid[meta.uid] = meta
                            insertedCount++
                            yield(HourlyUsage(row.hourStart, meta.uid, row.network, row.subscriptionId, row.rxBytes, row.txBytes))
                        } else {
                            skippedCount++
                        }
                    }
                }
            }

            restore.replaceAll(sanitizedPlans, sanitizedBudgets, usageSequence) {
                metaByUid.values.toList()
            }

            Outcome.Success(RestoreSummary(sanitizedPlans.size, sanitizedBudgets.size, insertedCount, skippedCount))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: OutOfMemoryError) {
        Outcome.Failure(EmberbyteError.Invalid("file", "The file is too large for this device"))
    } catch (e: BackupFormatException) {
        Outcome.Failure(EmberbyteError.Invalid(e.field, e.message ?: "The backup is damaged"))
    } catch (e: ZipException) {
        Outcome.Failure(EmberbyteError.Invalid("file", "The backup is damaged"))
    } catch (e: Exception) {
        Outcome.Failure(EmberbyteError.Storage(e.message ?: "Restore failed"))
    } finally {
        passphrase.fill('\u0000')
    }
}
