package com.wonderfood.app.data

fun classifyStorageZone(value: String): StorageZone {
    val text = value.lowercase()
    return when {
        listOf("frozen", "freezer", "ice cream", "popsicle").any { it in text } -> StorageZone.FREEZER
        listOf(
            "milk",
            "yogurt",
            "curd",
            "dahi",
            "paneer",
            "egg",
            "cheese",
            "spinach",
            "lettuce",
            "chicken",
            "fish",
            "tofu",
            "berries",
            "cilantro",
            "parsley",
            "cucumber",
            "eggplant",
            "brinjal",
            "tomato",
            "pepper",
            "jalap",
            "lime",
            "lemon",
            "grape",
        ).any { it in text } -> StorageZone.FRIDGE
        else -> StorageZone.PANTRY
    }
}

fun categorizeFood(value: String): String {
    val text = value.lowercase()
    return when {
        listOf("chicken", "fish", "salmon", "beef", "egg", "tofu", "bean", "lentil", "dal", "yogurt", "curd", "dahi", "paneer").any { it in text } -> "protein"
        listOf("spinach", "lettuce", "greens", "broccoli", "carrot", "pepper", "jalap", "tomato", "cucumber", "potato", "onion", "bhindi", "okra", "lauki", "gourd", "brinjal", "eggplant").any { it in text } -> "produce"
        listOf("berry", "banana", "apple", "orange", "grape", "lime", "lemon", "fruit").any { it in text } -> "fruit"
        listOf("rice", "oat", "pasta", "bread", "tortilla", "cereal").any { it in text } -> "grain"
        listOf("milk", "cheese", "cream", "yogurt", "curd", "dahi", "paneer").any { it in text } -> "dairy"
        listOf("water", "juice", "soda", "coffee", "tea", "drink", "beverage").any { it in text } -> "beverage"
        listOf("oil", "butter", "avocado", "nuts", "peanut").any { it in text } -> "fat"
        else -> "other"
    }
}
