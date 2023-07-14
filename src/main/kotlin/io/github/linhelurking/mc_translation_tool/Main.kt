package io.github.linhelurking.mc_translation_tool

import io.github.linhelurking.mc_translation_tool.translator.SnbtTranslator
import io.github.linhelurking.mc_translation_tool.translator.XunfeiStringTranslator
import io.github.linhelurking.snbt.parser.SnbtParser
import io.github.linhelurking.snbt.tag.TagId
import java.io.File

fun main(args: Array<String>) {
    val dirStr =
        "/Users/bytedance/workspace/minecraft/.minecraft/versions/Medieval MC [FABRIC] 1.19.2/config/ftbquests/quests/chapters/"
    File(dirStr).walkTopDown().filter {
        it.isFile
    }.forEach {
        val tag = SnbtParser.fromFile(it.absolutePath).read()
        val translator = SnbtTranslator(XunfeiStringTranslator.buildFromEnvVar()) { k, v ->
            k.lowercase().contains("title") && v.id == TagId.STRING
        }
        translator.translate(tag)
    }
}