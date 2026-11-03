package com.equinoxtrack.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.House
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.equinoxtrack.app.domain.AssetKind

/** Stable string keys stored in the database, mapped to vector icons. */
object AppIcons {
    val all: Map<String, ImageVector> = linkedMapOf(
        "wallet" to Icons.Rounded.AccountBalanceWallet,
        "bank" to Icons.Rounded.AccountBalance,
        "phone" to Icons.Rounded.PhoneAndroid,
        "cash" to Icons.Rounded.Payments,
        "savings" to Icons.Rounded.Savings,
        "restaurant" to Icons.Rounded.Restaurant,
        "coffee" to Icons.Rounded.LocalCafe,
        "grocery" to Icons.Rounded.LocalGroceryStore,
        "bus" to Icons.Rounded.DirectionsBus,
        "car" to Icons.Rounded.DirectionsCar,
        "home" to Icons.Rounded.Home,
        "house" to Icons.Rounded.House,
        "bolt" to Icons.Rounded.Bolt,
        "wifi" to Icons.Rounded.Wifi,
        "mobile" to Icons.Rounded.PhoneIphone,
        "subscriptions" to Icons.Rounded.Subscriptions,
        "receipt" to Icons.Rounded.Receipt,
        "bag" to Icons.Rounded.ShoppingBag,
        "health" to Icons.Rounded.LocalHospital,
        "fitness" to Icons.Rounded.FitnessCenter,
        "game" to Icons.Rounded.SportsEsports,
        "movie" to Icons.Rounded.Movie,
        "music" to Icons.Rounded.MusicNote,
        "school" to Icons.Rounded.School,
        "spa" to Icons.Rounded.Spa,
        "flight" to Icons.Rounded.Flight,
        "beach" to Icons.Rounded.BeachAccess,
        "gift" to Icons.Rounded.CardGiftcard,
        "giving" to Icons.Rounded.VolunteerActivism,
        "pets" to Icons.Rounded.Pets,
        "child" to Icons.Rounded.ChildCare,
        "heart" to Icons.Rounded.Favorite,
        "shield" to Icons.Rounded.Shield,
        "build" to Icons.Rounded.Build,
        "work" to Icons.Rounded.Work,
        "laptop" to Icons.Rounded.Laptop,
        "store" to Icons.Rounded.Storefront,
        "refund" to Icons.Rounded.Replay,
        "trending" to Icons.Rounded.Insights,
        "watch" to Icons.Rounded.Watch,
        "star" to Icons.Rounded.Star,
        "tag" to Icons.Rounded.LocalOffer,
        "more" to Icons.Rounded.MoreHoriz,
    )

    fun of(key: String?): ImageVector = all[key] ?: Icons.Rounded.Category

    fun asset(kind: AssetKind): ImageVector = when (kind) {
        AssetKind.PROPERTY -> Icons.Rounded.House
        AssetKind.VEHICLE -> Icons.Rounded.DirectionsCar
        AssetKind.VALUABLES -> Icons.Rounded.Watch
        AssetKind.COLLECTIBLES -> Icons.Rounded.Star
        AssetKind.OTHER -> Icons.Rounded.Category
    }

    val categoryChoices = listOf(
        "restaurant", "coffee", "grocery", "bus", "car", "home", "bolt", "wifi", "mobile", "subscriptions",
        "receipt", "bag", "health", "fitness", "game", "movie", "music", "school", "spa", "flight", "beach",
        "gift", "giving", "pets", "child", "heart", "shield", "build", "work", "laptop", "store", "refund",
        "trending", "savings", "tag", "more",
    )
    val accountChoices = listOf("wallet", "cash", "bank", "phone", "savings", "shield", "star", "store", "work", "home")
    val goalChoices = listOf("savings", "flight", "beach", "home", "car", "school", "laptop", "heart", "gift", "shield", "star", "fitness")
}
