package black.android.app;

import android.content.Intent;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BField;

@BClassName("android.app.ResultInfo")
public interface ResultInfo {
    @BField
    Intent mData();

    @BField
    int mResultCode();
}
