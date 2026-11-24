@file:JvmName("Utils")

package com.github.shadowsocks.plugin

import android.os.Parcelable
import android.os.Parcel

class Empty : Parcelable {
    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = Unit

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<Empty> {
            override fun createFromParcel(source: Parcel) = Empty()
            override fun newArray(size: Int): Array<Empty?> = arrayOfNulls(size)
        }
    }
}
