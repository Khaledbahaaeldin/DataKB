package io.github.khaledbahaaeldin.emberbyte.data.db

import java.io.File
import org.json.JSONObject

internal object SchemaFile {
    private const val DIR = "schemas/io.github.khaledbahaaeldin.emberbyte.data.db.EmberbyteDatabase"

    /** Every statement needed to create the database of [version], read from the exported schema (the test working directory is the module directory). */
    fun statements(version: Int): List<String> {
        val file = File("$DIR/$version.json").let { if (it.exists()) it else File("core/data/$DIR/$version.json") }
        val db = JSONObject(file.readText()).getJSONObject("database")
        val out = mutableListOf<String>()
        val entities = db.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            out += entity.getString("createSql").replace("\${TABLE_NAME}", table)
            entity.optJSONArray("indices")?.let { indices ->
                for (j in 0 until indices.length()) out += indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)
            }
        }
        db.optJSONArray("setupQueries")?.let { setup -> for (i in 0 until setup.length()) out += setup.getString(i) }
        return out
    }
}
