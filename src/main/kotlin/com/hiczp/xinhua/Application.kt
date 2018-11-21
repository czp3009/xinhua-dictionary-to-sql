package com.hiczp.xinhua

import com.google.gson.stream.JsonReader
import mu.KotlinLogging
import org.apache.log4j.BasicConfigurator
import java.io.File
import java.io.FileReader

private val logger = KotlinLogging.logger("Application")

@Suppress("SpellCheckingInspection")
private const val DICTIONARY_DIRECTORY = "chinese-xinhua/data"
private const val OUTPUT_DIRECTORY = "sql"

//magic string
private val predefinedFieldType = mapOf(
    "word" to mapOf(
        "explanation" to "TEXT",
        "more" to "MEDIUMTEXT"
    )
)
//magic number
private const val recordsCountInQuery = 500

fun main(args: Array<String>) {
    //log
    BasicConfigurator.configure()

    //validate args
    val dictionaryDirectory = File(args.getOrNull(0) ?: DICTIONARY_DIRECTORY)
    val outputDirectory = File(args.getOrNull(1) ?: OUTPUT_DIRECTORY)
    validationArgs(dictionaryDirectory, outputDirectory)

    dictionaryDirectory.run {
        listFiles { it -> it.extension == "json" }.also {
            if (it.isEmpty()) {
                logger.warn { "No available json file found in $absolutePath" }
                return
            }
        }
    }.also { files ->
        logger.info {
            StringBuilder("Found ${files.size} available json file(s):")
                .appendln()
                .apply {
                    files.forEach { appendln(it.name) }
                }.toString()
        }
    }.forEach {
        logger.info { "Preparing to parse file ${it.name}" }
        try {
            if (parseJsonFileAndGenerateSQLFile(it, outputDirectory)) {
                logger.info { "File ${it.name} parsed" }
            } else {
                logger.error { "Parse file ${it.name} failed" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error occurred while parsing file ${it.name}" }
        }
        println()
    }
}

fun validationArgs(dictionaryDirectory: File, outputDirectory: File) {
    dictionaryDirectory.run {
        if (!exists()) throw IllegalArgumentException("Directory $absolutePath not exists")
        if (!isDirectory) throw IllegalArgumentException("The path $absolutePath is not a directory")
    }
    outputDirectory.run {
        if (exists()) {
            if (!isDirectory) throw IllegalArgumentException("Output path $absolutePath must be a directory")
            logger.info { "Output directory already exists" }
        } else {
            if (!mkdirs()) throw SecurityException("Cannot create directory $absolutePath")
            logger.info { "Created directory $absolutePath" }
        }
    }
}

fun parseJsonFileAndGenerateSQLFile(file: File, outputDirectory: File): Boolean {
    val tableName = file.nameWithoutExtension
    val sqlFile = outputDirectory.resolve("$tableName.sql").apply {
        delete()
        createNewFile()
        logger.info { "File $absolutePath created" }
    }

    JsonReader(FileReader(file)).use {
        val valueLists = ArrayList<List<String>>(recordsCountInQuery)
        if (!it.hasNext()) {
            logger.warn { "Json file ${file.absolutePath} is empty" }
            return false
        }
        it.beginArray()

        //first object
        if (!it.hasNext()) {
            logger.warn { "Json file ${file.absolutePath} does not have any available data" }
            return false
        }
        it.beginObject()
        if (!it.hasNext()) {
            logger.error { "The first JsonObject in json file ${file.absolutePath} is empty" }
            return false
        }
        val firstObject = LinkedHashMap<String, String>().apply {
            while (it.hasNext()) {
                put(it.nextName(), it.nextString())
            }
        }
        it.endObject()
        val fields = firstObject.keys.sorted()
        writeToSQLFile(generateTableStructure(tableName, fields), sqlFile)
        valueLists.add(firstObject.toSortedMap().values.toList())

        //objects
        val currentObject = HashMap<String, String>()
        while (it.hasNext()) {
            it.beginObject()
            while (it.hasNext()) {
                currentObject[it.nextName()] = it.nextString()
            }
            it.endObject()
            valueLists.add(currentObject.toSortedMap().values.toList())
            if (valueLists.size == recordsCountInQuery) {
                writeToSQLFile(generateQuery(tableName, fields, valueLists), sqlFile)
                valueLists.clear()
            }
        }

        it.endArray()
        writeToSQLFile(generateQuery(tableName, fields, valueLists), sqlFile)
    }

    return true
}

fun generateTableStructure(tableName: String, fields: List<String>) =
    fields.joinToString(separator = "\n\t") {
        "`$it` ${predefinedFieldType[tableName]?.get(it) ?: "VARCHAR(255)"} NOT NULL,"
    }.let {
        @Suppress("SpellCheckingInspection")
        """
        |CREATE TABLE IF NOT EXISTS `$tableName`(
        |  `id` INT UNSIGNED AUTO_INCREMENT,
        |  $it
        |  PRIMARY KEY ( `id` )
        |)ENGINE=InnoDB DEFAULT CHARSET=utf8;
        |
        |
        """.trimMargin()
    }

fun generateQuery(tableName: String, fields: List<String>, valueLists: List<List<String>>) =
    if (valueLists.isEmpty()) {
        ""
    } else {
        StringBuilder("INSERT INTO `$tableName` (${fields.joinToString { "`$it`" }}) VALUES").apply {
            appendln()
            valueLists.forEach { values ->
                appendln(values.joinToString(prefix = "(", postfix = "),") {
                    "'${it.replace("'", """\'""")}'"
                })
            }
            //remove last ,
            setLength(length - 2)
            appendln(";")
            appendln()
        }.toString()
    }

fun writeToSQLFile(content: String, file: File) = file.appendText(content)
