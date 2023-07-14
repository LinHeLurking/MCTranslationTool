package io.github.linhelurking.mc_translation_tool.translator

import io.github.linhelurking.snbt.tag.SnbtTag
import io.github.linhelurking.snbt.tag.TagId

class SnbtTranslator(
    private val stringTranslator: IStringTranslator,
    private val needTranslation: (key: String, value: SnbtTag) -> Boolean
) {
    fun translate(tag: SnbtTag) {
        when (tag.id) {
            TagId.COMPOUND -> {
                val map = tag.toMap().toMutableMap()
                map.replaceAll { k, v ->
                    if (v.id == TagId.STRING && needTranslation(k, v)) {
                        v.value = stringTranslator.translate(v.value as String)
                        println("key: $k, value: $v")
                    } else {
                        translate(v)
                    }
                    v
                }
                tag.value = map
            }

            TagId.LIST -> {
                val lst = tag.toList().toMutableList()
                lst.replaceAll {
                    translate(it)
                    it
                }
                tag.value = lst
            }

            else -> {}
        }
    }
}