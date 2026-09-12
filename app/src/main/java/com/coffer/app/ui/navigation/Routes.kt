package com.coffer.app.ui.navigation

object Routes {
    const val DASHBOARD = "dashboard"
    const val CONTACTS = "contacts"
    const val PRODUCTS = "products"
    const val ACTIVITY = "activity"
    const val CONTACT_DETAIL = "contactDetail/{contactId}"
    const val ORDER_DETAIL = "orderDetail/{orderId}"
    const val NEW_ORDER = "newOrder?contactId={contactId}"

    fun contactDetail(contactId: Int) = "contactDetail/$contactId"
    fun orderDetail(orderId: Int) = "orderDetail/$orderId"
    fun newOrder(contactId: Int? = null) = if (contactId != null) "newOrder?contactId=$contactId" else "newOrder"
}
