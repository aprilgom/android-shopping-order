package woowacourse.shopping.presentation.detail

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isNotEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import woowacourse.shopping.R
import woowacourse.shopping.data.datasource.local.room.entity.product.ProductEntity
import woowacourse.shopping.fakerepository.FakeCartRepository
import woowacourse.shopping.fakerepository.FakeRecentProductRepository
import woowacourse.shopping.firstProduct

@RunWith(AndroidJUnit4::class)
class ProductEntityDetailActivityTest {
    private val productEntity: ProductEntity = firstProduct
    private lateinit var intent: Intent

    @Before
    fun setUp() {
        com.example.domain.repository.RecentProductRepository.setInstance(
            FakeRecentProductRepository(),
        )
        com.example.domain.repository.CartRepository.setInstance(FakeCartRepository())
        intent =
            Intent(ApplicationProvider.getApplicationContext(), ProductDetailActivity::class.java)
                .putExtra("product_id_key", productEntity.productId)
    }

    @Test
    fun `상품_제목이_보인다`() {
        ActivityScenario.launch<ProductDetailActivity>(intent)
        onView(withId(R.id.tv_product_detail_title))
            .check(matches(withText(productEntity.name)))
    }

    @Test
    fun `아직_장바구니에_상품이_담기지_않은_경우_상품_가격_0원이_보인다`() {
        ActivityScenario.launch<ProductDetailActivity>(intent)

        onView(withId(R.id.tv_product_detail_price))
            .check(matches(withText("0원")))
    }

    @Test
    fun `상품을_3개_담으면_상품_3개의_가격이_보인다`() {
        ActivityScenario.launch<ProductDetailActivity>(intent)

        repeat(3) {
            onView(withId(R.id.btn_cart_quantity_plus))
                .perform(click())
        }

        val expected = "%,d원".format(productEntity.price * 3)
        onView(withId(R.id.tv_product_detail_price))
            .check(matches(withText(expected)))
    }

    @Test
    fun `상품_개수가_0개인_경우_장바구니_담기_버튼이_비활성화_된다`() {
        ActivityScenario.launch<ProductDetailActivity>(intent)

        onView(withId(R.id.btn_product_detail_add_cart))
            .check(matches(isNotEnabled()))
    }

    @Test
    fun `상품_개수가_1개_이상인_경우_장바구니_담기_버튼이_활성화_된다`() {
        ActivityScenario.launch<ProductDetailActivity>(intent)

        onView(withId(R.id.btn_cart_quantity_plus))
            .perform(click())

        onView(withId(R.id.btn_product_detail_add_cart))
            .check(matches(isEnabled()))
    }
}
