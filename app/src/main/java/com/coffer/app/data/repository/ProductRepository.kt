package com.coffer.app.data.repository

import com.coffer.app.data.local.dao.ProductDao
import com.coffer.app.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val productDao: ProductDao
) {
    fun getAllProducts(): Flow<List<ProductEntity>> = productDao.getAllProducts()

    suspend fun createProduct(name: String, buyPriceCents: Long, sellPriceCents: Long): Int =
        productDao.insert(ProductEntity(name = name, buyPriceCents = buyPriceCents, sellPriceCents = sellPriceCents)).toInt()

    suspend fun updateProduct(product: ProductEntity, name: String, buyPriceCents: Long, sellPriceCents: Long) {
        productDao.update(product.copy(name = name, buyPriceCents = buyPriceCents, sellPriceCents = sellPriceCents))
    }

    suspend fun deleteProduct(product: ProductEntity) {
        productDao.delete(product)
    }
}
