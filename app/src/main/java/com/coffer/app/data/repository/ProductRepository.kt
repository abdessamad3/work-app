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

    suspend fun createProduct(name: String, defaultUnitPriceCents: Long): Int =
        productDao.insert(ProductEntity(name = name, defaultUnitPriceCents = defaultUnitPriceCents)).toInt()

    suspend fun updateProduct(product: ProductEntity, name: String, defaultUnitPriceCents: Long) {
        productDao.update(product.copy(name = name, defaultUnitPriceCents = defaultUnitPriceCents))
    }

    suspend fun deleteProduct(product: ProductEntity) {
        productDao.delete(product)
    }
}
