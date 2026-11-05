package top.niunaijun.blackbox.core.system.pm;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.InstrumentationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageParser;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.os.Bundle;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import top.niunaijun.blackbox.entity.pm.InstallOption;
import top.niunaijun.blackbox.utils.compat.BuildCompat;


public class BPackage implements Parcelable {
    private static final int PARCEL_VERSION_MARKER = -1;
    private static final int PARCEL_VERSION = 1;

    public ArrayList<Activity> activities = new ArrayList<Activity>(0);
    public ArrayList<Activity> receivers = new ArrayList<Activity>(0);
    public ArrayList<Provider> providers = new ArrayList<Provider>(0);
    public ArrayList<Service> services = new ArrayList<Service>(0);
    public ArrayList<Instrumentation> instrumentation = new ArrayList<Instrumentation>(0);
    public ArrayList<Permission> permissions = new ArrayList<Permission>(0);
    public ArrayList<PermissionGroup> permissionGroups = new ArrayList<PermissionGroup>(0);
    public ArrayList<String> requestedPermissions = new ArrayList<String>();
    public Signature[] mSignatures;
    public SigningDetails mSigningDetails;
    public Bundle mAppMetaData;
    public BPackageSettings mExtras;
    public String packageName;
    public int mPreferredOrder;
    public String mSharedUserId;
    public ArrayList<String> usesLibraries;
    public ArrayList<String> usesOptionalLibraries;
    public int mVersionCode;
    public ApplicationInfo applicationInfo;
    public String mVersionName;
    public String baseCodePath;
    public String[] splitNames = new String[0];
    public String[] splitCodePaths = new String[0];

    public int mSharedUserLabel;
    
    public ArrayList<ConfigurationInfo> configPreferences = null;
    
    public ArrayList<FeatureInfo> reqFeatures = null;

    public InstallOption installOption;

    public BPackage(PackageParser.Package aPackage) {
        this.activities = new ArrayList<>(aPackage.activities.size());
        for (PackageParser.Activity activity : aPackage.activities) {
            Activity selfActivity = new Activity(activity);
            for (ActivityIntentInfo intent : selfActivity.intents) {
                intent.activity = selfActivity;
            }
            selfActivity.owner = this;
            this.activities.add(selfActivity);
        }

        this.receivers = new ArrayList<>(aPackage.receivers.size());
        for (PackageParser.Activity receiver : aPackage.receivers) {
            Activity selfReceiver = new Activity(receiver);
            for (ActivityIntentInfo intent : selfReceiver.intents) {
                intent.activity = selfReceiver;
            }
            selfReceiver.owner = this;
            this.receivers.add(selfReceiver);
        }

        this.providers = new ArrayList<>(aPackage.providers.size());
        for (PackageParser.Provider provider : aPackage.providers) {
            Provider selfProvider = new Provider(provider);
            for (ProviderIntentInfo intent : selfProvider.intents) {
                intent.provider = selfProvider;
            }
            selfProvider.owner = this;
            this.providers.add(selfProvider);
        }

        this.services = new ArrayList<>(aPackage.services.size());
        for (PackageParser.Service service : aPackage.services) {
            Service selfService = new Service(service);
            for (ServiceIntentInfo intent : selfService.intents) {
                intent.service = selfService;
            }
            selfService.owner = this;
            this.services.add(selfService);
        }

        this.instrumentation = new ArrayList<>(aPackage.instrumentation.size());
        for (PackageParser.Instrumentation instrumentation1 : aPackage.instrumentation) {
            Instrumentation selfInstrumentation = new Instrumentation(instrumentation1);
            selfInstrumentation.owner = this;
            this.instrumentation.add(selfInstrumentation);
        }

        this.permissions = new ArrayList<>(aPackage.permissions.size());
        for (PackageParser.Permission permission : aPackage.permissions) {
            Permission selfPermission = new Permission(permission);
            selfPermission.owner = this;
            this.permissions.add(selfPermission);
        }

        this.permissionGroups = new ArrayList<>(aPackage.permissionGroups.size());
        for (PackageParser.PermissionGroup permissionGroup : aPackage.permissionGroups) {
            PermissionGroup selfPermissionGroup = new PermissionGroup(permissionGroup);
            selfPermissionGroup.owner = this;
            this.permissionGroups.add(selfPermissionGroup);
        }

        this.requestedPermissions = aPackage.requestedPermissions;
        if (Build.VERSION.SDK_INT >= 33) {
            this.mSignatures = aPackage.mSignatures;
        } else if (BuildCompat.isPie()) {
            this.mSigningDetails = new SigningDetails(aPackage.mSigningDetails);
            this.mSignatures = this.mSigningDetails.signatures;
        } else {
            this.mSignatures = aPackage.mSignatures;
        }
        this.mAppMetaData = aPackage.mAppMetaData;
        
        this.packageName = aPackage.packageName;
        this.mPreferredOrder = aPackage.mPreferredOrder;
        this.mSharedUserId = aPackage.mSharedUserId;
        this.usesLibraries = aPackage.usesLibraries;
        this.usesOptionalLibraries = aPackage.usesOptionalLibraries;
        this.mVersionCode = aPackage.mVersionCode;
        this.applicationInfo = aPackage.applicationInfo;
        this.mVersionName = aPackage.mVersionName;
        this.baseCodePath = aPackage.baseCodePath;
        this.mSharedUserLabel = aPackage.mSharedUserLabel;
        this.configPreferences = aPackage.configPreferences;
        this.reqFeatures = aPackage.reqFeatures;
    }

    /**
     * Builds the virtual package model from public PackageManager metadata for an
     * already-installed app. Unlike PackageParser.Package, PackageInfo is a public
     * API and includes components from installed split APKs.
     */
    public BPackage(PackageInfo packageInfo, String[] installedSplitNames,
                    String[] installedSplitPaths, List<ResolveInfo> entryPointFilters) {
        ApplicationInfo sourceApplication = packageInfo.applicationInfo;
        this.applicationInfo = sourceApplication == null ? null : new ApplicationInfo(sourceApplication);
        this.packageName = packageInfo.packageName;
        this.baseCodePath = sourceApplication == null ? null : sourceApplication.sourceDir;
        this.splitNames = installedSplitNames == null ? new String[0] : installedSplitNames.clone();
        this.splitCodePaths = installedSplitPaths == null ? new String[0] : installedSplitPaths.clone();
        if (this.applicationInfo != null) {
            this.applicationInfo.splitNames = this.splitNames.clone();
            this.applicationInfo.splitSourceDirs = this.splitCodePaths.clone();
            this.applicationInfo.splitPublicSourceDirs = this.splitCodePaths.clone();
        }

        if (packageInfo.activities != null) {
            for (ActivityInfo info : packageInfo.activities) {
                if (info == null || info.name == null) continue;
                Activity activity = new Activity(info);
                addEntryPointFilters(activity, entryPointFilters);
                activity.owner = this;
                for (ActivityIntentInfo intent : activity.intents) intent.activity = activity;
                activities.add(activity);
            }
        }
        if (packageInfo.receivers != null) {
            for (ActivityInfo info : packageInfo.receivers) {
                if (info == null || info.name == null) continue;
                Activity receiver = new Activity(info);
                receiver.owner = this;
                receivers.add(receiver);
            }
        }
        if (packageInfo.services != null) {
            for (ServiceInfo info : packageInfo.services) {
                if (info == null || info.name == null) continue;
                Service service = new Service(info);
                service.owner = this;
                services.add(service);
            }
        }
        if (packageInfo.providers != null) {
            for (ProviderInfo info : packageInfo.providers) {
                if (info == null || info.name == null) continue;
                Provider provider = new Provider(info);
                provider.owner = this;
                providers.add(provider);
            }
        }
        if (packageInfo.instrumentation != null) {
            for (InstrumentationInfo info : packageInfo.instrumentation) {
                if (info == null || info.name == null) continue;
                Instrumentation item = new Instrumentation(info);
                item.owner = this;
                instrumentation.add(item);
            }
        }
        if (packageInfo.permissions != null) {
            for (PermissionInfo info : packageInfo.permissions) {
                if (info == null || info.name == null) continue;
                Permission permission = new Permission(info);
                permission.owner = this;
                permissions.add(permission);
            }
        }

        this.requestedPermissions = new ArrayList<>();
        if (packageInfo.requestedPermissions != null) {
            Collections.addAll(this.requestedPermissions, packageInfo.requestedPermissions);
        }
        if (packageInfo.signingInfo != null) {
            Signature[] currentSigners = packageInfo.signingInfo.getApkContentsSigners();
            this.mSignatures = currentSigners == null ? new Signature[0] : currentSigners.clone();
        } else {
            this.mSignatures = packageInfo.signatures == null
                    ? new Signature[0] : packageInfo.signatures.clone();
        }
        this.mSigningDetails = new SigningDetails(this.mSignatures);
        this.mAppMetaData = this.applicationInfo == null ? null : this.applicationInfo.metaData;
        this.mSharedUserId = packageInfo.sharedUserId;
        this.mSharedUserLabel = packageInfo.sharedUserLabel;
        this.mVersionCode = packageInfo.versionCode;
        this.mVersionName = packageInfo.versionName;
        this.configPreferences = packageInfo.configPreferences == null
                ? null : new ArrayList<>(java.util.Arrays.asList(packageInfo.configPreferences));
        this.reqFeatures = packageInfo.reqFeatures == null
                ? null : new ArrayList<>(java.util.Arrays.asList(packageInfo.reqFeatures));
    }

    private static void addEntryPointFilters(Activity activity, List<ResolveInfo> entryPointFilters) {
        if (entryPointFilters == null) return;
        ComponentName component = new ComponentName(activity.info.packageName, activity.info.name);
        for (ResolveInfo resolveInfo : entryPointFilters) {
            if (resolveInfo == null || resolveInfo.activityInfo == null
                    || !component.getPackageName().equals(resolveInfo.activityInfo.packageName)
                    || !component.getClassName().equals(resolveInfo.activityInfo.name)) {
                continue;
            }
            IntentFilter filter = resolveInfo.filter == null
                    ? new IntentFilter(Intent.ACTION_MAIN) : new IntentFilter(resolveInfo.filter);
            if (resolveInfo.filter == null) filter.addCategory(Intent.CATEGORY_LAUNCHER);
            IntentInfo intentInfo = new IntentInfo(filter, filter.hasCategory(Intent.CATEGORY_DEFAULT));
            ActivityIntentInfo activityIntentInfo = new ActivityIntentInfo(intentInfo);
            activityIntentInfo.activity = activity;
            activity.intents.add(activityIntentInfo);
        }
    }

    protected BPackage(Parcel in) {
        int first = in.readInt();
        boolean versioned = first == PARCEL_VERSION_MARKER;
        if (versioned) in.readInt(); // Reserved for parcel migrations.
        int N = versioned ? in.readInt() : first;
        this.activities = new ArrayList<>(N);
        while (N-- > 0) {
            Activity activity = new Activity(in);
            for (ActivityIntentInfo intent : activity.intents) {
                intent.activity = activity;
            }
            activity.owner = this;
            this.activities.add(activity);
        }

        N = in.readInt();
        this.receivers = new ArrayList<>(N);
        while (N-- > 0) {
            Activity activity = new Activity(in);
            for (ActivityIntentInfo intent : activity.intents) {
                intent.activity = activity;
            }
            activity.owner = this;
            this.receivers.add(activity);
        }

        N = in.readInt();
        this.providers = new ArrayList<>(N);
        while (N-- > 0) {
            Provider provider = new Provider(in);
            for (ProviderIntentInfo intent : provider.intents) {
                intent.provider = provider;
            }
            provider.owner = this;
            this.providers.add(provider);
        }

        N = in.readInt();
        this.services = new ArrayList<>(N);
        while (N-- > 0) {
            Service service = new Service(in);
            for (ServiceIntentInfo intent : service.intents) {
                intent.service = service;
            }
            service.owner = this;
            this.services.add(service);
        }

        N = in.readInt();
        this.instrumentation = new ArrayList<>(N);
        while (N-- > 0) {
            Instrumentation instrumentation = new Instrumentation(in);
            instrumentation.owner = this;
            this.instrumentation.add(instrumentation);
        }

        N = in.readInt();
        this.permissions = new ArrayList<>(N);
        while (N-- > 0) {
            Permission permission = new Permission(in);
            permission.owner = this;
            this.permissions.add(permission);
        }

        N = in.readInt();
        this.permissionGroups = new ArrayList<>(N);
        while (N-- > 0) {
            PermissionGroup permissionGroup = new PermissionGroup(in);
            permissionGroup.owner = this;
            this.permissionGroups.add(permissionGroup);
        }

        in.readStringList(this.requestedPermissions);
        if (BuildCompat.isPie()) {
            this.mSigningDetails = in.readParcelable(SigningDetails.class.getClassLoader());
        }
        this.mSignatures = in.createTypedArray(Signature.CREATOR);
        this.mAppMetaData = in.readBundle(Bundle.class.getClassLoader());

        this.packageName = in.readString();
        this.mPreferredOrder = in.readInt();
        this.mSharedUserId = in.readString();
        this.usesLibraries = in.createStringArrayList();
        this.usesOptionalLibraries = in.createStringArrayList();
        this.mVersionCode = in.readInt();
        this.applicationInfo = in.readParcelable(ApplicationInfo.class.getClassLoader());
        this.mVersionName = in.readString();
        this.baseCodePath = in.readString();
        this.mSharedUserLabel = in.readInt();
        this.configPreferences = in.createTypedArrayList(ConfigurationInfo.CREATOR);
        this.reqFeatures = in.createTypedArrayList(FeatureInfo.CREATOR);
        this.installOption = in.readParcelable(InstallOption.class.getClassLoader());
        if (versioned) {
            this.splitNames = in.createStringArray();
            this.splitCodePaths = in.createStringArray();
            if (this.splitNames == null) this.splitNames = new String[0];
            if (this.splitCodePaths == null) this.splitCodePaths = new String[0];
        }
    }

    public final static class Activity extends Component<ActivityIntentInfo> {
        public ActivityInfo info;

        public Activity(PackageParser.Activity activity) {
            super(activity);
            this.info = activity.info;
            if (activity.intents != null) {
                int size = activity.intents.size();
                this.intents = new ArrayList<>(size);
                for (PackageParser.ActivityIntentInfo intent : activity.intents) {
                    this.intents.add(new ActivityIntentInfo(intent));
                }
            }
        }

        public Activity(ActivityInfo activityInfo) {
            this.info = new ActivityInfo(activityInfo);
            this.className = this.info.name;
            this.metaData = this.info.metaData;
            this.intents = new ArrayList<>();
        }

        public Activity(Parcel parcel) {
            super(parcel);
            this.info = parcel.readParcelable(ActivityInfo.class.getClassLoader());
            int N = parcel.readInt();
            this.intents = new ArrayList<>(N);
            while (N-- > 0) {
                IntentInfo intentInfo = parcel.readParcelable(BPackage.class.getClassLoader());
                this.intents.add(new ActivityIntentInfo(intentInfo));
            }
        }
    }

    public static final class Service extends Component<ServiceIntentInfo> {
        public ServiceInfo info;

        public Service(PackageParser.Service service) {
            super(service);
            info = service.info;
            if (service.intents != null) {
                int size = service.intents.size();
                intents = new ArrayList<>(size);
                for (PackageParser.ServiceIntentInfo intent : service.intents) {
                    intents.add(new ServiceIntentInfo(intent));
                }
            }
        }

        public Service(ServiceInfo serviceInfo) {
            this.info = new ServiceInfo(serviceInfo);
            this.className = this.info.name;
            this.metaData = this.info.metaData;
            this.intents = new ArrayList<>();
        }

        public Service(Parcel parcel) {
            super(parcel);
            info = parcel.readParcelable(ServiceInfo.class.getClassLoader());
            int N = parcel.readInt();
            intents = new ArrayList<>(N);
            while (N-- > 0) {
                IntentInfo intentInfo = parcel.readParcelable(BPackage.class.getClassLoader());
                intents.add(new ServiceIntentInfo(intentInfo));
            }
        }
    }

    public static final class Provider extends Component<ProviderIntentInfo> {
        public ProviderInfo info;

        public Provider(PackageParser.Provider provider) {
            super(provider);
            info = provider.info;
            if (provider.intents != null) {
                int size = provider.intents.size();
                intents = new ArrayList<>(size);
                for (PackageParser.ProviderIntentInfo intent : provider.intents) {
                    intents.add(new ProviderIntentInfo(intent));
                }
            }
        }

        public Provider(ProviderInfo providerInfo) {
            this.info = new ProviderInfo(providerInfo);
            this.className = this.info.name;
            this.metaData = this.info.metaData;
            this.intents = new ArrayList<>();
        }

        public Provider(Parcel parcel) {
            super(parcel);
            info = parcel.readParcelable(ProviderInfo.class.getClassLoader());
            int N = parcel.readInt();
            intents = new ArrayList<>(N);
            while (N-- > 0) {
                IntentInfo intentInfo = parcel.readParcelable(BPackage.class.getClassLoader());
                intents.add(new ProviderIntentInfo(intentInfo));
            }
        }
    }

    public static final class Instrumentation extends Component<IntentInfo> {
        public InstrumentationInfo info;

        public Instrumentation(PackageParser.Instrumentation instrumentation) {
            super(instrumentation);
            info = instrumentation.info;
            if (instrumentation.intents != null) {
                int size = instrumentation.intents.size();
                this.intents = new ArrayList<>(size);
                for (PackageParser.IntentInfo intent : instrumentation.intents) {
                    this.intents.add(new IntentInfo(intent));
                }
            }
        }

        public Instrumentation(InstrumentationInfo instrumentationInfo) {
            this.info = new InstrumentationInfo(instrumentationInfo);
            this.className = this.info.name;
            this.metaData = this.info.metaData;
            this.intents = new ArrayList<>();
        }

        public Instrumentation(Parcel parcel) {
            super(parcel);
            this.info = parcel.readParcelable(InstrumentationInfo.class.getClassLoader());
            int N = parcel.readInt();
            this.intents = new ArrayList<>(N);
            while (N-- > 0) {
                IntentInfo intentInfo = parcel.readParcelable(BPackage.class.getClassLoader());
                this.intents.add(intentInfo);
            }
        }
    }

    public static final class Permission extends Component<IntentInfo> {
        public PermissionInfo info;

        public Permission(PackageParser.Permission permission) {
            super(permission);
            this.info = permission.info;
            if (permission.intents != null) {
                int size = permission.intents.size();
                this.intents = new ArrayList<>(size);
                for (PackageParser.IntentInfo intent : permission.intents) {
                    this.intents.add(new IntentInfo(intent));
                }
            }
        }

        public Permission(PermissionInfo permissionInfo) {
            this.info = new PermissionInfo(permissionInfo);
            this.className = this.info.name;
            this.metaData = this.info.metaData;
            this.intents = new ArrayList<>();
        }

        public Permission(Parcel parcel) {
            super(parcel);
            this.info = parcel.readParcelable(Permission.class.getClassLoader());
            int N = parcel.readInt();
            this.intents = new ArrayList<>(N);
            while (N-- > 0) {
                IntentInfo intentInfo = parcel.readParcelable(BPackage.class.getClassLoader());
                this.intents.add(intentInfo);
            }
        }
    }

    public static final class PermissionGroup extends Component<IntentInfo> {
        public PermissionGroupInfo info;

        public PermissionGroup(PackageParser.PermissionGroup group) {
            super(group);
            this.info = group.info;
            if (group.intents != null) {
                int size = group.intents.size();
                this.intents = new ArrayList<>(size);
                for (PackageParser.IntentInfo intent : group.intents) {
                    this.intents.add(new IntentInfo(intent));
                }
            }
        }

        public PermissionGroup(Parcel parcel) {
            super(parcel);
            this.info = parcel.readParcelable(PermissionGroup.class.getClassLoader());
            int N = parcel.readInt();
            this.intents = new ArrayList<>(N);
            while (N-- > 0) {
                IntentInfo intentInfo = parcel.readParcelable(BPackage.class.getClassLoader());
                this.intents.add(intentInfo);
            }
        }
    }

    public static class ActivityIntentInfo extends IntentInfo {
        public Activity activity;

        public ActivityIntentInfo(PackageParser.IntentInfo intentInfo) {
            super(intentInfo);
        }

        public ActivityIntentInfo(IntentInfo intentInfo) {
            super(intentInfo);
        }
    }

    public static class ServiceIntentInfo extends IntentInfo {
        public Service service;

        public ServiceIntentInfo(PackageParser.IntentInfo intentInfo) {
            super(intentInfo);
        }

        public ServiceIntentInfo(IntentInfo intentInfo) {
            super(intentInfo);
        }
    }

    public static class ProviderIntentInfo extends IntentInfo {
        public Provider provider;

        public ProviderIntentInfo(PackageParser.IntentInfo intentInfo) {
            super(intentInfo);
        }

        public ProviderIntentInfo(IntentInfo intentInfo) {
            super(intentInfo);
        }
    }

    public static final class SigningDetails implements Parcelable {
        public Signature[] signatures;

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeTypedArray(this.signatures, flags);
        }

        public SigningDetails(PackageParser.SigningDetails signingDetails) {
            if (signingDetails.pastSigningCertificates == null) {
                this.signatures = signingDetails.signatures;
            } else {
                this.signatures = signingDetails.pastSigningCertificates;
            }
        }

        public SigningDetails(Signature[] signatures) {
            this.signatures = signatures == null ? new Signature[0] : signatures.clone();
        }

        protected SigningDetails(Parcel in) {
            this.signatures = in.createTypedArray(Signature.CREATOR);
        }

        public static final Creator<SigningDetails> CREATOR = new Creator<SigningDetails>() {
            @Override
            public SigningDetails createFromParcel(Parcel source) {
                return new SigningDetails(source);
            }

            @Override
            public SigningDetails[] newArray(int size) {
                return new SigningDetails[size];
            }
        };
    }

    public static class IntentInfo implements Parcelable {
        public IntentFilter intentFilter;
        public boolean hasDefault;
        public int labelRes;
        public String nonLocalizedLabel;
        public int icon;
        public int logo;
        public int banner;

        public IntentInfo(PackageParser.IntentInfo intentInfo) {
            this.intentFilter = intentInfo;
            this.hasDefault = intentInfo.hasDefault;
            this.labelRes = intentInfo.labelRes;
            this.nonLocalizedLabel = intentInfo.nonLocalizedLabel == null ? null : intentInfo.nonLocalizedLabel.toString();
            this.icon = intentInfo.icon;
            this.logo = intentInfo.logo;
            this.banner = intentInfo.banner;
        }

        public IntentInfo(IntentFilter intentFilter, boolean hasDefault) {
            this.intentFilter = intentFilter;
            this.hasDefault = hasDefault;
        }

        public IntentInfo(IntentInfo intentInfo) {
            this.intentFilter = intentInfo.intentFilter;
            this.hasDefault = intentInfo.hasDefault;
            this.labelRes = intentInfo.labelRes;
            this.nonLocalizedLabel = intentInfo.nonLocalizedLabel == null ? null : intentInfo.nonLocalizedLabel.toString();
            this.icon = intentInfo.icon;
            this.logo = intentInfo.logo;
            this.banner = intentInfo.banner;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeParcelable(this.intentFilter, flags);
            dest.writeByte(this.hasDefault ? (byte) 1 : (byte) 0);
            dest.writeInt(this.labelRes);
            dest.writeString(this.nonLocalizedLabel);
            dest.writeInt(this.icon);
            dest.writeInt(this.logo);
            dest.writeInt(this.banner);
        }

        protected IntentInfo(Parcel in) {
            this.intentFilter = in.readParcelable(BPackage.class.getClassLoader());
            this.hasDefault = in.readByte() != 0;
            this.labelRes = in.readInt();
            this.nonLocalizedLabel = in.readString();
            this.icon = in.readInt();
            this.logo = in.readInt();
            this.banner = in.readInt();
        }

        public static final Creator<IntentInfo> CREATOR = new Creator<IntentInfo>() {
            @Override
            public IntentInfo createFromParcel(Parcel source) {
                return new IntentInfo(source);
            }

            @Override
            public IntentInfo[] newArray(int size) {
                return new IntentInfo[size];
            }
        };
    }

    public static class Component<II extends BPackage.IntentInfo> {
        public BPackage owner;
        public ArrayList<II> intents;
        public String className;
        public Bundle metaData;
        public ComponentName componentName;

        public Component() {
            this.intents = new ArrayList<>();
        }

        public Component(Parcel parcel) {
            this.className = parcel.readString();
            this.metaData = parcel.readBundle(Bundle.class.getClassLoader());
        }

        public Component(PackageParser.Component<?> component) {
            this.className = component.className;
            this.metaData = component.metaData;
        }

        public ComponentName getComponentName() {
            if (componentName != null) {
                return componentName;
            }
            if (className != null) {
                componentName = new ComponentName(owner.packageName,
                        className);
            }
            return componentName;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(PARCEL_VERSION_MARKER);
        dest.writeInt(PARCEL_VERSION);
        int size = this.activities.size();
        dest.writeInt(size);
        for (Activity activity : this.activities) {
            dest.writeString(activity.className);
            dest.writeBundle(activity.metaData);

            dest.writeParcelable(activity.info, flags);
            if (activity.intents != null) {
                int N = activity.intents.size();
                dest.writeInt(N);
                while (N-- > 0) {
                    dest.writeParcelable(activity.intents.get(N), flags);
                }
            } else {
                dest.writeInt(0);
            }
        }

        size = this.receivers.size();
        dest.writeInt(size);
        for (Activity receiver : this.receivers) {
            dest.writeString(receiver.className);
            dest.writeBundle(receiver.metaData);

            dest.writeParcelable(receiver.info, flags);
            if (receiver.intents != null) {
                int N = receiver.intents.size();
                dest.writeInt(N);
                while (N-- > 0) {
                    dest.writeParcelable(receiver.intents.get(N), flags);
                }
            } else {
                dest.writeInt(0);
            }
        }

        size = this.providers.size();
        dest.writeInt(size);
        for (Provider provider : this.providers) {
            dest.writeString(provider.className);
            dest.writeBundle(provider.metaData);

            dest.writeParcelable(provider.info, flags);
            if (provider.intents != null) {
                int N = provider.intents.size();
                dest.writeInt(N);
                while (N-- > 0) {
                    dest.writeParcelable(provider.intents.get(N), flags);
                }
            } else {
                dest.writeInt(0);
            }
        }

        size = this.services.size();
        dest.writeInt(size);
        for (Service service : this.services) {
            dest.writeString(service.className);
            dest.writeBundle(service.metaData);

            dest.writeParcelable(service.info, flags);
            if (service.intents != null) {
                int N = service.intents.size();
                dest.writeInt(N);
                while (N-- > 0) {
                    dest.writeParcelable(service.intents.get(N), flags);
                }
            } else {
                dest.writeInt(0);
            }
        }

        size = this.instrumentation.size();
        dest.writeInt(size);
        for (Instrumentation instrumentation : this.instrumentation) {
            dest.writeString(instrumentation.className);
            dest.writeBundle(instrumentation.metaData);

            dest.writeParcelable(instrumentation.info, flags);
            if (instrumentation.intents != null) {
                int N = instrumentation.intents.size();
                dest.writeInt(N);
                while (N-- > 0) {
                    dest.writeParcelable(instrumentation.intents.get(N), flags);
                }
            } else {
                dest.writeInt(0);
            }
        }

        size = this.permissions.size();
        dest.writeInt(size);
        for (Permission permission : this.permissions) {
            dest.writeString(permission.className);
            dest.writeBundle(permission.metaData);

            dest.writeParcelable(permission.info, flags);
            if (permission.intents != null) {
                int N = permission.intents.size();
                dest.writeInt(N);
                while (N-- > 0) {
                    dest.writeParcelable(permission.intents.get(N), flags);
                }
            } else {
                dest.writeInt(0);
            }
        }

        size = this.permissionGroups.size();
        dest.writeInt(size);
        for (PermissionGroup permissionGroup : this.permissionGroups) {
            dest.writeString(permissionGroup.className);
            dest.writeBundle(permissionGroup.metaData);

            dest.writeParcelable(permissionGroup.info, flags);
            if (permissionGroup.intents != null) {
                int N = permissionGroup.intents.size();
                dest.writeInt(N);
                while (N-- > 0) {
                    dest.writeParcelable(permissionGroup.intents.get(N), flags);
                }
            } else {
                dest.writeInt(0);
            }
        }

        dest.writeStringList(this.requestedPermissions);
        if (BuildCompat.isPie()) {
            dest.writeParcelable(this.mSigningDetails, flags);
        }
        dest.writeTypedArray(this.mSignatures, flags);
        dest.writeBundle(this.mAppMetaData);

        dest.writeString(this.packageName);
        dest.writeInt(this.mPreferredOrder);
        dest.writeString(this.mSharedUserId);
        dest.writeStringList(this.usesLibraries);
        dest.writeStringList(this.usesOptionalLibraries);
        dest.writeInt(this.mVersionCode);
        dest.writeParcelable(this.applicationInfo, flags);
        dest.writeString(this.mVersionName);
        dest.writeString(this.baseCodePath);
        dest.writeInt(this.mSharedUserLabel);
        dest.writeTypedList(this.configPreferences);
        dest.writeTypedList(this.reqFeatures);
        dest.writeParcelable(this.installOption, flags);
        dest.writeStringArray(this.splitNames);
        dest.writeStringArray(this.splitCodePaths);
    }

    public static final Parcelable.Creator<BPackage> CREATOR = new Parcelable.Creator<BPackage>() {
        @Override
        public BPackage createFromParcel(Parcel source) {
            return new BPackage(source);
        }

        @Override
        public BPackage[] newArray(int size) {
            return new BPackage[size];
        }
    };
}
