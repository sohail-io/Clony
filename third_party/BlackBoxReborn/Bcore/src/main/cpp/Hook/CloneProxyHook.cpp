#include "ProxyTunnel.h"
#include "Dobby/dobby.h"
#include <jni.h>
#include <dlfcn.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <netdb.h>
#include <poll.h>
#include <cerrno>
#include <cstring>
#include <mutex>
#include <unordered_map>
#include <unordered_set>
#include <vector>
#include <time.h>
#include <chrono>
#include <unistd.h>

static CloneProxy::Config settings;
static int (*realConnect)(int,const sockaddr*,socklen_t)=nullptr;
static int (*realSocket)(int,int,int)=nullptr;
static int (*realClose)(int)=nullptr;
static ssize_t (*realSend)(int,const void*,size_t,int)=nullptr;
static ssize_t (*realRecv)(int,void*,size_t,int)=nullptr;
static ssize_t (*realSendTo)(int,const void*,size_t,int,const sockaddr*,socklen_t)=nullptr;
static ssize_t (*realRecvFrom)(int,void*,size_t,int,sockaddr*,socklen_t*)=nullptr;
static ssize_t (*realSendMsg)(int,const msghdr*,int)=nullptr;
static ssize_t (*realRecvMsg)(int,msghdr*,int)=nullptr;
static int (*realSendMmsg)(int,mmsghdr*,unsigned int,int)=nullptr;
static int (*realRecvMmsg)(int,mmsghdr*,unsigned int,int,timespec*)=nullptr;
static bool configured=false;
static std::mutex configLock;

struct UdpPeer {
 sockaddr_storage address{};
 socklen_t length=0;
};
static std::mutex udpLock;
static std::unordered_map<int,UdpPeer> udpPeers;
static std::unordered_set<int> proxiedUdpFds;
static int udpControlFd=-1;
static sockaddr_storage udpRelay{};
static socklen_t udpRelayLength=0;
static bool udpRelayFailed=false;
static int udpRelayError=0;
static std::chrono::steady_clock::time_point udpRelayRetryAfter{};

static bool internetAddress(const sockaddr* address){return address&&(address->sa_family==AF_INET||address->sa_family==AF_INET6);}
static bool loopbackAddress(const sockaddr* address){
 if(!address)return false;
 if(address->sa_family==AF_INET)return (ntohl(((const sockaddr_in*)address)->sin_addr.s_addr)>>24)==127;
 return address->sa_family==AF_INET6&&IN6_IS_ADDR_LOOPBACK(&((const sockaddr_in6*)address)->sin6_addr);
}
static int socketType(int fd){int type=0;socklen_t size=sizeof(type);return getsockopt(fd,SOL_SOCKET,SO_TYPE,&type,&size)==0?type:0;}

static bool failUdpRelay(int error){
 udpRelayFailed=true;
 udpRelayError=error==0?ENETUNREACH:error;
 udpRelayRetryAfter=std::chrono::steady_clock::now()+std::chrono::seconds(30);
 errno=udpRelayError;
 return false;
}

static bool ensureUdpRelay(){
 std::lock_guard<std::mutex> lock(udpLock);
 if(udpControlFd>=0)return true;
 if(udpRelayFailed&&std::chrono::steady_clock::now()<udpRelayRetryAfter){errno=udpRelayError;return false;}
 udpRelayFailed=false;udpRelayError=0;
 if(settings.type!=1||realSocket==nullptr||realConnect==nullptr)return failUdpRelay(ENETUNREACH);

 int fd=realSocket(AF_INET,SOCK_STREAM|SOCK_CLOEXEC|SOCK_NONBLOCK,0);
 if(fd<0)return failUdpRelay(errno);
 sockaddr_in endpoint{};endpoint.sin_family=AF_INET;endpoint.sin_port=htons(settings.port);
 if(inet_pton(AF_INET,settings.host.c_str(),&endpoint.sin_addr)!=1){realClose(fd);return failUdpRelay(EINVAL);}
 int result=realConnect(fd,(sockaddr*)&endpoint,sizeof(endpoint));
 bool connected=result==0;
 if(result<0&&errno==EINPROGRESS){
  pollfd item{fd,POLLOUT,0};
  int ready=poll(&item,1,3000);
  if(ready>0){int error=0;socklen_t size=sizeof(error);connected=getsockopt(fd,SOL_SOCKET,SO_ERROR,&error,&size)==0&&error==0;if(!connected)errno=error?error:ECONNREFUSED;}
  else errno=ready==0?ETIMEDOUT:errno;
 }
 if(!connected){int error=errno;realClose(fd);return failUdpRelay(error);}
 sockaddr_storage relay{};socklen_t relaySize=0;
 if(!CloneProxy::udpAssociate(fd,settings,&relay,&relaySize,3000)){
  int error=errno;realClose(fd);return failUdpRelay(error);
 }
 udpRelay=relay;udpRelayLength=relaySize;udpControlFd=fd;udpRelayFailed=false;udpRelayError=0;
 return true;
}

static bool relayForSocket(int fd,sockaddr_storage* out,socklen_t* outLength){
 if(!ensureUdpRelay())return false;
 sockaddr_storage local{};socklen_t localLength=sizeof(local);
 if(getsockname(fd,(sockaddr*)&local,&localLength)<0)return false;
 std::lock_guard<std::mutex> lock(udpLock);
 if(local.ss_family==udpRelay.ss_family){*out=udpRelay;*outLength=udpRelayLength;return true;}
 if(local.ss_family==AF_INET6&&udpRelay.ss_family==AF_INET){
  const auto* source=(const sockaddr_in*)&udpRelay;
  auto* mapped=(sockaddr_in6*)out;memset(mapped,0,sizeof(*mapped));
  mapped->sin6_family=AF_INET6;mapped->sin6_port=source->sin_port;
  mapped->sin6_addr.s6_addr[10]=0xff;mapped->sin6_addr.s6_addr[11]=0xff;
  memcpy(&mapped->sin6_addr.s6_addr[12],&source->sin_addr,4);
  *outLength=sizeof(*mapped);return true;
 }
 if(local.ss_family==AF_INET&&udpRelay.ss_family==AF_INET6){
  const auto* source=(const sockaddr_in6*)&udpRelay;
  if(!IN6_IS_ADDR_V4MAPPED(&source->sin6_addr)){errno=EAFNOSUPPORT;return false;}
  auto* address=(sockaddr_in*)out;memset(address,0,sizeof(*address));
  address->sin_family=AF_INET;address->sin_port=source->sin6_port;
  memcpy(&address->sin_addr,&source->sin6_addr.s6_addr[12],4);
  *outLength=sizeof(*address);return true;
 }
 errno=EAFNOSUPPORT;return false;
}

static bool getUdpPeer(int fd,UdpPeer* peer){
 std::lock_guard<std::mutex> lock(udpLock);
 auto found=udpPeers.find(fd);if(found==udpPeers.end())return false;
 *peer=found->second;return true;
}

static std::vector<unsigned char> socksUdpPacket(const void* data,size_t size,const sockaddr* destination){
 std::vector<unsigned char> packet;packet.reserve(size+22);
 packet.push_back(0);packet.push_back(0);packet.push_back(0);
 if(destination->sa_family==AF_INET){
  const auto* address=(const sockaddr_in*)destination;packet.push_back(1);
  const auto* bytes=(const unsigned char*)&address->sin_addr;packet.insert(packet.end(),bytes,bytes+4);
  const auto* port=(const unsigned char*)&address->sin_port;packet.insert(packet.end(),port,port+2);
 }else if(destination->sa_family==AF_INET6){
  const auto* address=(const sockaddr_in6*)destination;packet.push_back(4);
  const auto* bytes=(const unsigned char*)&address->sin6_addr;packet.insert(packet.end(),bytes,bytes+16);
  const auto* port=(const unsigned char*)&address->sin6_port;packet.insert(packet.end(),port,port+2);
 }else{errno=EAFNOSUPPORT;return {};}
 if(size>0){const auto* bytes=(const unsigned char*)data;packet.insert(packet.end(),bytes,bytes+size);}
 return packet;
}

static ssize_t sendUdp(int fd,const void* data,size_t size,int flags,const sockaddr* destination,socklen_t destinationLength){
 if(socketType(fd)!=SOCK_DGRAM)return realSendTo(fd,data,size,flags,destination,destinationLength);
 UdpPeer connectedPeer{};
 if(destination==nullptr){
  if(!getUdpPeer(fd,&connectedPeer)){errno=EDESTADDRREQ;return -1;}
  destination=(const sockaddr*)&connectedPeer.address;destinationLength=connectedPeer.length;
 }
 if(!internetAddress(destination)){return realSendTo(fd,data,size,flags,destination,destinationLength);}
 if(loopbackAddress(destination))return realSendTo(fd,data,size,flags,destination,destinationLength);
 if(settings.type!=1){errno=ENETUNREACH;return -1;}
 sockaddr_storage relay{};socklen_t relayLength=0;
 if(!relayForSocket(fd,&relay,&relayLength))return -1;
 std::vector<unsigned char> packet=socksUdpPacket(data,size,destination);
 if(packet.empty())return -1;
 ssize_t sent=realSendTo(fd,packet.data(),packet.size(),flags,(sockaddr*)&relay,relayLength);
 if(sent<0)return -1;
 {
  std::lock_guard<std::mutex> lock(udpLock);proxiedUdpFds.insert(fd);
 }
 return (ssize_t)size;
}

static bool sameAddress(const sockaddr* a,const sockaddr* b){
 if(!a||!b)return false;
 if(a->sa_family==AF_INET&&b->sa_family==AF_INET){
  const auto* left=(const sockaddr_in*)a;const auto* right=(const sockaddr_in*)b;
  return left->sin_port==right->sin_port&&left->sin_addr.s_addr==right->sin_addr.s_addr;
 }
 if(a->sa_family==AF_INET6&&b->sa_family==AF_INET6){
  const auto* left=(const sockaddr_in6*)a;const auto* right=(const sockaddr_in6*)b;
  return left->sin6_port==right->sin6_port&&memcmp(&left->sin6_addr,&right->sin6_addr,16)==0;
 }
 if(a->sa_family==AF_INET6&&b->sa_family==AF_INET){
  const auto* left=(const sockaddr_in6*)a;const auto* right=(const sockaddr_in*)b;
  return IN6_IS_ADDR_V4MAPPED(&left->sin6_addr)&&left->sin6_port==right->sin_port&&memcmp(&left->sin6_addr.s6_addr[12],&right->sin_addr,4)==0;
 }
 if(a->sa_family==AF_INET&&b->sa_family==AF_INET6)return sameAddress(b,a);
 return false;
}

static void adaptAddressToSocket(int fd,sockaddr_storage* address,socklen_t* length){
 sockaddr_storage local{};socklen_t localLength=sizeof(local);
 if(getsockname(fd,(sockaddr*)&local,&localLength)<0)return;
 if(local.ss_family==AF_INET6&&address->ss_family==AF_INET){
  const auto* source=(sockaddr_in*)address;sockaddr_in6 mapped{};mapped.sin6_family=AF_INET6;mapped.sin6_port=source->sin_port;
  mapped.sin6_addr.s6_addr[10]=0xff;mapped.sin6_addr.s6_addr[11]=0xff;memcpy(&mapped.sin6_addr.s6_addr[12],&source->sin_addr,4);
  memset(address,0,sizeof(*address));memcpy(address,&mapped,sizeof(mapped));*length=sizeof(mapped);
 }else if(local.ss_family==AF_INET&&address->ss_family==AF_INET6){
  const auto* source=(sockaddr_in6*)address;if(!IN6_IS_ADDR_V4MAPPED(&source->sin6_addr))return;
  sockaddr_in converted{};converted.sin_family=AF_INET;converted.sin_port=source->sin6_port;memcpy(&converted.sin_addr,&source->sin6_addr.s6_addr[12],4);
  memset(address,0,sizeof(*address));memcpy(address,&converted,sizeof(converted));*length=sizeof(converted);
 }
}

static bool decodeSocksUdpPacket(const unsigned char* packet,size_t size,sockaddr_storage* source,
                                socklen_t* sourceLength,size_t* payloadOffset){
 if(size<4||packet[0]!=0||packet[1]!=0||packet[2]!=0){errno=EPROTO;return false;}
 size_t offset=4;uint16_t port=0;sockaddr_storage address{};
 if(packet[3]==1){
  if(size<offset+6){errno=EPROTO;return false;}
  auto* value=(sockaddr_in*)&address;value->sin_family=AF_INET;
  memcpy(&value->sin_addr,packet+offset,4);offset+=4;memcpy(&port,packet+offset,2);offset+=2;
  value->sin_port=port;*sourceLength=sizeof(*value);
 }else if(packet[3]==4){
  if(size<offset+18){errno=EPROTO;return false;}
  auto* value=(sockaddr_in6*)&address;value->sin6_family=AF_INET6;
  memcpy(&value->sin6_addr,packet+offset,16);offset+=16;memcpy(&port,packet+offset,2);offset+=2;
  value->sin6_port=port;*sourceLength=sizeof(*value);
 }else if(packet[3]==3){
  if(size<offset+1){errno=EPROTO;return false;}
  unsigned length=packet[offset++];if(length==0||size<offset+length+2){errno=EPROTO;return false;}
  std::string host((const char*)packet+offset,length);offset+=length;memcpy(&port,packet+offset,2);offset+=2;
  addrinfo hints{};hints.ai_family=AF_INET;hints.ai_socktype=SOCK_DGRAM;addrinfo* resolved=nullptr;
  if(getaddrinfo(host.c_str(),nullptr,&hints,&resolved)!=0||resolved==nullptr){errno=EHOSTUNREACH;return false;}
  auto* value=(sockaddr_in*)&address;*value=*(sockaddr_in*)resolved->ai_addr;value->sin_port=port;freeaddrinfo(resolved);
  *sourceLength=sizeof(*value);
 }else{errno=EPROTO;return false;}
 *source=address;*payloadOffset=offset;return true;
}

static ssize_t receiveUdp(int fd,void* data,size_t capacity,int flags,sockaddr* from,socklen_t* fromLength){
 bool proxied=false;
 {
  std::lock_guard<std::mutex> lock(udpLock);proxied=proxiedUdpFds.find(fd)!=proxiedUdpFds.end();
 }
 if(!proxied)return realRecvFrom(fd,data,capacity,flags,from,fromLength);
 std::vector<unsigned char> raw(65536);
 sockaddr_storage sender{};socklen_t senderLength=sizeof(sender);
 ssize_t received=realRecvFrom(fd,raw.data(),raw.size(),flags,(sockaddr*)&sender,&senderLength);
 if(received<0)return received;
 size_t offset=0,payloadSize=(size_t)received; sockaddr_storage source=sender;socklen_t sourceLength=senderLength;
 sockaddr_storage relay{};socklen_t relayLength=0;
 {
  std::lock_guard<std::mutex> lock(udpLock);relay=udpRelay;relayLength=udpRelayLength;
 }
 if(relayLength>0&&sameAddress((sockaddr*)&sender,(sockaddr*)&relay)){
  if(!decodeSocksUdpPacket(raw.data(),(size_t)received,&source,&sourceLength,&offset))return -1;
  adaptAddressToSocket(fd,&source,&sourceLength);
  payloadSize=(size_t)received-offset;
 }
 size_t copied=payloadSize<capacity?payloadSize:capacity;
 if(copied>0)memcpy(data,raw.data()+offset,copied);
 if(from&&fromLength){
  socklen_t available=*fromLength;*fromLength=sourceLength;
  if(available>0){socklen_t count=available<sourceLength?available:sourceLength;memcpy(from,&source,count);}
 }
 if((flags&MSG_TRUNC)!=0)return (ssize_t)payloadSize;
 return (ssize_t)copied;
}

static ssize_t proxySendTo(int fd,const void* data,size_t size,int flags,const sockaddr* address,socklen_t length){
 if(socketType(fd)==SOCK_DGRAM&&address==nullptr){UdpPeer peer{};if(!getUdpPeer(fd,&peer))return realSendTo(fd,data,size,flags,address,length);}
 return sendUdp(fd,data,size,flags,address,length);
}
static ssize_t proxySend(int fd,const void* data,size_t size,int flags){
 if(socketType(fd)==SOCK_DGRAM){UdpPeer peer{};if(getUdpPeer(fd,&peer))return sendUdp(fd,data,size,flags,nullptr,0);}
 return realSend(fd,data,size,flags);
}
static ssize_t proxyRecvFrom(int fd,void* data,size_t size,int flags,sockaddr* address,socklen_t* length){
 if(socketType(fd)==SOCK_DGRAM)return receiveUdp(fd,data,size,flags,address,length);
 return realRecvFrom(fd,data,size,flags,address,length);
}
static ssize_t proxyRecv(int fd,void* data,size_t size,int flags){
 if(socketType(fd)==SOCK_DGRAM)return receiveUdp(fd,data,size,flags,nullptr,nullptr);
 return realRecv(fd,data,size,flags);
}

static ssize_t proxySendMsg(int fd,const msghdr* message,int flags){
 if(socketType(fd)!=SOCK_DGRAM)return realSendMsg(fd,message,flags);
 if(message->msg_name==nullptr){UdpPeer peer{};if(!getUdpPeer(fd,&peer))return realSendMsg(fd,message,flags);}
 size_t size=0;
 for(size_t i=0;i<message->msg_iovlen;i++){
  if(message->msg_iov[i].iov_len>SIZE_MAX-size){errno=EMSGSIZE;return -1;}
  size+=message->msg_iov[i].iov_len;
 }
 std::vector<unsigned char> data(size);size_t offset=0;
 for(size_t i=0;i<message->msg_iovlen;i++){
  if(message->msg_iov[i].iov_len>0)memcpy(data.data()+offset,message->msg_iov[i].iov_base,message->msg_iov[i].iov_len);
  offset+=message->msg_iov[i].iov_len;
 }
 return sendUdp(fd,data.data(),size,flags,(const sockaddr*)message->msg_name,message->msg_namelen);
}
static ssize_t proxyRecvMsg(int fd,msghdr* message,int flags){
 if(socketType(fd)!=SOCK_DGRAM)return realRecvMsg(fd,message,flags);
 bool proxied=false;
 {
  std::lock_guard<std::mutex> lock(udpLock);
  proxied=proxiedUdpFds.find(fd)!=proxiedUdpFds.end();
 }
 if(!proxied)return realRecvMsg(fd,message,flags);
 size_t capacity=0;
 for(size_t i=0;i<message->msg_iovlen;i++){
  if(message->msg_iov[i].iov_len>SIZE_MAX-capacity){errno=EMSGSIZE;return -1;}
  capacity+=message->msg_iov[i].iov_len;
 }
 std::vector<unsigned char> data(capacity);
 sockaddr_storage from{};socklen_t fromLength=sizeof(from);
 ssize_t received=receiveUdp(fd,data.data(),capacity,flags,(sockaddr*)&from,&fromLength);
 if(received<0)return received;
 size_t count=(size_t)received<capacity?(size_t)received:capacity;size_t offset=0;
 for(size_t i=0;i<message->msg_iovlen&&offset<count;i++){
  size_t amount=message->msg_iov[i].iov_len<count-offset?message->msg_iov[i].iov_len:count-offset;
  memcpy(message->msg_iov[i].iov_base,data.data()+offset,amount);offset+=amount;
 }
 if(message->msg_name&&message->msg_namelen>0){socklen_t amount=message->msg_namelen<fromLength?message->msg_namelen:fromLength;memcpy(message->msg_name,&from,amount);}
 message->msg_namelen=fromLength;message->msg_controllen=0;message->msg_flags=0;
 if((size_t)received>capacity)message->msg_flags|=MSG_TRUNC;
 return received;
}

static int proxySendMmsg(int fd,mmsghdr* messages,unsigned int count,int flags){
 if(socketType(fd)!=SOCK_DGRAM)return realSendMmsg(fd,messages,count,flags);
 unsigned int sent=0;
 for(;sent<count;sent++){
  ssize_t result=proxySendMsg(fd,&messages[sent].msg_hdr,flags);
  if(result<0)return sent?(int)sent:-1;
  messages[sent].msg_len=(unsigned int)result;
 }
 return (int)sent;
}
static int proxyRecvMmsg(int fd,mmsghdr* messages,unsigned int count,int flags,timespec* timeout){
 if(socketType(fd)!=SOCK_DGRAM)return realRecvMmsg(fd,messages,count,flags,timeout);
 unsigned int received=0;
 for(;received<count;received++){
  int currentFlags=flags;
  if(received>0)currentFlags|=MSG_DONTWAIT;
  else if(timeout&&(flags&MSG_DONTWAIT)==0){
   long long milliseconds=(long long)timeout->tv_sec*1000+(timeout->tv_nsec+999999)/1000000;
   if(milliseconds>INT32_MAX)milliseconds=INT32_MAX;
   pollfd item{fd,POLLIN,0};int ready=poll(&item,1,(int)milliseconds);
   if(ready==0){timeout->tv_sec=0;timeout->tv_nsec=0;return 0;}
   if(ready<0)return -1;
   currentFlags|=MSG_DONTWAIT;
  }
  ssize_t result=proxyRecvMsg(fd,&messages[received].msg_hdr,currentFlags);
  if(result<0)return received?(int)received:-1;
  messages[received].msg_len=(unsigned int)result;
 }
 return (int)received;
}

static int proxySocket(int family,int type,int protocol){
 int socketTypeValue=type&0xf;
 if((family==AF_INET||family==AF_INET6)&&socketTypeValue!=SOCK_STREAM&&socketTypeValue!=SOCK_DGRAM){errno=EPROTONOSUPPORT;return -1;}
 int fd=realSocket(family,type,protocol);
 if(fd>=0&&family==AF_INET6&&socketTypeValue==SOCK_DGRAM){int dualStack=0;setsockopt(fd,IPPROTO_IPV6,IPV6_V6ONLY,&dualStack,sizeof(dualStack));}
 return fd;
}
static int proxyClose(int fd){
 {
  std::lock_guard<std::mutex> lock(udpLock);udpPeers.erase(fd);proxiedUdpFds.erase(fd);
 }
 return realClose(fd);
}
static int proxyConnect(int fd,const sockaddr* address,socklen_t length){
 if(address&&address->sa_family==AF_UNSPEC&&socketType(fd)==SOCK_DGRAM){
  bool wasProxied=false;{
   std::lock_guard<std::mutex> lock(udpLock);wasProxied=udpPeers.erase(fd)>0;proxiedUdpFds.erase(fd);
  }
  if(wasProxied)return 0;
  return realConnect(fd,address,length);
 }
 if(!address||(address->sa_family!=AF_INET&&address->sa_family!=AF_INET6))return realConnect(fd,address,length);
 if((address->sa_family==AF_INET&&length<sizeof(sockaddr_in))||(address->sa_family==AF_INET6&&length<sizeof(sockaddr_in6))){errno=EINVAL;return -1;}
 if(loopbackAddress(address))return realConnect(fd,address,length);
 int type=socketType(fd);
 if(type==SOCK_DGRAM){
  if(settings.type!=1)return realConnect(fd,address,length);
  sockaddr_storage local{};socklen_t localLength=sizeof(local);
  if(getsockname(fd,(sockaddr*)&local,&localLength)==0){
   bool unbound=(local.ss_family==AF_INET&&((sockaddr_in*)&local)->sin_port==0)
       ||(local.ss_family==AF_INET6&&((sockaddr_in6*)&local)->sin6_port==0);
   if(unbound){
    if(local.ss_family==AF_INET){sockaddr_in bindAddress{};bindAddress.sin_family=AF_INET;if(bind(fd,(sockaddr*)&bindAddress,sizeof(bindAddress))<0)return -1;}
    else if(local.ss_family==AF_INET6){sockaddr_in6 bindAddress{};bindAddress.sin6_family=AF_INET6;if(bind(fd,(sockaddr*)&bindAddress,sizeof(bindAddress))<0)return -1;}
   }
  }
  UdpPeer peer{};peer.length=address->sa_family==AF_INET?sizeof(sockaddr_in):sizeof(sockaddr_in6);
  memcpy(&peer.address,address,peer.length);
  std::lock_guard<std::mutex> lock(udpLock);udpPeers[fd]=peer;return 0;
 }
 if(type!=SOCK_STREAM){errno=ENETUNREACH;return -1;}
 sockaddr_storage endpoint{};socklen_t endpointSize;
 if(address->sa_family==AF_INET){
  auto p=(sockaddr_in*)&endpoint;p->sin_family=AF_INET;p->sin_port=htons(settings.port);inet_pton(AF_INET,settings.host.c_str(),&p->sin_addr);endpointSize=sizeof(*p);
 }else{
  auto p=(sockaddr_in6*)&endpoint;p->sin6_family=AF_INET6;p->sin6_port=htons(settings.port);inet_pton(AF_INET6,("::ffff:"+settings.host).c_str(),&p->sin6_addr);endpointSize=sizeof(*p);
 }
 int flags=fcntl(fd,F_GETFL,0);if(flags<0)return -1;
 if(fcntl(fd,F_SETFL,flags|O_NONBLOCK)<0)return -1;
 int r=realConnect(fd,(sockaddr*)&endpoint,endpointSize);
 bool ok=r==0;
 if(r<0&&errno==EISCONN){fcntl(fd,F_SETFL,flags);errno=EISCONN;return -1;}
 if(r<0&&errno==EINPROGRESS){
  pollfd item{fd,POLLOUT,0};int result=poll(&item,1,4000);
  if(result>0){int error=0;socklen_t size=sizeof(error);ok=getsockopt(fd,SOL_SOCKET,SO_ERROR,&error,&size)==0&&error==0;if(!ok)errno=error?error:ECONNREFUSED;}
  else errno=result==0?ETIMEDOUT:errno;
 }
 if(ok)ok=CloneProxy::handshake(fd,address,settings,4000);
 int failure=errno;fcntl(fd,F_SETFL,flags);
 if(!ok){shutdown(fd,SHUT_RDWR);errno=failure;return -1;}
 return 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_top_niunaijun_blackbox_core_NativeCore_configureProxy(JNIEnv* env,jclass,jint type,jstring host,jint port,jbyteArray user,jbyteArray password){
 std::lock_guard<std::mutex> lock(configLock);
 if(configured)return JNI_FALSE;
 if(type==0)return JNI_TRUE;
 if(type!=1&&type!=2)return JNI_FALSE;
 const char* text=env->GetStringUTFChars(host,nullptr);settings.host=text;env->ReleaseStringUTFChars(host,text);
 settings.type=type;settings.port=port;
 settings.user.resize(env->GetArrayLength(user));settings.password.resize(env->GetArrayLength(password));
 if(!settings.user.empty())env->GetByteArrayRegion(user,0,settings.user.size(),(jbyte*)&settings.user[0]);
 if(!settings.password.empty())env->GetByteArrayRegion(password,0,settings.password.size(),(jbyte*)&settings.password[0]);
 in_addr ip{};if(port<1||port>65535||inet_pton(AF_INET,settings.host.c_str(),&ip)!=1||settings.user.size()>255||settings.password.size()>255)return JNI_FALSE;
 void* connectAddress=dlsym(RTLD_DEFAULT,"connect");
 if(!connectAddress)return JNI_FALSE;
 if(DobbyHook(connectAddress,(void*)proxyConnect,(void**)&realConnect)!=0)return JNI_FALSE;
 if(type==2){configured=true;return JNI_TRUE;}
 void* socketAddress=dlsym(RTLD_DEFAULT,"socket");
 void* closeAddress=dlsym(RTLD_DEFAULT,"close");void* sendAddress=dlsym(RTLD_DEFAULT,"send");void* recvAddress=dlsym(RTLD_DEFAULT,"recv");
 void* sendTo=dlsym(RTLD_DEFAULT,"sendto");void* recvFrom=dlsym(RTLD_DEFAULT,"recvfrom");
 void* sendMsg=dlsym(RTLD_DEFAULT,"sendmsg");void* recvMsg=dlsym(RTLD_DEFAULT,"recvmsg");
 void* sendMmsg=dlsym(RTLD_DEFAULT,"sendmmsg");void* recvMmsg=dlsym(RTLD_DEFAULT,"recvmmsg");
 if(!socketAddress||!closeAddress||!sendAddress||!recvAddress||!sendTo||!recvFrom||!sendMsg||!recvMsg||!sendMmsg||!recvMmsg)return JNI_FALSE;
 if(DobbyHook(socketAddress,(void*)proxySocket,(void**)&realSocket)!=0)return JNI_FALSE;
 if(DobbyHook(closeAddress,(void*)proxyClose,(void**)&realClose)!=0)return JNI_FALSE;
 if(DobbyHook(sendAddress,(void*)proxySend,(void**)&realSend)!=0)return JNI_FALSE;
 if(DobbyHook(recvAddress,(void*)proxyRecv,(void**)&realRecv)!=0)return JNI_FALSE;
 if(DobbyHook(sendTo,(void*)proxySendTo,(void**)&realSendTo)!=0)return JNI_FALSE;
 if(DobbyHook(recvFrom,(void*)proxyRecvFrom,(void**)&realRecvFrom)!=0)return JNI_FALSE;
 if(DobbyHook(sendMsg,(void*)proxySendMsg,(void**)&realSendMsg)!=0)return JNI_FALSE;
 if(DobbyHook(recvMsg,(void*)proxyRecvMsg,(void**)&realRecvMsg)!=0)return JNI_FALSE;
 if(DobbyHook(sendMmsg,(void*)proxySendMmsg,(void**)&realSendMmsg)!=0)return JNI_FALSE;
 if(DobbyHook(recvMmsg,(void*)proxyRecvMmsg,(void**)&realRecvMmsg)!=0)return JNI_FALSE;
 configured=true;return JNI_TRUE;
}

static jint proxyProbeFailure(jint stage,int error){
 int code=error>0&&error<1000?error:EIO;
 return stage*1000+code;
}

extern "C" JNIEXPORT jint JNICALL
Java_top_niunaijun_blackbox_core_NativeCore_probeProxy(JNIEnv* env,jclass,jint type,jstring host,jint port,jbyteArray user,jbyteArray password){
 CloneProxy::Config c;c.type=type;c.port=port;
 const char* text=env->GetStringUTFChars(host,nullptr);c.host=text;env->ReleaseStringUTFChars(host,text);
 c.user.resize(env->GetArrayLength(user));c.password.resize(env->GetArrayLength(password));
 if(!c.user.empty())env->GetByteArrayRegion(user,0,c.user.size(),(jbyte*)&c.user[0]);
 if(!c.password.empty())env->GetByteArrayRegion(password,0,c.password.size(),(jbyte*)&c.password[0]);
 if((type!=1&&type!=2)||port<1||port>65535)return EINVAL;
 sockaddr_in endpoint{};endpoint.sin_family=AF_INET;endpoint.sin_port=htons(port);
 if(inet_pton(AF_INET,c.host.c_str(),&endpoint.sin_addr)!=1)return EINVAL;
 int fd=socket(AF_INET,SOCK_STREAM|SOCK_CLOEXEC|SOCK_NONBLOCK,0);if(fd<0)return proxyProbeFailure(1,errno);
 int result=connect(fd,(sockaddr*)&endpoint,sizeof(endpoint));bool ok=result==0;
 if(result<0&&errno==EINPROGRESS){
  pollfd p{fd,POLLOUT,0};int ready=poll(&p,1,4000);int error=0;socklen_t size=sizeof(error);
  if(ready>0){ok=getsockopt(fd,SOL_SOCKET,SO_ERROR,&error,&size)==0&&error==0;if(!ok)errno=error?error:ECONNREFUSED;}
  else {ok=false;errno=ready==0?ETIMEDOUT:errno;}
 }
 sockaddr_in target{};target.sin_family=AF_INET;target.sin_port=htons(443);inet_pton(AF_INET,"1.1.1.1",&target.sin_addr);
 int stage=1;int httpStatus=0;
 if(ok){stage=2;ok=CloneProxy::handshake(fd,(sockaddr*)&target,c,4000,&httpStatus);}
 int error=ok?0:(httpStatus>=100&&httpStatus<=599?httpStatus:errno);close(fd);
 if(error!=0)return proxyProbeFailure(stage,error);
 if(type==1){
  int udpFd=socket(AF_INET,SOCK_STREAM|SOCK_CLOEXEC|SOCK_NONBLOCK,0);
  if(udpFd<0)return proxyProbeFailure(3,errno);
  int connected=connect(udpFd,(sockaddr*)&endpoint,sizeof(endpoint))==0;
  if(!connected&&errno==EINPROGRESS){
   pollfd item{udpFd,POLLOUT,0};int ready=poll(&item,1,4000);int connectError=0;socklen_t size=sizeof(connectError);
   if(ready>0){connected=getsockopt(udpFd,SOL_SOCKET,SO_ERROR,&connectError,&size)==0&&connectError==0;if(!connected)errno=connectError?connectError:ECONNREFUSED;}
   else {errno=ready==0?ETIMEDOUT:errno;}
  }
  if(!connected){int connectError=errno;close(udpFd);return proxyProbeFailure(3,connectError);}
  sockaddr_storage relay{};socklen_t relayLength=0;
  bool associated=CloneProxy::udpAssociate(udpFd,c,&relay,&relayLength,4000);
  error=associated?0:errno;close(udpFd);
  if(error!=0)return proxyProbeFailure(4,error);
 }
 return 0;
}
