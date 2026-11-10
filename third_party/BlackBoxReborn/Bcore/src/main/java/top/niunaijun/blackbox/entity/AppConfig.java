package top.niunaijun.blackbox.entity;

import android.os.IBinder;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;



public class AppConfig implements Parcelable {
    public static final String KEY = "BlackBox_client_config";

    public String packageName;
    public String processName;
    public int bpid;
    public int buid;
    public int uid;
    public int userId;
    public int callingBUid;
    public IBinder token;
    public int proxyType;
    public int proxyPort;
    public String proxyHost = "";
    public String proxyUser = "";
    public String proxyPassword = "";

    /** Framework-only extras survive Android's system-side intent inspection. */
    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString("packageName", packageName);
        bundle.putString("processName", processName);
        bundle.putInt("bpid", bpid);
        bundle.putInt("buid", buid);
        bundle.putInt("uid", uid);
        bundle.putInt("userId", userId);
        bundle.putInt("callingBUid", callingBUid);
        bundle.putBinder("token", token);
        bundle.putInt("proxyType", proxyType);
        bundle.putInt("proxyPort", proxyPort);
        bundle.putString("proxyHost", proxyHost);
        bundle.putString("proxyUser", proxyUser);
        bundle.putString("proxyPassword", proxyPassword);
        return bundle;
    }

    public static AppConfig fromBundle(Bundle bundle) {
        AppConfig config = new AppConfig();
        config.packageName = bundle.getString("packageName");
        config.processName = bundle.getString("processName");
        config.bpid = bundle.getInt("bpid", -1);
        config.buid = bundle.getInt("buid");
        config.uid = bundle.getInt("uid");
        config.userId = bundle.getInt("userId", -1);
        config.callingBUid = bundle.getInt("callingBUid");
        config.token = bundle.getBinder("token");
        config.proxyType = bundle.getInt("proxyType", 0);
        config.proxyPort = bundle.getInt("proxyPort", 0);
        config.proxyHost = bundle.getString("proxyHost", "");
        config.proxyUser = bundle.getString("proxyUser", "");
        config.proxyPassword = bundle.getString("proxyPassword", "");
        return config;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.packageName);
        dest.writeString(this.processName);
        dest.writeInt(this.bpid);
        dest.writeInt(this.buid);
        dest.writeInt(this.uid);
        dest.writeInt(this.userId);
        dest.writeInt(this.callingBUid);
        dest.writeStrongBinder(token);
        dest.writeInt(proxyType);
        dest.writeInt(proxyPort);
        dest.writeString(proxyHost);
        dest.writeString(proxyUser);
        dest.writeString(proxyPassword);
    }

    public AppConfig() {
    }

    protected AppConfig(Parcel in) {
        this.packageName = in.readString();
        this.processName = in.readString();
        this.bpid = in.readInt();
        this.buid = in.readInt();
        this.uid = in.readInt();
        this.userId = in.readInt();
        this.callingBUid = in.readInt();
        this.token = in.readStrongBinder();
        this.proxyType = in.readInt();
        this.proxyPort = in.readInt();
        this.proxyHost = in.readString();
        this.proxyUser = in.readString();
        this.proxyPassword = in.readString();
    }

    public static final Parcelable.Creator<AppConfig> CREATOR = new Parcelable.Creator<AppConfig>() {
        @Override
        public AppConfig createFromParcel(Parcel source) {
            return new AppConfig(source);
        }

        @Override
        public AppConfig[] newArray(int size) {
            return new AppConfig[size];
        }
    };
}
