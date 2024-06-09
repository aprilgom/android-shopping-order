package woowacourse.shopping.presentation.cart

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.example.domain.datasource.onSuccess
import com.example.domain.model.CartItem
import com.example.domain.model.Quantity
import com.example.domain.repository.CartRepository
import com.example.domain.repository.OrderRepository
import com.example.domain.repository.RecentProductRepository
import woowacourse.shopping.common.Event
import woowacourse.shopping.presentation.products.uimodel.ProductUiModel
import kotlin.concurrent.thread

class CartViewModel(
    private val recommendRepository: RecentProductRepository,
    private val cartRepository: CartRepository,
    private val orderRepository: OrderRepository,
) : ViewModel(), CartActionHandler {
    private val _cartUiState = MutableLiveData<CartUiState>()
    val cartUiState: LiveData<CartUiState> get() = _cartUiState

    private val _changedCartEvent = MutableLiveData<Event<Unit>>()
    val changedCartEvent: LiveData<Event<Unit>> get() = _changedCartEvent

    val cartItemSelectedCount: LiveData<Int>
        get() = cartUiState.map { it.cartUiModels.count { cartUiModel -> cartUiModel.isSelected } }

    val cartItemAllSelected: LiveData<Boolean>
        get() =
            cartUiState.map { it.cartUiModels.all { cartUiModel -> cartUiModel.isSelected } }

    val totalPrice: LiveData<Int>
        get() =
            cartUiState.map { cartUiState ->
                cartUiState.cartUiModels.filter {
                    it.isSelected
                }.sumOf { it.product.price * it.quantity.count }
            }

    private val _recommendProductUiModels = MutableLiveData<List<ProductUiModel>>()
    val recommendProductUiModels: LiveData<List<ProductUiModel>> get() = _recommendProductUiModels

    private val _isSuccessCreateOrder = MutableLiveData<Event<Boolean>>()
    val isSuccessCreateOrder: LiveData<Event<Boolean>> get() = _isSuccessCreateOrder

    private val _navigateEvent = MutableLiveData<Event<Unit>>()
    val navigateEvent: LiveData<Event<Unit>> get() = _navigateEvent

    private val _checkboxVisibility = MutableLiveData<Boolean>(true)
    val checkboxVisibility: LiveData<Boolean> get() = _checkboxVisibility

    private val _orderButtonEnabled = MutableLiveData(false)
    val orderButtonEnabled: LiveData<Boolean> get() = _orderButtonEnabled

    private val handler = Handler(Looper.getMainLooper())

    init {
        loadAllCartItems()
    }

    private fun loadAllCartItems() {
        thread {
            val result = cartRepository.findAll()
            handler.post {
                result.onSuccess { cartItems ->
                    val oldCartItems = cartUiState.value?.cartUiModels ?: cartItems.toCartUiModels()
                    val newCartItems: MutableList<CartUiModel> = mutableListOf()
                    cartItems.forEach { cartItem ->
                        val oldItem = oldCartItems.find { it.toCartItem().id == cartItem.id }
                        if (oldItem != null) {
                            newCartItems.add(oldItem.copy(quantity = cartItem.quantity))
                        } else {
                            newCartItems.add(cartItem.toCartUiModel())
                        }
                    }
                    val newUiState =
                        CartUiState(
                            newCartItems,
                            isLoading = false,
                            isFailure = false,
                            isSuccess = true,
                        )
                    _cartUiState.postValue(newUiState)
                }
            }
        }
    }

    @Synchronized
    private fun updateCartUiState(cartItem: CartItem) {
        val oldCartUiModels = cartUiState.value?.cartUiModels ?: emptyList()
        val oldCartUiModel =
            findCartUiModelByProductId(cartItem.product.id) ?: cartItem.toCartUiModel()
        val newCartUiModel = oldCartUiModel.copy(quantity = cartItem.quantity)
        val newCartUiModels = oldCartUiModels.upsert(newCartUiModel).sortedBy { it.cartItemId }
        val newCartUiState = CartUiState(newCartUiModels, isSuccess = true)
        _cartUiState.postValue(newCartUiState)
    }

    override fun deleteCartItem(productId: Int) {
        _changedCartEvent.value = Event(Unit)
        val cartUiModel = findCartUiModelByProductId(productId) ?: return
        thread {
            cartRepository.deleteCartItem(cartUiModel.cartItemId)
            reloadCartUiState()
        }
    }

    private fun reloadCartUiState() {
        loadAllCartItems()
    }

    override fun increaseQuantity(productId: Int) {
        thread {
            cartRepository.increaseQuantity(productId)
            reloadCartUiState()
        }
    }

    override fun decreaseQuantity(productId: Int) {
        thread {
            cartRepository.decreaseQuantity(productId)
            reloadCartUiState()
        }
    }

    override fun selectCartItem(
        productId: Int,
        isSelected: Boolean,
    ) {
        val oldCartUiModels = cartUiState.value?.cartUiModels ?: emptyList()
        val oldCartUiModel = findCartUiModelByProductId(productId) ?: return
        if (oldCartUiModel.isSelected == isSelected) return

        val newCartUiModels = oldCartUiModels.upsert(oldCartUiModel.copy(isSelected = isSelected))
        val newCartUiState = CartUiState(newCartUiModels, isLoading = false, isFailure = false, isSuccess = true)
        _cartUiState.value = newCartUiState
    }

    private fun List<CartUiModel>.upsert(cartUiModel: CartUiModel): List<CartUiModel> {
        val list = this.toMutableList()
        if (this.none { it.cartItemId == cartUiModel.cartItemId }) {
            list += cartUiModel
        } else {
            this.forEachIndexed { index, listItem ->
                if (listItem.cartItemId == cartUiModel.cartItemId) {
                    list[index] = cartUiModel
                }
            }
        }
        return list.toList()
    }

    override fun selectAllCartItem(isChecked: Boolean) {
        if (cartItemAllSelected.value == true && isChecked) return
        cartUiState.value?.cartUiModels?.forEach {
            selectCartItem(it.product.id, isSelected = isChecked)
        }
    }

    override fun navigateCartRecommend() {
        _navigateEvent.postValue(Event(Unit))
        _checkboxVisibility.value = false
    }

    fun loadRecommendProductUiModels() {
        val cartItems = cartUiState.value?.cartUiModels?.map { it.toCartItem() } ?: return
        val recommendProducts = recommendRepository.getRecommendProducts(cartItems = cartItems)
        _recommendProductUiModels.value =
            recommendProducts.map { product ->
                val quantity = cartItems.find { it.product == product }?.quantity ?: Quantity(0)
                ProductUiModel(product, quantity)
            }
    }

    fun createOrder() {
        val cartUiModels = cartUiState.value?.cartUiModels ?: return
        val cartItemIds = cartUiModels.filter { it.isSelected }.map { it.cartItemId }
        thread {
            orderRepository.createOrder(cartItemIds)
        }
        /*
        orderRepository.createOrder(
            cartItemIds,
            object : DataCallback<Unit> {
                override fun onSuccess(result: Unit) {
                    _isSuccessCreateOrder.value = Event(true)
                    deleteCartItemIds(cartItemIds)
                }

                override fun onFailure(t: Throwable) {
                    _isSuccessCreateOrder.value = Event(false)
                }
            },
        )
         */
    }

    private fun deleteCartItemIds(cartItemIds: List<Int>) {
        _changedCartEvent.value = Event(Unit)
        thread {
            cartItemIds.forEach {
                cartRepository.deleteCartItem(it)
            }
        }
        /*
        cartItemIds.forEach {
            cartRepository.deleteCartItem(
                it,
                object : DataCallback<Unit> {
                    override fun onSuccess(result: Unit) {}

                    override fun onFailure(t: Throwable) {
                        setError()
                    }
                },
            )
        }
         */
    }

    private fun findCartUiModelByProductId(productId: Int): CartUiModel? {
        return cartUiState.value?.cartUiModels?.find { it.product.id == productId }
    }
}
