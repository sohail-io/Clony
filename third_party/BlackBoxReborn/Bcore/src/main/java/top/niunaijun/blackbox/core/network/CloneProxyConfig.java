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
  if(parts.length!=2&&parts.length!=4)throw new IllegalArgumentException("Use IP:port or IP:port:user:pass.");
  parts[0]=parts[0].trim(); parts[1]=parts[1].trim();
  String[] octets=parts[0].split("\\.",-1);
  if(octets.length!=4)throw new IllegalArgumentException("Enter the proxy's IPv4 address.");
  for(String octet:octets) {
   if(!octet.matches("[0-9]{1,3}")||Integer.parseInt(octet)>255)throw new IllegalArgumentException("Invalid IPv4 address.");
   if(octet.length()>1&&octet.charAt(0)=='0')throw new IllegalArgumentException("Remove leading zeros from the IP address.");
  }
  int port;
  try {port=Integer.parseInt(parts[1]);}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid proxy port.");}
  if(port<1||port>65535)throw new IllegalArgumentException("Port must be 1–65535.");
  String user=parts.length==4?parts[2]:"", pass=parts.length==4?parts[3]:"";
  if(parts.length==4&&(user.isEmpty()||pass.isEmpty()))throw new IllegalArgumentException("Enter both username and password, or use IP:port.");
  if(user.indexOf('\r')>=0||user.indexOf('\n')>=0||pass.indexOf('\r')>=0||pass.indexOf('\n')>=0||user.indexOf('\0')>=0||pass.indexOf('\0')>=0)throw new IllegalArgumentException("Credentials cannot contain control separators.");
  if(user.getBytes(StandardCharsets.UTF_8).length>255||pass.getBytes(StandardCharsets.UTF_8).length>255)throw new IllegalArgumentException("Credentials must be at most 255 UTF-8 bytes each.");
  return new CloneProxyConfig(type,parts[0],port,user,pass);
 }
 public String input() {return type==0?"":host+":"+port+(username.isEmpty()?"":":"+username+":"+password);}
 public String label() {return type==0?"No proxy":(type==1?"SOCKS5 ":"HTTP ")+host+":"+port;}
}
