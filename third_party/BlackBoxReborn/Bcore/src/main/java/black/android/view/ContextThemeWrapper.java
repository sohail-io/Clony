package black.android.view;

import android.content.res.Resources;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BField;
import top.niunaijun.blackreflection.annotation.BFieldSetNotProcess;

@BClassName("android.view.ContextThemeWrapper")
public interface ContextThemeWrapper {
    @BField
    Resources mResources();

    @BFieldSetNotProcess
    void _set_mResources(Resources resources);
}
