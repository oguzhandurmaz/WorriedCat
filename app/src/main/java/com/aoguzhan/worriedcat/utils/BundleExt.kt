@file:Suppress("DEPRECATION")

package com.aoguzhan.worriedcat.utils

import android.content.Intent
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import java.io.Serializable

inline fun <reified T : Serializable> Bundle.serializable(key: String): T? = when {
    SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getSerializable(key, T::class.java)
    else -> getSerializable(key) as? T
}

inline fun <reified T : Serializable> Intent.serializable(key: String): T? = when {
    SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getSerializableExtra(key, T::class.java)
    else -> getSerializableExtra(key) as? T
}

inline fun <reified T: Parcelable> Intent.parcelable(key: String): T? = when{
    SDK_INT >= Build.VERSION_CODES.TIRAMISU -> parcelableExtra(key)
    else -> getParcelableExtra(key)
}

inline fun <reified T : Parcelable> Intent.parcelableExtra(key: String): T? =
    IntentCompat.getParcelableExtra(this, key, T::class.java)

inline fun <reified T : Parcelable> Intent.parcelableArrayExtra(key: String): Array<out Parcelable>? =
    IntentCompat.getParcelableArrayExtra(this,key,T::class.java)

inline fun <reified T : Parcelable> Intent.parcelableArrayListExtra(key: String): ArrayList<T>? =
    IntentCompat.getParcelableArrayListExtra(this, key, T::class.java)


inline fun <reified T : Parcelable> Bundle.parcelable(key: String): T? =
    BundleCompat.getParcelable(this, key, T::class.java)

inline fun <reified T : Parcelable> Bundle.parcelableArray(key: String): Array<out Parcelable>? =
    BundleCompat.getParcelableArray(this, key, T::class.java)

inline fun <reified T : Parcelable> Bundle.parcelableArrayList(key: String): ArrayList<T>? =
    BundleCompat.getParcelableArrayList(this, key, T::class.java)

inline fun <reified T : Parcelable> Bundle.sparseParcelableArray(key: String): SparseArray<T>? =
    BundleCompat.getSparseParcelableArray(this, key, T::class.java)
