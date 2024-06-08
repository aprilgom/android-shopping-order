package com.example.data.datasource.local.room.entity.cart

import com.example.data.datasource.local.room.entity.product.toProduct
import com.example.data.datasource.local.room.entity.product.toProductEntity
import com.example.domain.model.CartItem

fun CartItemEntity.toCartItem() = CartItem(id, product.toProduct(), quantity)

fun List<CartItemEntity>.toCartItems() = map { it.toCartItem() }

fun CartItem.toCartItemEntity() = CartItemEntity(id, product.toProductEntity(), quantity)

fun List<CartItem>.toCartItemEntities() = map { it.toCartItemEntity() }
