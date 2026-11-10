#include "ProxyTunnel.h"
#include <arpa/inet.h>
#include <poll.h>
#include <chrono>
#include <cerrno>
#include <cstdint>
#include <cstring>
#include <functional>
#include <netdb.h>
namespace CloneProxy {
static long long now(){return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();}
static bool transfer(int fd,void* data,size_t size,bool writing,long long deadline){
 auto p=(unsigned char*)data;
 while(size){
  int ms=(int)(deadline-now());if(ms<=0){errno=ETIMEDOUT;return false;}
  pollfd f{fd,(short)(writing?POLLOUT:POLLIN),0};int r=poll(&f,1,ms);
  if(r<0&&errno==EINTR)continue;if(r<=0){if(!r)errno=ETIMEDOUT;return false;}
  ssize_t n=writing?send(fd,p,size,MSG_DONTWAIT|MSG_NOSIGNAL):recv(fd,p,size,MSG_DONTWAIT);
  if(n<0&&(errno==EINTR||errno==EAGAIN))continue;
  if(n<=0){errno=ECONNRESET;return false;}p+=n;size-=n;
 }return true;
}
static std::string base64(const std::string& data){
 const char* alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
 std::string out;unsigned value=0;int bits=-6;
 for(unsigned char c:data){value=(value<<8)|c;bits+=8;while(bits>=0){out+=alphabet[(value>>bits)&63];bits-=6;}}
 if(bits>-6)out+=alphabet[((value<<8)>>(bits+8))&63];while(out.size()%4)out+='=';return out;
}
static bool socks5Authenticate(int fd,const Config& c,const std::function<bool(const std::string&)>& put,
                               const std::function<bool(void*,size_t)>& get){
 bool auth=!c.user.empty();char hello[3]={5,1,(char)(auth?2:0)},reply[4];
 if(!put(std::string(hello,3))||!get(reply,2))return false;
 if(reply[0]!=5||reply[1]!=(auth?2:0)){errno=EACCES;return false;}
 if(auth){
  std::string login;login+=(char)1;login+=(char)c.user.size();login+=c.user;
  login+=(char)c.password.size();login+=c.password;
  if(!put(login)||!get(reply,2))return false;
  if(reply[0]!=1||reply[1]!=0){errno=EACCES;return false;}
 }
 return true;
}
bool handshake(int fd,const sockaddr* address,const Config& c,int timeoutMs,int* httpStatus){
 if(httpStatus)*httpStatus=0;
 long long end=now()+timeoutMs;
 auto put=[&](const std::string& b){return transfer(fd,(void*)b.data(),b.size(),true,end);};
 auto get=[&](void* b,size_t n){return transfer(fd,b,n,false,end);};
 std::string target;char ip[INET6_ADDRSTRLEN];uint16_t port;
 if(address->sa_family==AF_INET){auto a=(const sockaddr_in*)address;inet_ntop(AF_INET,&a->sin_addr,ip,sizeof(ip));target=ip;port=ntohs(a->sin_port);}
 else if(address->sa_family==AF_INET6){auto a=(const sockaddr_in6*)address;inet_ntop(AF_INET6,&a->sin6_addr,ip,sizeof(ip));target="["+std::string(ip)+"]";port=ntohs(a->sin6_port);}
 else {errno=EAFNOSUPPORT;return false;}
 if(c.type==2){
  std::string authority=target+":"+std::to_string(port);
  std::string request="CONNECT "+authority+" HTTP/1.1\r\nHost: "+authority+"\r\n";
  if(!c.user.empty())request+="Proxy-Authorization: Basic "+base64(c.user+":"+c.password)+"\r\n";
  if(!put(request+"\r\n"))return false;
  std::string response;char b;
  if(!get(&b,1))return false;
  if(b!='H'){errno=EPROTO;return false;}
  response+=b;
  while(response.size()<8192){
   if(!get(&b,1))return false;
   response+=b;
   if(response.size()>=4&&response.substr(response.size()-4)=="\r\n\r\n")break;
  }
  if(response.size()<4||response.substr(response.size()-4)!="\r\n\r\n"){
   errno=EPROTO;return false;
  }
  size_t lineEnd=response.find("\r\n");
  if(lineEnd==std::string::npos){errno=EPROTO;return false;}
  std::string statusLine=response.substr(0,lineEnd);
  if(statusLine.compare(0,9,"HTTP/1.1 ")!=0&&statusLine.compare(0,9,"HTTP/1.0 ")!=0){
   errno=EPROTO;return false;
  }
  if(statusLine.size()<12||statusLine[9]<'0'||statusLine[9]>'9'||
     statusLine[10]<'0'||statusLine[10]>'9'||statusLine[11]<'0'||statusLine[11]>'9'||
     (statusLine.size()>12&&statusLine[12]!=' ')){
   errno=EPROTO;return false;
  }
  int status=(statusLine[9]-'0')*100+(statusLine[10]-'0')*10+(statusLine[11]-'0');
  if(httpStatus)*httpStatus=status;
  bool ok=status>=200&&status<300;
  if(!ok)errno=EACCES;
  return ok;
 }
 if(c.type!=1){errno=EINVAL;return false;}
 char reply[4];
 if(!socks5Authenticate(fd,c,put,get))return false;
 std::string request("\5\1\0",3);
 if(address->sa_family==AF_INET){request+=(char)1;request.append((char*)&((const sockaddr_in*)address)->sin_addr,4);}
 else {request+=(char)4;request.append((char*)&((const sockaddr_in6*)address)->sin6_addr,16);}
 request+=(char)(port>>8);request+=(char)port;
 if(!put(request)||!get(reply,4))return false;
 if(reply[0]!=5||reply[1]!=0||reply[2]!=0){errno=ECONNREFUSED;return false;}
 unsigned count=reply[3]==1?4:reply[3]==4?16:0;
 if(reply[3]==3){unsigned char length;if(!get(&length,1))return false;count=length;}
 if(!count){errno=EPROTO;return false;}char bound[257];return get(bound,count+2);
}

bool udpAssociate(int fd,const Config& c,sockaddr_storage* relay,socklen_t* relaySize,int timeoutMs){
 if(c.type!=1||relay==nullptr||relaySize==nullptr){errno=EINVAL;return false;}
 long long end=now()+timeoutMs;
 auto put=[&](const std::string& b){return transfer(fd,(void*)b.data(),b.size(),true,end);};
 auto get=[&](void* b,size_t n){return transfer(fd,b,n,false,end);};
 if(!socks5Authenticate(fd,c,put,get))return false;

 const char request[10]={5,3,0,1,0,0,0,0,0,0};
 if(!put(std::string(request,sizeof(request))))return false;
 unsigned char head[4];
 if(!get(head,sizeof(head)))return false;
 if(head[0]!=5||head[1]!=0||head[2]!=0){errno=EACCES;return false;}

 sockaddr_storage result{};
 uint16_t port=0;
 if(head[3]==1){
  unsigned char value[6];
  if(!get(value,sizeof(value)))return false;
  auto address=(sockaddr_in*)&result;
  address->sin_family=AF_INET;
  std::memcpy(&address->sin_addr,value,4);
  std::memcpy(&port,value+4,2);
  address->sin_port=port;
  if(address->sin_addr.s_addr==htonl(INADDR_ANY)){
   if(inet_pton(AF_INET,c.host.c_str(),&address->sin_addr)!=1){errno=EAFNOSUPPORT;return false;}
  }
  *relaySize=sizeof(sockaddr_in);
 }else if(head[3]==4){
  unsigned char value[18];
  if(!get(value,sizeof(value)))return false;
  auto address=(sockaddr_in6*)&result;
  address->sin6_family=AF_INET6;
  std::memcpy(&address->sin6_addr,value,16);
  std::memcpy(&port,value+16,2);
  address->sin6_port=port;
  if(IN6_IS_ADDR_UNSPECIFIED(&address->sin6_addr)){
   in_addr proxy{};
   if(inet_pton(AF_INET,c.host.c_str(),&proxy)!=1){errno=EAFNOSUPPORT;return false;}
   address->sin6_addr.s6_addr[10]=0xff;
   address->sin6_addr.s6_addr[11]=0xff;
   std::memcpy(&address->sin6_addr.s6_addr[12],&proxy,4);
  }
  *relaySize=sizeof(sockaddr_in6);
 }else if(head[3]==3){
  unsigned char length=0;
  if(!get(&length,1))return false;
  if(length==0||length>253){errno=EPROTO;return false;}
  std::string hostname(length,'\0');
  unsigned char portBytes[2];
  if(!get(&hostname[0],length)||!get(portBytes,sizeof(portBytes)))return false;
  std::memcpy(&port,portBytes,2);
  addrinfo hints{};hints.ai_family=AF_INET;hints.ai_socktype=SOCK_DGRAM;
  addrinfo* resolved=nullptr;
  if(getaddrinfo(hostname.c_str(),nullptr,&hints,&resolved)!=0||resolved==nullptr){errno=EHOSTUNREACH;return false;}
  auto address=(sockaddr_in*)&result;
  *address=*(sockaddr_in*)resolved->ai_addr;
  address->sin_port=port;
  freeaddrinfo(resolved);
  *relaySize=sizeof(sockaddr_in);
 }else {errno=EPROTO;return false;}
 if(port==0){errno=EPROTO;return false;}
 *relay=result;
 return true;
}
}
