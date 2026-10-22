package com.easylife.app.core.data.repo

import com.easylife.app.core.data.db.BudgetDao
import com.easylife.app.core.data.db.CategoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Spec 6.3 default categories (editable). Colors are ARGB longs. */
object DefaultCategories {
    val all = listOf(
        CategoryEntity("groceries", "Groceries", "سودا سلف", "ShoppingBasket", 0xFF2E7D32, sortOrder = 0),
        CategoryEntity("food", "Food & dining", "کھانا پینا", "Restaurant", 0xFFEF6C00, sortOrder = 1),
        CategoryEntity("transport", "Transport & fuel", "سفر اور پیٹرول", "LocalGasStation", 0xFF1565C0, sortOrder = 2),
        CategoryEntity("bills", "Bills & utilities", "بل اور یوٹیلیٹی", "ReceiptLong", 0xFF6A1B9A, sortOrder = 3),
        CategoryEntity("mobile", "Mobile & internet", "موبائل اور انٹرنیٹ", "PhoneAndroid", 0xFF00838F, sortOrder = 4),
        CategoryEntity("education", "Education", "تعلیم", "School", 0xFF283593, sortOrder = 5),
        CategoryEntity("health", "Health", "صحت", "LocalHospital", 0xFFC62828, sortOrder = 6),
        CategoryEntity("shopping", "Shopping", "خریداری", "ShoppingBag", 0xFFAD1457, sortOrder = 7),
        CategoryEntity("family", "Family & gifts", "خاندان اور تحائف", "CardGiftcard", 0xFF8D6E63, sortOrder = 8),
        CategoryEntity("charity", "Charity / Zakat / Sadqa", "خیرات / زکوٰۃ / صدقہ", "VolunteerActivism", 0xFF558B2F, sortOrder = 9),
        CategoryEntity("rent", "Rent", "کرایہ", "Home", 0xFF5D4037, sortOrder = 10),
        CategoryEntity("transfers", "Transfers", "رقم کی منتقلی", "SwapHoriz", 0xFF546E7A, sortOrder = 11),
        CategoryEntity("cash", "Cash withdrawal", "نقد رقم نکلوانا", "LocalAtm", 0xFF757575, sortOrder = 12),
        CategoryEntity("income", "Income", "آمدنی", "Savings", 0xFF2E7D32, sortOrder = 13),
        CategoryEntity("other", "Other", "دیگر", "Category", 0xFF9E9E9E, sortOrder = 14),
    )
}

@Singleton
class CategoryRepository @Inject constructor(private val dao: BudgetDao) {
    val categories: Flow<List<CategoryEntity>> = dao.observeCategories()

    suspend fun ensureDefaults() {
        val existing = dao.getCategories().map { it.key }.toSet()
        val missing = DefaultCategories.all.filter { it.key !in existing }
        if (missing.isNotEmpty()) dao.upsertCategories(missing)
    }

    suspend fun setBudget(key: String, amount: Double?) = dao.setBudget(key, amount?.takeIf { it > 0 })

    suspend fun all(): List<CategoryEntity> = dao.getCategories()
}
