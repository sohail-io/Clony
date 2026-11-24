package io.nekohasekai.sagernet.aidl

import android.os.Parcelable
import android.os.Parcel

data class SpeedDisplayData(
    // Bytes per second
    var txRateProxy: Long = 0L,
    var rxRateProxy: Long = 0L,
    var txRateDirect: Long = 0L,
    var rxRateDirect: Long = 0L,

    // Bytes for the current session
    // Outbound "bypass" usage is not counted
    var txTotal: Long = 0L,
    var rxTotal: Long = 0L,
) : Parcelable {
    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(txRateProxy)
        dest.writeLong(rxRateProxy)
        dest.writeLong(txRateDirect)
        dest.writeLong(rxRateDirect)
        dest.writeLong(txTotal)
        dest.writeLong(rxTotal)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<SpeedDisplayData> {
            override fun createFromParcel(source: Parcel) = SpeedDisplayData(
                source.readLong(), source.readLong(), source.readLong(), source.readLong(),
                source.readLong(), source.readLong()
            )
            override fun newArray(size: Int): Array<SpeedDisplayData?> = arrayOfNulls(size)
        }
    }
}
