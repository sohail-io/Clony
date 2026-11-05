package top.niunaijun.blackbox.app;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import top.niunaijun.blackbox.fake.frameworks.BAccountManager;

/** Host-side account picker for account records stored by a virtual clone. */
public final class VirtualAccountChooserActivity extends Activity {
    public static final String EXTRA_VIRTUAL_USER_ID =
            "top.niunaijun.blackbox.extra.VIRTUAL_ACCOUNT_USER_ID";

    private static final String TAG = "VirtualAccountChooser";
    private static final String EXTRA_ALLOWABLE_TYPES = "allowableAccountTypes";
    private static final String EXTRA_ALLOWABLE_ACCOUNTS = "allowableAccounts";
    private static final String EXTRA_SELECTED_ACCOUNT = "selectedAccount";
    private static final String EXTRA_ALWAYS_PROMPT = "alwaysPromptForAccount";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int virtualUserId = getIntent().getIntExtra(EXTRA_VIRTUAL_USER_ID, -1);
        if (virtualUserId < 0) {
            Log.w(TAG, "Account picker received no virtual user id");
            finishWithCancel();
            return;
        }

        Set<String> allowedTypes = readAllowedTypes();
        Set<Account> allowedAccounts = readAllowedAccounts();
        List<Account> accounts = new ArrayList<>();
        Account[] stored = BAccountManager.get().getAccountsAsUser(null, virtualUserId);
        if (stored != null) {
            for (Account account : stored) {
                if (account == null) continue;
                if (allowedTypes != null && !allowedTypes.contains(account.type)) continue;
                if (allowedAccounts != null && !allowedAccounts.contains(account)) continue;
                accounts.add(account);
            }
        }

        Log.i(TAG, "Loaded clone accounts for picker; user=" + virtualUserId
                + ", count=" + accounts.size());
        if (accounts.isEmpty()) {
            finishWithCancel();
            return;
        }

        Account selected = readSelectedAccount();
        boolean alwaysPrompt = getIntent().getBooleanExtra(EXTRA_ALWAYS_PROMPT, false);
        if (!alwaysPrompt && selected != null && accounts.contains(selected)) {
            finishWithAccount(selected);
            return;
        }
        if (!alwaysPrompt && accounts.size() == 1) {
            finishWithAccount(accounts.get(0));
            return;
        }

        CharSequence description = getIntent().getCharSequenceExtra("descriptionTextOverride");
        CharSequence[] labels = new CharSequence[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) {
            labels[i] = accounts.get(i).name;
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(description != null ? description : "Choose an account")
                .setItems(labels, (ignored, which) -> finishWithAccount(accounts.get(which)))
                .setOnCancelListener(ignored -> finishWithCancel())
                .create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private Set<String> readAllowedTypes() {
        String[] values = getIntent().getStringArrayExtra(EXTRA_ALLOWABLE_TYPES);
        return values == null ? null : new HashSet<>(Arrays.asList(values));
    }

    private Set<Account> readAllowedAccounts() {
        try {
            Parcelable[] values = getIntent().getParcelableArrayExtra(EXTRA_ALLOWABLE_ACCOUNTS);
            if (values == null) return null;
            Set<Account> accounts = new HashSet<>();
            for (Parcelable value : values) {
                if (value instanceof Account) accounts.add((Account) value);
            }
            return accounts;
        } catch (RuntimeException malformedExtra) {
            Log.w(TAG, "Ignoring malformed allowable-account list", malformedExtra);
            return null;
        }
    }

    private Account readSelectedAccount() {
        try {
            Parcelable value = getIntent().getParcelableExtra(EXTRA_SELECTED_ACCOUNT);
            return value instanceof Account ? (Account) value : null;
        } catch (RuntimeException malformedExtra) {
            Log.w(TAG, "Ignoring malformed selected-account value", malformedExtra);
            return null;
        }
    }

    private void finishWithAccount(Account account) {
        Intent result = new Intent();
        result.putExtra(AccountManager.KEY_ACCOUNT_NAME, account.name);
        result.putExtra(AccountManager.KEY_ACCOUNT_TYPE, account.type);
        setResult(RESULT_OK, result);
        Log.i(TAG, "Returned selected clone account; type=" + account.type);
        finish();
    }

    private void finishWithCancel() {
        setResult(RESULT_CANCELED);
        finish();
    }
}
