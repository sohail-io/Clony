package top.niunaijun.blackbox.core.network;
import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Atomic encrypted files avoid stale cross-process SharedPreferences reads. */
public final class CloneProxyStore {
 private static final String ALIAS="clony_proxy_credentials_v1";
 private static File path(Context c,String pkg,int user) {
  if(!pkg.matches("[A-Za-z0-9_.]+")||user<0)throw new IllegalArgumentException("Invalid clone identity");
  File dir=new File(c.getFilesDir(),"clone-proxies");
  if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Couldn't create proxy settings storage");
  return new File(dir,pkg+"_"+user+".bin");
 }
 private static javax.crypto.SecretKey key(boolean create) throws Exception {
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(!store.containsAlias(ALIAS)) {
   if(!create)throw new IllegalStateException("Proxy credential key is unavailable");
   KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore");
   generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
   generator.generateKey();
  }
  return (javax.crypto.SecretKey)store.getKey(ALIAS,null);
 }
 public static synchronized CloneProxyConfig read(Context c,String pkg,int user) {
  AtomicFile file=new AtomicFile(path(c,pkg,user));
  if(!file.getBaseFile().exists()&&!new File(file.getBaseFile().getPath()+".bak").exists())return CloneProxyConfig.parse(0,"");
  try {
   byte[] bytes=file.readFully();
   if(bytes.length<29)throw new IOException("Invalid settings");
   Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
   cipher.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,bytes,0,12));
   cipher.updateAAD((pkg+":"+user).getBytes(StandardCharsets.UTF_8));
   JSONObject json=new JSONObject(new String(cipher.doFinal(bytes,12,bytes.length-12),StandardCharsets.UTF_8));
   return CloneProxyConfig.parse(json.getInt("type"),json.getString("input"));
  }catch(Exception error){throw new IllegalStateException("Cannot read this clone's proxy settings; refusing direct fallback.");}
 }
 public static synchronized void save(Context c,String pkg,int user,CloneProxyConfig config) {
  AtomicFile file=new AtomicFile(path(c,pkg,user));
  if(config.type==0){file.delete();if(file.getBaseFile().exists())throw new IllegalStateException("Couldn't remove proxy settings");return;}
  FileOutputStream out=null;
  try {
   Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));
   cipher.updateAAD((pkg+":"+user).getBytes(StandardCharsets.UTF_8));
   JSONObject json=new JSONObject();json.put("type",config.type);json.put("input",config.input());
   byte[] encrypted=cipher.doFinal(json.toString().getBytes(StandardCharsets.UTF_8));
   out=file.startWrite();out.write(cipher.getIV());out.write(encrypted);file.finishWrite(out);
  }catch(Exception error){if(out!=null)file.failWrite(out);throw new IllegalStateException("Couldn't securely save proxy settings.");}
 }
 public static synchronized void delete(Context c,String pkg,int user) {
  AtomicFile file=new AtomicFile(path(c,pkg,user));
  file.delete();
  if(file.getBaseFile().exists())throw new IllegalStateException("Couldn't remove proxy settings");
 }
}
