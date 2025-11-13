package com.roadguardian.auto.models
import java.util.Locale
/**
 * Tipos de animales detectables por el modelo YOLOv8.
 * Incluye soporte multilenguaje y acceso por ID de clase.
 */
enum class AnimalType(val id: Int, private val defaultName: String) {

    BIRD(14, "Bird"),
    CAT(15, "Cat"),
    DOG(16, "Dog"),
    HORSE(17, "Horse"),
    SHEEP(18, "Sheep"),
    COW(19, "Cow"),
    ELEPHANT(20, "Elephant"),
    BEAR(21, "Bear"),
    ZEBRA(22, "Zebra"),
    GIRAFFE(23, "Giraffe");

    /**
     * Obtiene el nombre traducido según el idioma.
     * @param lang Código ISO del idioma (es, en, it, fr, de, ru)
     */
    fun getLocalizedName(lang: String = "es"): String {
        return when (lang.lowercase(Locale.getDefault())) {
            "es" -> when (this) {
                BIRD -> "Ave"
                CAT -> "Gato"
                DOG -> "Perro"
                HORSE -> "Caballo"
                SHEEP -> "Oveja"
                COW -> "Vaca"
                ELEPHANT -> "Elefante"
                BEAR -> "Oso"
                ZEBRA -> "Cebra"
                GIRAFFE -> "Jirafa"
            }
            "en" -> defaultName
            "it" -> when (this) {
                BIRD -> "Uccello"
                CAT -> "Gatto"
                DOG -> "Cane"
                HORSE -> "Cavallo"
                SHEEP -> "Pecora"
                COW -> "Mucca"
                ELEPHANT -> "Elefante"
                BEAR -> "Orso"
                ZEBRA -> "Zebra"
                GIRAFFE -> "Giraffa"
            }
            "fr" -> when (this) {
                BIRD -> "Oiseau"
                CAT -> "Chat"
                DOG -> "Chien"
                HORSE -> "Cheval"
                SHEEP -> "Mouton"
                COW -> "Vache"
                ELEPHANT -> "Éléphant"
                BEAR -> "Ours"
                ZEBRA -> "Zèbre"
                GIRAFFE -> "Girafe"
            }
            "de" -> when (this) {
                BIRD -> "Vogel"
                CAT -> "Katze"
                DOG -> "Hund"
                HORSE -> "Pferd"
                SHEEP -> "Schaf"
                COW -> "Kuh"
                ELEPHANT -> "Elefant"
                BEAR -> "Bär"
                ZEBRA -> "Zebra"
                GIRAFFE -> "Giraffe"
            }
            "ru" -> when (this) {
                BIRD -> "Птица"
                CAT -> "Кошка"
                DOG -> "Собака"
                HORSE -> "Лошадь"
                SHEEP -> "Овца"
                COW -> "Корова"
                ELEPHANT -> "Слон"
                BEAR -> "Медведь"
                ZEBRA -> "Зебра"
                GIRAFFE -> "Жираф"
            }
            else -> defaultName
        }
    }

    companion object {
        /**
         * Obtiene un tipo AnimalType según su ID YOLO.
         */
        fun fromId(id: Int): AnimalType? = values().find { it.id == id }

        /**
         * Devuelve todos los IDs reconocidos por el modelo.
         */
        fun getAllIds(): List<Int> = values().map { it.id }
    }
}
