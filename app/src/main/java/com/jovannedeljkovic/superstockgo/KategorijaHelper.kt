package com.jovannedeljkovic.superstockgo

object KategorijaHelper {

    // Ekstraktuj čisti naziv iz pune kategorije (bez emoji)
    fun extractCleanName(fullCategory: String): String {
        return fullCategory.replace(Regex("^[\\p{So}\\s]+"), "").trim()
    }

    // Kreiraj punu kategoriju (sa emoji)
    fun createFullCategory(emoji: String, name: String): String {
        return "$emoji $name".trim()
    }

    // Proveri da li su dve kategorije iste (ignoriše emoji)
    fun areCategoriesEqual(category1: String, category2: String): Boolean {
        val clean1 = extractCleanName(category1)
        val clean2 = extractCleanName(category2)
        return clean1 == clean2
    }

    // Pronađi sve proizvode sa određenom kategorijom
    fun findProductsByCategory(products: List<Proizvod>, categoryName: String): List<Proizvod> {
        val cleanCategoryName = extractCleanName(categoryName)
        return products.filter { product ->
            val cleanProductCategory = extractCleanName(product.kategorija)
            cleanProductCategory == cleanCategoryName
        }
    }
}