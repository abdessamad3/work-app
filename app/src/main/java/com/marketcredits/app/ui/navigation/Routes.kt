package com.marketcredits.app.ui.navigation

object Routes {
    const val PROFILE_SWITCHER = "profileSwitcher"
    const val MARKETPLACE = "marketplace"
    const val SELL = "sell"
    const val MY_PROFILE = "myProfile"
    const val ITEM_DETAIL = "itemDetail/{itemId}"

    fun itemDetail(itemId: Int) = "itemDetail/$itemId"
}
