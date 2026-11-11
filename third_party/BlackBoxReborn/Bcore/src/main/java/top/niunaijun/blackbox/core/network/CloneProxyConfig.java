package top.niunaijun.blackbox.core.network;
import java.nio.charset.StandardCharsets;
public final class CloneProxyConfig {
 public final int type, port;
 public final String host, username, password;
 public CloneProxyConfig(int type, String host, int port, String username, String password) {
  this.type=type; this.host=host; this.port=port; this.username=username; this.password=password;
 }
 public static CloneProxyConfig parse(int type, String value) {
  if(type==0)return new CloneProxyConfig(0,"",0,"","");
  if(type!=1&&type!=2)throw new IllegalArgumentException("Choose SOCKS5, HTTP or No proxy.");
  String[] parts=value.split(":",4);
  if(parts.length!=2&&parts.length!=4)throw new IllegalArgumentException("Use host:port or host:port:user:pass.");
  parts[0]=parts[0].trim(); parts[1]=parts[1].trim();
  validateHost(parts[0]);
  int port;
  try {port=Integer.parseInt(parts[1]);}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid proxy port.");}
  if(port<1||port>65535)throw new IllegalArgumentException("Port must be 1–65535.");
  String user=parts.length==4?parts[2]:"", pass=parts.length==4?parts[3]:"";
  if(parts.length==4&&(user.isEmpty()||pass.isEmpty()))throw new IllegalArgumentException("Enter both username and password, or use host:port.");
  if(user.indexOf('\r')>=0||user.indexOf('\n')>=0||pass.indexOf('\r')>=0||pass.indexOf('\n')>=0||user.indexOf('\0')>=0||pass.indexOf('\0')>=0)throw new IllegalArgumentException("Credentials cannot contain control separators.");
  if(user.getBytes(StandardCharsets.UTF_8).length>255||pass.getBytes(StandardCharsets.UTF_8).length>255)throw new IllegalArgumentException("Credentials must be at most 255 UTF-8 bytes each.");
  return new CloneProxyConfig(type,parts[0],port,user,pass);
 }
 private static void validateHost(String host) {
  if(host.isEmpty()||host.length()>253)throw new IllegalArgumentException("Enter a valid IPv4 address or proxy hostname.");
  if(host.matches("[0-9.]+")) {
   String[] octets=host.split("\\.",-1);
   if(octets.length!=4)throw new IllegalArgumentException("Enter a valid IPv4 address or proxy hostname.");
   for(String octet:octets) {
    if(!octet.matches("[0-9]{1,3}")||Integer.parseInt(octet)>255)throw new IllegalArgumentException("Invalid IPv4 address.");
    if(octet.length()>1&&octet.charAt(0)=='0')throw new IllegalArgumentException("Remove leading zeros from the IP address.");
   }
   return;
  }
  String dnsName=host.endsWith(".")?host.substring(0,host.length()-1):host;
  if(dnsName.isEmpty())throw new IllegalArgumentException("Enter a valid IPv4 address or proxy hostname.");
  for(String label:dnsName.split("\\.",-1)) {
   if(label.isEmpty()||label.length()>63||label.charAt(0)=='-'||label.charAt(label.length()-1)=='-')
    throw new IllegalArgumentException("Enter a valid IPv4 address or proxy hostname.");
   for(int i=0;i<label.length();i++) {
    char c=label.charAt(i);
    if(!((c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='-'))
     throw new IllegalArgumentException("Enter a valid IPv4 address or proxy hostname.");
   }
  }
 }
 public String input() {return type==0?"":host+":"+port+(username.isEmpty()?"":":"+username+":"+password);}
 public String label() {return type==0?"No proxy":(type==1?"SOCKS5 ":"HTTP ")+host+":"+port;}
}
