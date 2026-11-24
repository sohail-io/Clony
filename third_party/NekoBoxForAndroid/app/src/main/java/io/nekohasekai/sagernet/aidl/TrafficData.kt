package io.nekohasekai.sagernet.aidl

import android.os.Parcelable
import android.os.Parcel

data class TrafficData(
    var id: Long = 0L,
    var tx: Long = 0L,
    var rx: Long = 0L,
) : Parcelable {
    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(id)
        dest.writeLong(tx)
        dest.writeLong(rx)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<TrafficData> {
            override fun createFromParcel(source: Parcel) = TrafficData(
                source.readLong(), source.readLong(), source.readLong()
            )
            override fun newArray(size: Int): Array<TrafficData?> = arrayOfNulls(size)
        }
    }
}
