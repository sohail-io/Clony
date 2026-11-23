#include "ProxyTunnel.h"
#include "Dobby/dobby.h"
#include <jni.h>
#include <dlfcn.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <netdb.h>
#include <poll.h>
#include <android/log.h>
#include <cerrno>
#include <cstring>
#include <atomic>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <unordered_set>
#include <vector>
#include <string>
#include <cctype>
#include <cstdint>
#include <time.h>
#include <chrono>
#include <thread>
#include <unistd.h>

static CloneProxy::Config settings;
static int (*realConnect)(int,const sockaddr*,socklen_t)=nullptr;
static int (*realSocket)(int,int,int)=nullptr;
static int (*realClose)(int)=nullptr;
static int (*realGetAddrInfo)(const char*,const char*,const addrinfo*,addrinfo**)=nullptr;
static int (*realAndroidGetAddrInfoForNet)(const char*,const char*,const addrinfo*,unsigned int,unsigned int,addrinfo**)=nullptr;
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
static std::string proxyEndpointName;
static std::mutex remoteDnsLock;
static std::unordered_map<std::string,std::string> remoteDnsAddressToName;
static std::unordered_map<std::string,uint32_t> remoteDnsNameToId;
static std::mutex loggedDnsSystemResolutionLock;
static std::unordered_set<uint64_t> loggedDnsSystemResolutionHashes;
static uint32_t nextRemoteDnsId=1;
static thread_local bool bypassRemoteDnsResolution=false;
static std::atomic<uint64_t> nextDnsQueryId{1};
static std::atomic<uint64_t> nextTcpRouteId{1};
static constexpr const char* PROXY_LOG_TAG="ClonyCloneProxy";
// connect() is synchronous at the interception point. Bound endpoint setup
// and the SOCKS handshake separately so slow residential proxies have time to
// respond while the total wait remains below Android's input-dispatch ANR limit.
static constexpr int PROXY_CONNECT_TIMEOUT_MS=2000;
static constexpr int PROXY_HANDSHAKE_TIMEOUT_MS=2000;
static constexpr int PROXY_UDP_TIMEOUT_MS=2000;
static constexpr int PROXY_ENDPOINT_DNS_TIMEOUT_MS=1200;
static std::atomic<bool> loggedLocalDatagramBypass{false};
static std::atomic<bool> loggedDirectUdpBypass{false};

struct UdpPeer {
 sockaddr_storage address{};
 socklen_t length=0;
};
static std::mutex udpLock;
static std::unordered_map<int,UdpPeer> udpPeers;
static std::unordered_set<int> proxiedUdpFds;
static std::unordered_map<int,std::unordered_set<std::string>> loggedUdpPorts;
static int udpControlFd=-1;
static sockaddr_storage udpRelay{};
static socklen_t udpRelayLength=0;
static bool udpRelayFailed=false;
static int udpRelayError=0;
static std::chrono::steady_clock::time_point udpRelayRetryAfter{};

struct TcpSocketTrace {
 uint64_t routeId=0;
 std::chrono::steady_clock::time_point connectedAt{};
 uint64_t sendCalls=0;
 uint64_t receiveCalls=0;
 uint64_t bytesSent=0;
 uint64_t bytesReceived=0;
 unsigned int sendErrors=0;
 unsigned int receiveErrors=0;
 bool firstSendSeen=false;
 bool firstReceiveSeen=false;
};
static std::mutex tcpSocketTraceLock;
static std::unordered_map<int,TcpSocketTrace> tcpSocketTraces;

class RemoteDnsBypassScope {
 bool previous;
public:
 RemoteDnsBypassScope():previous(bypassRemoteDnsResolution){bypassRemoteDnsResolution=true;}
 ~RemoteDnsBypassScope(){bypassRemoteDnsResolution=previous;}
};

static bool internetAddress(const sockaddr* address){return address&&(address->sa_family==AF_INET||address->sa_family==AF_INET6);}
static long long elapsedMilliseconds(const std::chrono::steady_clock::time_point& start){
 return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now()-start).count();
}
static const char* addressFamilyName(const sockaddr* address){
 if(!address)return "unknown";
 if(address->sa_family==AF_INET)return "ipv4";
 if(address->sa_family==AF_INET6)return "ipv6";
 return "other";
}
static unsigned int addressPort(const sockaddr* address){
 if(!address)return 0;
 if(address->sa_family==AF_INET)return ntohs(((const sockaddr_in*)address)->sin_port);
 if(address->sa_family==AF_INET6)return ntohs(((const sockaddr_in6*)address)->sin6_port);
 return 0;
}
static void registerTcpSocketTrace(int fd,uint64_t routeId){
 TcpSocketTrace trace;trace.routeId=routeId;trace.connectedAt=std::chrono::steady_clock::now();
 std::lock_guard<std::mutex> lock(tcpSocketTraceLock);tcpSocketTraces[fd]=trace;
}
static void recordTcpSocketIo(int fd,const char* direction,size_t requested,ssize_t result,int error){
 uint64_t routeId=0;long long elapsedMs=0;bool firstEvent=false;bool firstError=false;const char* phase="";
 {
  std::lock_guard<std::mutex> lock(tcpSocketTraceLock);
  auto found=tcpSocketTraces.find(fd);if(found==tcpSocketTraces.end())return;
  TcpSocketTrace& trace=found->second;routeId=trace.routeId;
  elapsedMs=std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now()-trace.connectedAt).count();
  if(strcmp(direction,"send")==0){
   trace.sendCalls++;
   if(result>0)trace.bytesSent+=(uint64_t)result;
   if(result<0){trace.sendErrors++;firstError=trace.sendErrors==1;phase="first_write_error";}
   else {firstEvent=!trace.firstSendSeen;trace.firstSendSeen=true;phase="first_write";}
  }else{
   trace.receiveCalls++;
   if(result>0)trace.bytesReceived+=(uint64_t)result;
   if(result<0){trace.receiveErrors++;firstError=trace.receiveErrors==1;phase="first_read_error";}
   else {firstEvent=!trace.firstReceiveSeen;trace.firstReceiveSeen=true;phase="first_read";}
  }
 }
 if(firstEvent||firstError){
  __android_log_print(result<0?ANDROID_LOG_WARN:ANDROID_LOG_INFO,PROXY_LOG_TAG,
                      "event=tcp_io route_id=%llu phase=%s direction=%s result=%s requested_bytes=%zu actual_bytes=%lld error=%d elapsed_since_tunnel_ms=%lld payload_logged=false",
                      (unsigned long long)routeId,phase,direction,result<0?"failure":result==0?"eof":"success",
                      requested,(long long)result,result<0?error:0,elapsedMs);
 }
}
static void logTcpSocketSummaryOnClose(int fd){
 TcpSocketTrace trace;
 {
  std::lock_guard<std::mutex> lock(tcpSocketTraceLock);
  auto found=tcpSocketTraces.find(fd);if(found==tcpSocketTraces.end())return;
  trace=found->second;tcpSocketTraces.erase(found);
 }
 long long lifetimeMs=std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now()-trace.connectedAt).count();
 __android_log_print(ANDROID_LOG_INFO,PROXY_LOG_TAG,
                     "event=tcp_socket_close route_id=%llu lifetime_ms=%lld send_calls=%llu receive_calls=%llu bytes_sent=%llu bytes_received=%llu send_errors=%u receive_errors=%u payload_logged=false",
                     (unsigned long long)trace.routeId,lifetimeMs,(unsigned long long)trace.sendCalls,
                     (unsigned long long)trace.receiveCalls,(unsigned long long)trace.bytesSent,
                     (unsigned long long)trace.bytesReceived,trace.sendErrors,trace.receiveErrors);
}
static uint64_t dnsNameHash(const std::string& name){
 uint64_t hash=1469598103934665603ULL;
 for(unsigned char value:name){hash^=value;hash*=1099511628211ULL;}
 return hash;
}
static void logTcpRoute(uint64_t routeId,const char* result,const sockaddr* destination,const char* stage,
                        int error=0,long long elapsedMs=0,long long connectElapsedMs=0,
                        long long handshakeElapsedMs=0,const std::string* destinationHost=nullptr){
 __android_log_print(ANDROID_LOG_INFO,PROXY_LOG_TAG,
                     "event=tcp_route route_id=%llu result=%s stage=%s transport=%s destination_family=%s destination_port=%u hostname_hash=%016llx error=%d endpoint_connect_ms=%lld handshake_ms=%lld elapsed_ms=%lld proxy_endpoint_logged=false credentials_logged=false",
                     (unsigned long long)routeId,result,stage,settings.type==2?"http_connect_tcp":"socks5_tcp",
                     addressFamilyName(destination),addressPort(destination),
                     (unsigned long long)(destinationHost==nullptr?0:dnsNameHash(*destinationHost)),error,
                     connectElapsedMs,handshakeElapsedMs,elapsedMs);
}
static void logUdpRouteOnce(int fd,const sockaddr* destination,const char* result,const char* reason,int error=0){
 unsigned int port=addressPort(destination);
 if(strcmp(result,"direct_bypass")==0){
  if(loggedDirectUdpBypass.exchange(true))return;
 }else{
 {
  std::lock_guard<std::mutex> lock(udpLock);
  std::string eventKey=std::to_string(port)+":"+result;
  if(!loggedUdpPorts[fd].insert(eventKey).second)return;
 }
 }
 __android_log_print(ANDROID_LOG_INFO,PROXY_LOG_TAG,
                     "event=udp_route result=%s transport=%s destination_family=%s destination_port=%u destination_port_known=%s reason=%s error=%d proxy_endpoint_logged=false credentials_logged=false",
                     result,settings.type==2?"http_connect_tcp":"socks5_udp",
                     addressFamilyName(destination),port,port==0?"false":"true",reason,error);
}
static const sockaddr* connectedUdpPeer(int fd,sockaddr_storage* out,socklen_t* outLength){
 *outLength=sizeof(*out);
 return getpeername(fd,(sockaddr*)out,outLength)==0?(const sockaddr*)out:nullptr;
}
static bool resolveProxyIPv4(const std::string& host,in_addr* address){
 if(address==nullptr||host.empty()){errno=EINVAL;return false;}
 if(inet_pton(AF_INET,host.c_str(),address)==1)return true;
 addrinfo hints{};hints.ai_family=AF_INET;hints.ai_socktype=SOCK_STREAM;
 addrinfo* resolved=nullptr;
 int result=getaddrinfo(host.c_str(),nullptr,&hints,&resolved);
 if(result!=0||resolved==nullptr){if(resolved)freeaddrinfo(resolved);errno=EHOSTUNREACH;return false;}
 bool found=false;
 for(addrinfo* current=resolved;current!=nullptr;current=current->ai_next){
  if(current->ai_family==AF_INET&&current->ai_addrlen>=sizeof(sockaddr_in)){
   *address=((sockaddr_in*)current->ai_addr)->sin_addr;found=true;break;
  }
 }
 freeaddrinfo(resolved);
 if(!found)errno=EHOSTUNREACH;
 return found;
}
struct ProxyEndpointResolution {
 std::mutex lock;
 std::condition_variable completedSignal;
 bool completed=false;
 bool found=false;
 in_addr address{};
};
static bool resolveProxyIPv4Bounded(const std::string& host,in_addr* address,int timeoutMs){
 if(address==nullptr||host.empty()){errno=EINVAL;return false;}
 if(inet_pton(AF_INET,host.c_str(),address)==1)return true;

 auto result=std::make_shared<ProxyEndpointResolution>();
 std::thread([host,result](){
  in_addr resolved{};
  bool found=resolveProxyIPv4(host,&resolved);
  {
   std::lock_guard<std::mutex> lock(result->lock);
   result->found=found;
   result->address=resolved;
   result->completed=true;
  }
  result->completedSignal.notify_one();
 }).detach();

 std::unique_lock<std::mutex> lock(result->lock);
 if(!result->completedSignal.wait_for(lock,std::chrono::milliseconds(timeoutMs),[&](){return result->completed;})){
  errno=ETIMEDOUT;
  return false;
 }
 if(!result->found){errno=EHOSTUNREACH;return false;}
 *address=result->address;
 return true;
}
static std::string normalizeDnsName(const char* name){
 if(name==nullptr)return {};
 std::string normalized(name);
 while(!normalized.empty()&&normalized.back()=='.')normalized.pop_back();
 for(char& character:normalized){
  if(character>='A'&&character<='Z')character=(char)(character-'A'+'a');
 }
 return normalized;
}
static bool isNumericDnsName(const char* name){
 if(name==nullptr||*name=='\0')return false;
 in_addr ipv4{};in6_addr ipv6{};
 return inet_pton(AF_INET,name,&ipv4)==1||inet_pton(AF_INET6,name,&ipv6)==1;
}
static bool eligibleForRemoteDns(const char* node,const addrinfo* hints){
 if(settings.type!=1||bypassRemoteDnsResolution||node==nullptr||*node=='\0')return false;
 if(hints&&(hints->ai_flags&AI_NUMERICHOST))return false;
 if(isNumericDnsName(node))return false;
 std::string name=normalizeDnsName(node);
 if(name.empty()||name.find('.')==std::string::npos)return false;
 for(unsigned char character:name){if(character>0x7f)return false;}
 if(name=="localhost"||name.size()>=10&&name.compare(name.size()-10,10,".localhost")==0)return false;
 if(name.size()>=5&&name.compare(name.size()-5,5,".local")==0)return false;
 if(!proxyEndpointName.empty()&&name==normalizeDnsName(proxyEndpointName.c_str()))return false;
 return true;
}
static std::string remoteDnsAddressKey(const sockaddr* address){
 if(address==nullptr)return {};
 std::string key;
 if(address->sa_family==AF_INET){
  const auto* ipv4=(const sockaddr_in*)address;
  key.push_back((char)AF_INET);key.append((const char*)&ipv4->sin_addr,sizeof(ipv4->sin_addr));return key;
 }
 if(address->sa_family==AF_INET6){
  const auto* ipv6=(const sockaddr_in6*)address;
  key.push_back((char)AF_INET6);key.append((const char*)&ipv6->sin6_addr,sizeof(ipv6->sin6_addr));return key;
 }
 return {};
}
static bool syntheticDnsAddress(const std::string& name,int family,std::string* numericAddress){
 if(numericAddress==nullptr){errno=EINVAL;return false;}
 uint32_t id=0;
 {
  std::lock_guard<std::mutex> lock(remoteDnsLock);
  auto found=remoteDnsNameToId.find(name);
  if(found!=remoteDnsNameToId.end())id=found->second;
  else{
   if(nextRemoteDnsId>0x1ffffu){errno=EAI_AGAIN;return false;}
   id=nextRemoteDnsId++;
   remoteDnsNameToId.emplace(name,id);
  }
 }
 char buffer[INET6_ADDRSTRLEN]{};
 if(family==AF_INET){
  sockaddr_in address{};address.sin_family=AF_INET;
  address.sin_addr.s_addr=htonl(0xc6120000u|id);
  if(inet_ntop(AF_INET,&address.sin_addr,buffer,sizeof(buffer))==nullptr)return false;
 }else if(family==AF_INET6){
  sockaddr_in6 address{};address.sin6_family=AF_INET6;
  const unsigned char prefix[4]={0x20,0x01,0x0d,0xb8};
  std::memcpy(address.sin6_addr.s6_addr,prefix,sizeof(prefix));
  for(int index=0;index<4;index++)address.sin6_addr.s6_addr[12+index]=(unsigned char)(id>>(24-index*8));
  if(inet_ntop(AF_INET6,&address.sin6_addr,buffer,sizeof(buffer))==nullptr)return false;
 }else{errno=EAFNOSUPPORT;return false;}
 *numericAddress=buffer;
 return true;
}
static void rememberRemoteDnsAddress(const sockaddr* address,const std::string& name){
 std::string key=remoteDnsAddressKey(address);if(key.empty())return;
 std::lock_guard<std::mutex> lock(remoteDnsLock);remoteDnsAddressToName[key]=name;
}
static void logSystemDnsResolutionOnce(const char* node,const addrinfo* hints,const char* api){
 if(settings.type!=1||bypassRemoteDnsResolution||node==nullptr||*node=='\0'||isNumericDnsName(node))return;
 std::string name=normalizeDnsName(node);
 if(name.find('.')==std::string::npos)return;
 uint64_t hash=dnsNameHash(name);
 {
  std::lock_guard<std::mutex> lock(loggedDnsSystemResolutionLock);
  if(!loggedDnsSystemResolutionHashes.insert(hash).second)return;
 }
 const int family=hints==nullptr?AF_UNSPEC:hints->ai_family;
 __android_log_print(ANDROID_LOG_WARN,PROXY_LOG_TAG,
                     "event=dns_route result=system_resolver reason=not_proxy_eligible api=%s requested_family=%s hostname_hash=%016llx hostname_logged=false",
                     api,family==AF_INET?"ipv4":family==AF_INET6?"ipv6":"unspecified",
                     (unsigned long long)hash);
}
static std::string remoteDnsNameForAddress(const sockaddr* address){
 std::string key=remoteDnsAddressKey(address);if(key.empty())return {};
 std::lock_guard<std::mutex> lock(remoteDnsLock);
 auto found=remoteDnsAddressToName.find(key);
 return found==remoteDnsAddressToName.end()?std::string():found->second;
}
static int proxyGetAddrInfo(const char* node,const char* service,const addrinfo* hints,addrinfo** result){
 if(realGetAddrInfo==nullptr)return EAI_FAIL;
 if(result==nullptr)return EAI_FAIL;
 *result=nullptr;
 if(!eligibleForRemoteDns(node,hints)){
  logSystemDnsResolutionOnce(node,hints,"getaddrinfo");
  return realGetAddrInfo(node,service,hints,result);
 }

 const std::string name=normalizeDnsName(node);
 std::vector<int> families;
 const int requestedFamily=hints==nullptr?AF_UNSPEC:hints->ai_family;
 const auto dnsStarted=std::chrono::steady_clock::now();
 if(requestedFamily==AF_UNSPEC){families.push_back(AF_INET6);families.push_back(AF_INET);}
 else if(requestedFamily==AF_INET||requestedFamily==AF_INET6)families.push_back(requestedFamily);
 else return realGetAddrInfo(node,service,hints,result);

 addrinfo* head=nullptr;addrinfo* tail=nullptr;int lastError=EAI_NONAME;
 for(int family:families){
  std::string numericAddress;
  if(!syntheticDnsAddress(name,family,&numericAddress)){lastError=EAI_AGAIN;continue;}
  addrinfo numericHints{};if(hints!=nullptr)numericHints=*hints;
  numericHints.ai_family=family;
  numericHints.ai_flags|=AI_NUMERICHOST;
  numericHints.ai_flags&=~AI_ADDRCONFIG;
#ifdef AI_V4MAPPED
  if(family!=AF_INET6)numericHints.ai_flags&=~AI_V4MAPPED;
#endif
#ifdef AI_ALL
  if(family!=AF_INET6)numericHints.ai_flags&=~AI_ALL;
#endif
#ifdef AI_CANONNAME
  numericHints.ai_flags&=~AI_CANONNAME;
#endif
  addrinfo* familyResults=nullptr;
  int lookupResult=realGetAddrInfo(numericAddress.c_str(),service,&numericHints,&familyResults);
  if(lookupResult!=0||familyResults==nullptr){lastError=lookupResult==0?EAI_NONAME:lookupResult;continue;}
  if(hints!=nullptr&&(hints->ai_flags&AI_CANONNAME)&&head==nullptr){
   familyResults->ai_canonname=strdup(node);
   if(familyResults->ai_canonname==nullptr){freeaddrinfo(familyResults);if(head)freeaddrinfo(head);return EAI_MEMORY;}
  }
  for(addrinfo* item=familyResults;item!=nullptr;item=item->ai_next)rememberRemoteDnsAddress(item->ai_addr,name);
  if(head==nullptr)head=familyResults;
  else tail->ai_next=familyResults;
  tail=familyResults;while(tail->ai_next!=nullptr)tail=tail->ai_next;
 }
 const uint64_t queryId=nextDnsQueryId.fetch_add(1);
 const uint64_t hostnameHash=dnsNameHash(name);
 if(head==nullptr){
  __android_log_print(ANDROID_LOG_WARN,PROXY_LOG_TAG,
                      "event=dns_route query_id=%llu result=failed transport=socks5_domain requested_family=%s hostname_hash=%016llx error=%d elapsed_ms=%lld hostname_logged=false proxy_endpoint_logged=false credentials_logged=false",
                      (unsigned long long)queryId,requestedFamily==AF_INET?"ipv4":requestedFamily==AF_INET6?"ipv6":"unspecified",
                      (unsigned long long)hostnameHash,lastError,elapsedMilliseconds(dnsStarted));
  return lastError;
 }
 *result=head;
 unsigned int answerCount=0;for(addrinfo* item=head;item!=nullptr;item=item->ai_next)answerCount++;
 __android_log_print(ANDROID_LOG_INFO,PROXY_LOG_TAG,
                     "event=dns_route query_id=%llu result=remote_resolution transport=socks5_domain requested_family=%s answer_count=%u hostname_hash=%016llx hostname_logged=false proxy_endpoint_logged=false credentials_logged=false",
                     (unsigned long long)queryId,requestedFamily==AF_INET?"ipv4":requestedFamily==AF_INET6?"ipv6":"unspecified",
                     answerCount,(unsigned long long)hostnameHash);
 return 0;
}
static int proxyAndroidGetAddrInfoForNet(const char* node,const char* service,const addrinfo* hints,
                                         unsigned int netId,unsigned int mark,addrinfo** result){
 if(realAndroidGetAddrInfoForNet==nullptr)return EAI_FAIL;
 if(!eligibleForRemoteDns(node,hints)){
  logSystemDnsResolutionOnce(node,hints,"android_getaddrinfofornet");
  return realAndroidGetAddrInfoForNet(node,service,hints,netId,mark,result);
 }
 return proxyGetAddrInfo(node,service,hints,result);
}
static bool loopbackAddress(const sockaddr* address){
 if(!address)return false;
 if(address->sa_family==AF_INET)return (ntohl(((const sockaddr_in*)address)->sin_addr.s_addr)>>24)==127;
 return address->sa_family==AF_INET6&&IN6_IS_ADDR_LOOPBACK(&((const sockaddr_in6*)address)->sin6_addr);
}
static int socketType(int fd){int type=0;socklen_t size=sizeof(type);return getsockopt(fd,SOL_SOCKET,SO_TYPE,&type,&size)==0?type:0;}
static int socketFamily(int fd){
 sockaddr_storage local{};socklen_t size=sizeof(local);
 return getsockname(fd,(sockaddr*)&local,&size)==0?local.ss_family:-1;
}
static bool internetSocket(int fd){
 int family=socketFamily(fd);
 if(family==AF_INET||family==AF_INET6)return true;
 if(family==AF_UNIX&&!loggedLocalDatagramBypass.exchange(true))
  __android_log_print(ANDROID_LOG_INFO,PROXY_LOG_TAG,
                      "event=local_socket_bypass family=unix result=direct reason=non_internet_socket");
 return false;
}

static bool failUdpRelay(int error,const char* stage,long long elapsedMs){
 udpRelayFailed=true;
 udpRelayError=error==0?ENETUNREACH:error;
 udpRelayRetryAfter=std::chrono::steady_clock::now()+std::chrono::seconds(30);
 __android_log_print(ANDROID_LOG_WARN,PROXY_LOG_TAG,
                     "event=socks5_udp_associate result=failure stage=%s error=%d elapsed_ms=%lld proxy_endpoint_logged=false credentials_logged=false",
                     stage,udpRelayError,elapsedMs);
 errno=udpRelayError;
 return false;
}

static bool ensureUdpRelay(){
 std::lock_guard<std::mutex> lock(udpLock);
 if(udpControlFd>=0)return true;
 if(udpRelayFailed&&std::chrono::steady_clock::now()<udpRelayRetryAfter){errno=udpRelayError;return false;}
 const auto started=std::chrono::steady_clock::now();
 __android_log_print(ANDROID_LOG_INFO,PROXY_LOG_TAG,
                     "event=socks5_udp_associate result=attempt stage=proxy_endpoint_connect timeout_ms=%d proxy_endpoint_logged=false credentials_logged=false",
                     PROXY_UDP_TIMEOUT_MS);
 udpRelayFailed=false;udpRelayError=0;
 if(settings.type!=1||realSocket==nullptr||realConnect==nullptr)return failUdpRelay(ENETUNREACH,"proxy_not_configured",elapsedMilliseconds(started));

 int fd=realSocket(AF_INET,SOCK_STREAM|SOCK_CLOEXEC|SOCK_NONBLOCK,0);
 if(fd<0)return failUdpRelay(errno,"create_control_socket",elapsedMilliseconds(started));
 sockaddr_in endpoint{};endpoint.sin_family=AF_INET;endpoint.sin_port=htons(settings.port);
 if(inet_pton(AF_INET,settings.host.c_str(),&endpoint.sin_addr)!=1){realClose(fd);return failUdpRelay(EINVAL,"proxy_endpoint_invalid",elapsedMilliseconds(started));}
 int result=realConnect(fd,(sockaddr*)&endpoint,sizeof(endpoint));
 bool connected=result==0;
 if(result<0&&errno==EINPROGRESS){
  pollfd item{fd,POLLOUT,0};
  int ready=poll(&item,1,PROXY_UDP_TIMEOUT_MS);
  if(ready>0){int error=0;socklen_t size=sizeof(error);connected=getsockopt(fd,SOL_SOCKET,SO_ERROR,&error,&size)==0&&error==0;if(!connected)errno=error?error:ECONNREFUSED;}
  else errno=ready==0?ETIMEDOUT:errno;
 }
 long long endpointConnectMs=elapsedMilliseconds(started);
 if(!connected){int error=errno;realClose(fd);return failUdpRelay(error,"proxy_endpoint_connect",elapsedMilliseconds(started));}
 sockaddr_storage relay{};socklen_t relaySize=0;
 bool associated=false;
 std::string failedStage="socks5_udp_associate";
 const auto associateStarted=std::chrono::steady_clock::now();
 {
  RemoteDnsBypassScope bypass;
  associated=CloneProxy::udpAssociate(fd,settings,&relay,&relaySize,PROXY_UDP_TIMEOUT_MS,&failedStage);
 }
 if(!associated){
  int error=errno;realClose(fd);
  __android_log_print(ANDROID_LOG_WARN,PROXY_LOG_TAG,
                      "event=socks5_udp_associate_stage result=failure stage=%s error=%d elapsed_ms=%lld endpoint_connect_ms=%lld proxy_endpoint_logged=false credentials_logged=false",
                      failedStage.c_str(),error,elapsedMilliseconds(associateStarted),endpointConnectMs);
  return failUdpRelay(error,failedStage.c_str(),elapsedMilliseconds(started));
 }
 udpRelay=relay;udpRelayLength=relaySize;udpControlFd=fd;udpRelayFailed=false;udpRelayError=0;
 __android_log_print(ANDROID_LOG_INFO,PROXY_LOG_TAG,
                     "event=socks5_udp_associate result=success stage=complete endpoint_connect_ms=%lld elapsed_ms=%lld proxy_endpoint_logged=false credentials_logged=false",
                     endpointConnectMs,elapsedMilliseconds(started));
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

static std::vector<unsigned char> socksUdpPacket(const void* data,size_t size,const sockaddr* destination,
                                                const std::string& destinationHost){
 std::vector<unsigned char> packet;packet.reserve(size+destinationHost.size()+22);
 packet.push_back(0);packet.push_back(0);packet.push_back(0);
 if(!destinationHost.empty()){
  if(destinationHost.size()>255){errno=EINVAL;return {};}
  packet.push_back(3);packet.push_back((unsigned char)destinationHost.size());
  packet.insert(packet.end(),destinationHost.begin(),destinationHost.end());
  uint16_t port=htons((uint16_t)addressPort(destination));
  const auto* bytes=(const unsigned char*)&port;packet.insert(packet.end(),bytes,bytes+2);
 }else if(destination->sa_family==AF_INET){
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
 if(settings.type!=1){
  logUdpRouteOnce(fd,destination,"direct_bypass","http_connect_carries_tcp_only");
  return realSendTo(fd,data,size,flags,destination,destinationLength);
 }
 sockaddr_storage relay{};socklen_t relayLength=0;
 if(!relayForSocket(fd,&relay,&relayLength)){
  int error=errno;
  logUdpRouteOnce(fd,destination,"failure","socks5_relay_setup",error);
  errno=error;
  return -1;
 }
 const std::string destinationHost=remoteDnsNameForAddress(destination);
 std::vector<unsigned char> packet=socksUdpPacket(data,size,destination,destinationHost);
 if(packet.empty()){
  int error=errno;
  logUdpRouteOnce(fd,destination,"failure","socks5_packet_encode",error);
  errno=error;
  return -1;
 }
 ssize_t sent=realSendTo(fd,packet.data(),packet.size(),flags,(sockaddr*)&relay,relayLength);
 if(sent<0){
  int error=errno;
  logUdpRouteOnce(fd,destination,"failure","sendto_socks5_relay",error);
  errno=error;
  return -1;
 }
 {
  std::lock_guard<std::mutex> lock(udpLock);proxiedUdpFds.insert(fd);
 }
 logUdpRouteOnce(fd,destination,"proxied",destinationHost.empty()?"socks5_udp_relay":"socks5_udp_domain");
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
  bool decoded=false;
  {
   RemoteDnsBypassScope bypass;
   decoded=decodeSocksUdpPacket(raw.data(),(size_t)received,&source,&sourceLength,&offset);
  }
  if(!decoded)return -1;
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
 int type=socketType(fd);
 if(type==SOCK_STREAM){
  int family=socketFamily(fd);ssize_t result=realSendTo(fd,data,size,flags,address,length);int savedErrno=errno;int error=result<0?savedErrno:0;
  if(settings.type==1&&(family==AF_INET||family==AF_INET6))recordTcpSocketIo(fd,"send",size,result,error);
  errno=savedErrno;
  return result;
 }
 if(type!=SOCK_DGRAM||!internetSocket(fd))return realSendTo(fd,data,size,flags,address,length);
 if(settings.type==2){
  sockaddr_storage peer{};socklen_t peerLength=0;
  const sockaddr* destination=address?address:connectedUdpPeer(fd,&peer,&peerLength);
  if(internetAddress(destination)&&!loopbackAddress(destination))
   logUdpRouteOnce(fd,destination,"direct_bypass","http_connect_carries_tcp_only");
  return realSendTo(fd,data,size,flags,address,length);
 }
 if(socketType(fd)==SOCK_DGRAM&&address==nullptr){UdpPeer peer{};if(!getUdpPeer(fd,&peer))return realSendTo(fd,data,size,flags,address,length);}
 return sendUdp(fd,data,size,flags,address,length);
}
static ssize_t proxySend(int fd,const void* data,size_t size,int flags){
 int type=socketType(fd);
 if(type==SOCK_STREAM){
  int family=socketFamily(fd);
  ssize_t result=realSend(fd,data,size,flags);int savedErrno=errno;int error=result<0?savedErrno:0;
  if(settings.type==1&&(family==AF_INET||family==AF_INET6))recordTcpSocketIo(fd,"send",size,result,error);
  errno=savedErrno;
  return result;
 }
 if(type!=SOCK_DGRAM||!internetSocket(fd))return realSend(fd,data,size,flags);
 if(settings.type==2){
  sockaddr_storage peer{};socklen_t peerLength=0;
  const sockaddr* destination=connectedUdpPeer(fd,&peer,&peerLength);
  if(internetAddress(destination)&&!loopbackAddress(destination))
   logUdpRouteOnce(fd,destination,"direct_bypass","http_connect_carries_tcp_only");
  return realSend(fd,data,size,flags);
 }
 if(socketType(fd)==SOCK_DGRAM){UdpPeer peer{};if(getUdpPeer(fd,&peer))return sendUdp(fd,data,size,flags,nullptr,0);}
 return realSend(fd,data,size,flags);
}
static ssize_t proxyRecvFrom(int fd,void* data,size_t size,int flags,sockaddr* address,socklen_t* length){
 if(socketType(fd)==SOCK_STREAM){
  int family=socketFamily(fd);ssize_t result=realRecvFrom(fd,data,size,flags,address,length);int savedErrno=errno;int error=result<0?savedErrno:0;
  if(settings.type==1&&(family==AF_INET||family==AF_INET6))recordTcpSocketIo(fd,"receive",size,result,error);
  errno=savedErrno;
  return result;
 }
 if(socketType(fd)==SOCK_DGRAM&&internetSocket(fd))return receiveUdp(fd,data,size,flags,address,length);
 return realRecvFrom(fd,data,size,flags,address,length);
}
static ssize_t proxyRecv(int fd,void* data,size_t size,int flags){
 int type=socketType(fd);
 if(type==SOCK_STREAM){
  int family=socketFamily(fd);
  ssize_t result=realRecv(fd,data,size,flags);int savedErrno=errno;int error=result<0?savedErrno:0;
  if(settings.type==1&&(family==AF_INET||family==AF_INET6))recordTcpSocketIo(fd,"receive",size,result,error);
  errno=savedErrno;
  return result;
 }
 if(socketType(fd)==SOCK_DGRAM&&internetSocket(fd))return receiveUdp(fd,data,size,flags,nullptr,nullptr);
 return realRecv(fd,data,size,flags);
}

static ssize_t proxySendMsg(int fd,const msghdr* message,int flags){
 int type=socketType(fd);
 if(type==SOCK_STREAM){
  int family=socketFamily(fd);ssize_t result=realSendMsg(fd,message,flags);int savedErrno=errno;int error=result<0?savedErrno:0;
  if(settings.type==1&&(family==AF_INET||family==AF_INET6))recordTcpSocketIo(fd,"send",0,result,error);
  errno=savedErrno;
  return result;
 }
 if(type!=SOCK_DGRAM||!internetSocket(fd))return realSendMsg(fd,message,flags);
 if(settings.type==2){
  sockaddr_storage peer{};socklen_t peerLength=0;
  const sockaddr* destination=message->msg_name?(const sockaddr*)message->msg_name:connectedUdpPeer(fd,&peer,&peerLength);
  if(internetAddress(destination)&&!loopbackAddress(destination))
   logUdpRouteOnce(fd,destination,"direct_bypass","http_connect_carries_tcp_only");
  return realSendMsg(fd,message,flags);
 }
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
 int type=socketType(fd);
 if(type==SOCK_STREAM){
  int family=socketFamily(fd);ssize_t result=realRecvMsg(fd,message,flags);int savedErrno=errno;int error=result<0?savedErrno:0;
  if(settings.type==1&&(family==AF_INET||family==AF_INET6))recordTcpSocketIo(fd,"receive",0,result,error);
  errno=savedErrno;
  return result;
 }
 if(type!=SOCK_DGRAM||!internetSocket(fd))return realRecvMsg(fd,message,flags);
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
 if(socketType(fd)!=SOCK_DGRAM||!internetSocket(fd))return realSendMmsg(fd,messages,count,flags);
 unsigned int sent=0;
 for(;sent<count;sent++){
  ssize_t result=proxySendMsg(fd,&messages[sent].msg_hdr,flags);
  if(result<0)return sent?(int)sent:-1;
  messages[sent].msg_len=(unsigned int)result;
 }
 return (int)sent;
}
static int proxyRecvMmsg(int fd,mmsghdr* messages,unsigned int count,int flags,timespec* timeout){
 if(socketType(fd)!=SOCK_DGRAM||!internetSocket(fd))return realRecvMmsg(fd,messages,count,flags,timeout);
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
 // IPv6 destinations are tunneled through an IPv4 proxy endpoint using an
 // IPv4-mapped IPv6 address. Make TCP sockets dual-stack too, before the app
 // can bind/connect them, so that this proxy connection is accepted by the
 // kernel. UDP needs the same setting when relaying to an IPv4 SOCKS endpoint.
 if(fd>=0&&family==AF_INET6){int dualStack=0;setsockopt(fd,IPPROTO_IPV6,IPV6_V6ONLY,&dualStack,sizeof(dualStack));}
 return fd;
}
static int proxyClose(int fd){
 logTcpSocketSummaryOnClose(fd);
 int family=socketFamily(fd);
 if(family==AF_INET||family==AF_INET6||family<0){
  std::lock_guard<std::mutex> lock(udpLock);udpPeers.erase(fd);proxiedUdpFds.erase(fd);loggedUdpPorts.erase(fd);
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
  if(settings.type!=1){
   logUdpRouteOnce(fd,address,"direct_bypass","http_connect_carries_tcp_only");
   return realConnect(fd,address,length);
  }
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
 const std::string destinationHost=remoteDnsNameForAddress(address);
 const uint64_t routeId=nextTcpRouteId.fetch_add(1);
 const auto routeStarted=std::chrono::steady_clock::now();
 logTcpRoute(routeId,"attempt",address,"proxy_endpoint_connect",0,0,0,0,
             destinationHost.empty()?nullptr:&destinationHost);
 sockaddr_storage endpoint{};socklen_t endpointSize;
 if(address->sa_family==AF_INET){
  auto p=(sockaddr_in*)&endpoint;p->sin_family=AF_INET;p->sin_port=htons(settings.port);inet_pton(AF_INET,settings.host.c_str(),&p->sin_addr);endpointSize=sizeof(*p);
 }else{
  auto p=(sockaddr_in6*)&endpoint;p->sin6_family=AF_INET6;p->sin6_port=htons(settings.port);inet_pton(AF_INET6,("::ffff:"+settings.host).c_str(),&p->sin6_addr);endpointSize=sizeof(*p);
 }
 const char* failedStage="prepare_socket";
 long long endpointConnectMs=0;
 long long handshakeMs=0;
 int flags=fcntl(fd,F_GETFL,0);if(flags<0){int error=errno;logTcpRoute(routeId,"failure",address,failedStage,error,elapsedMilliseconds(routeStarted),endpointConnectMs,handshakeMs,destinationHost.empty()?nullptr:&destinationHost);errno=error;return -1;}
 if(fcntl(fd,F_SETFL,flags|O_NONBLOCK)<0){int error=errno;logTcpRoute(routeId,"failure",address,failedStage,error,elapsedMilliseconds(routeStarted),endpointConnectMs,handshakeMs,destinationHost.empty()?nullptr:&destinationHost);errno=error;return -1;}
 failedStage="proxy_endpoint_connect";
 const auto endpointConnectStarted=std::chrono::steady_clock::now();
 int r=realConnect(fd,(sockaddr*)&endpoint,endpointSize);
 bool ok=r==0;
 if(r<0&&errno==EISCONN){fcntl(fd,F_SETFL,flags);logTcpRoute(routeId,"failure",address,"proxy_endpoint_connect_eisconn",EISCONN,elapsedMilliseconds(routeStarted),elapsedMilliseconds(endpointConnectStarted),handshakeMs,destinationHost.empty()?nullptr:&destinationHost);errno=EISCONN;return -1;}
 if(r<0&&errno==EINPROGRESS){
  pollfd item{fd,POLLOUT,0};int result=poll(&item,1,PROXY_CONNECT_TIMEOUT_MS);
  if(result>0){int error=0;socklen_t size=sizeof(error);ok=getsockopt(fd,SOL_SOCKET,SO_ERROR,&error,&size)==0&&error==0;if(!ok)errno=error?error:ECONNREFUSED;}
  else errno=result==0?ETIMEDOUT:errno;
 }
 endpointConnectMs=elapsedMilliseconds(endpointConnectStarted);
 std::string handshakeFailureStage="socks_handshake";
 if(ok){
  const auto handshakeStarted=std::chrono::steady_clock::now();
  ok=CloneProxy::handshake(fd,address,settings,PROXY_HANDSHAKE_TIMEOUT_MS,nullptr,
                           destinationHost.empty()?nullptr:&destinationHost,&handshakeFailureStage);
  handshakeMs=elapsedMilliseconds(handshakeStarted);
  if(!ok)failedStage=handshakeFailureStage.c_str();
  else failedStage="complete";
 }
 int failure=errno;fcntl(fd,F_SETFL,flags);
 long long elapsedMs=elapsedMilliseconds(routeStarted);
 if(!ok){shutdown(fd,SHUT_RDWR);logTcpRoute(routeId,"failure",address,failedStage,failure,elapsedMs,endpointConnectMs,handshakeMs,destinationHost.empty()?nullptr:&destinationHost);errno=failure;return -1;}
 registerTcpSocketTrace(fd,routeId);
 logTcpRoute(routeId,"tunneled",address,"complete",0,elapsedMs,endpointConnectMs,handshakeMs,destinationHost.empty()?nullptr:&destinationHost);
 return 0;
}

static jboolean proxyConfigureFailure(const char* stage,int error,
                                      const std::chrono::steady_clock::time_point& started){
 __android_log_print(ANDROID_LOG_ERROR,PROXY_LOG_TAG,
                     "event=proxy_configure result=failure stage=%s error=%d elapsed_ms=%lld endpoint_or_credentials_logged=false",
                     stage,error,elapsedMilliseconds(started));
 return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_top_niunaijun_blackbox_core_NativeCore_configureProxy(JNIEnv* env,jclass,jint type,jstring host,jint port,jbyteArray user,jbyteArray password,jboolean udpRelayEnabled){
 std::lock_guard<std::mutex> lock(configLock);
 const auto started=std::chrono::steady_clock::now();
 __android_log_print(ANDROID_LOG_INFO,PROXY_LOG_TAG,
                     "event=proxy_configure result=begin type=%d endpoint_or_credentials_logged=false",type);
 if(configured)return proxyConfigureFailure("already_configured",EALREADY,started);
 if(type==0){
  __android_log_print(ANDROID_LOG_INFO,PROXY_LOG_TAG,
                      "event=proxy_configure result=success stage=complete transport=direct elapsed_ms=%lld",
                      elapsedMilliseconds(started));
  return JNI_TRUE;
 }
 if(type!=1&&type!=2)return proxyConfigureFailure("proxy_type_validation",EINVAL,started);
 // SOCKS5 always relays UDP/QUIC; keep the JNI parameter for binary/API compatibility.
 (void)udpRelayEnabled;
 if(host==nullptr||user==nullptr||password==nullptr)return proxyConfigureFailure("jni_input_validation",EINVAL,started);
 const char* text=env->GetStringUTFChars(host,nullptr);
 if(text==nullptr)return proxyConfigureFailure("proxy_host_read",ENOMEM,started);
 settings.host=text;env->ReleaseStringUTFChars(host,text);
 proxyEndpointName=settings.host;
 settings.type=type;settings.port=port;
 settings.user.resize(env->GetArrayLength(user));settings.password.resize(env->GetArrayLength(password));
 if(!settings.user.empty())env->GetByteArrayRegion(user,0,settings.user.size(),(jbyte*)&settings.user[0]);
 if(!settings.password.empty())env->GetByteArrayRegion(password,0,settings.password.size(),(jbyte*)&settings.password[0]);
 in_addr ip{};
 if(port<1||port>65535||settings.user.size()>255||settings.password.size()>255)
  return proxyConfigureFailure("proxy_input_validation",EINVAL,started);
 const auto dnsStarted=std::chrono::steady_clock::now();
 if(!resolveProxyIPv4Bounded(settings.host,&ip,PROXY_ENDPOINT_DNS_TIMEOUT_MS))
  return proxyConfigureFailure("proxy_endpoint_dns",errno,dnsStarted);
 char numericHost[INET_ADDRSTRLEN];
 if(inet_ntop(AF_INET,&ip,numericHost,sizeof(numericHost))==nullptr)
  return proxyConfigureFailure("proxy_endpoint_address",errno,started);
 settings.host=numericHost;
 __android_log_print(ANDROID_LOG_INFO,PROXY_LOG_TAG,
                     "event=proxy_configure_stage result=success stage=proxy_endpoint_dns elapsed_ms=%lld",
                     elapsedMilliseconds(dnsStarted));
 void* connectAddress=dlsym(RTLD_DEFAULT,"connect");
 if(!connectAddress)return proxyConfigureFailure("resolve_connect_symbol",ENOENT,started);
 if(DobbyHook(connectAddress,(void*)proxyConnect,(void**)&realConnect)!=0)return proxyConfigureFailure("hook_connect",EIO,started);
 void* socketAddress=dlsym(RTLD_DEFAULT,"socket");
 if(!socketAddress)return proxyConfigureFailure("resolve_socket_symbol",ENOENT,started);
 if(DobbyHook(socketAddress,(void*)proxySocket,(void**)&realSocket)!=0)return proxyConfigureFailure("hook_socket",EIO,started);
 // HTTP CONNECT only changes TCP connection setup. Leave send/close and all
 // UDP entry points untouched so local framework IPC never enters proxy hooks.
 if(type==2){
  configured=true;
  __android_log_print(ANDROID_LOG_INFO,PROXY_LOG_TAG,
                      "event=proxy_configure result=success stage=complete transport=http_connect_tcp udp_hooks=disabled elapsed_ms=%lld endpoint_or_credentials_logged=false",
                      elapsedMilliseconds(started));
  return JNI_TRUE;
 }
 void* closeAddress=dlsym(RTLD_DEFAULT,"close");void* sendAddress=dlsym(RTLD_DEFAULT,"send");
 void* sendTo=dlsym(RTLD_DEFAULT,"sendto");void* sendMsg=dlsym(RTLD_DEFAULT,"sendmsg");
 void* sendMmsg=dlsym(RTLD_DEFAULT,"sendmmsg");
 if(!closeAddress||!sendAddress||!sendTo||!sendMsg||!sendMmsg)return proxyConfigureFailure("resolve_udp_send_symbols",ENOENT,started);
 if(DobbyHook(closeAddress,(void*)proxyClose,(void**)&realClose)!=0)return proxyConfigureFailure("hook_close",EIO,started);
 if(DobbyHook(sendAddress,(void*)proxySend,(void**)&realSend)!=0)return proxyConfigureFailure("hook_send",EIO,started);
 void* recvAddress=dlsym(RTLD_DEFAULT,"recv");void* recvFrom=dlsym(RTLD_DEFAULT,"recvfrom");
 void* recvMsg=dlsym(RTLD_DEFAULT,"recvmsg");void* recvMmsg=dlsym(RTLD_DEFAULT,"recvmmsg");
 if(!recvAddress||!recvFrom||!recvMsg||!recvMmsg)return proxyConfigureFailure("resolve_udp_receive_symbols",ENOENT,started);
 if(DobbyHook(recvAddress,(void*)proxyRecv,(void**)&realRecv)!=0)return proxyConfigureFailure("hook_recv",EIO,started);
 if(DobbyHook(sendTo,(void*)proxySendTo,(void**)&realSendTo)!=0)return proxyConfigureFailure("hook_sendto",EIO,started);
 if(DobbyHook(recvFrom,(void*)proxyRecvFrom,(void**)&realRecvFrom)!=0)return proxyConfigureFailure("hook_recvfrom",EIO,started);
 if(DobbyHook(sendMsg,(void*)proxySendMsg,(void**)&realSendMsg)!=0)return proxyConfigureFailure("hook_sendmsg",EIO,started);
 if(DobbyHook(recvMsg,(void*)proxyRecvMsg,(void**)&realRecvMsg)!=0)return proxyConfigureFailure("hook_recvmsg",EIO,started);
 if(DobbyHook(sendMmsg,(void*)proxySendMmsg,(void**)&realSendMmsg)!=0)return proxyConfigureFailure("hook_sendmmsg",EIO,started);
 if(DobbyHook(recvMmsg,(void*)proxyRecvMmsg,(void**)&realRecvMmsg)!=0)return proxyConfigureFailure("hook_recvmmsg",EIO,started);
 void* androidGetAddrInfoAddress=dlsym(RTLD_DEFAULT,"android_getaddrinfofornet");
 void* getAddrInfoAddress=dlsym(RTLD_DEFAULT,"getaddrinfo");
 if(!androidGetAddrInfoAddress||!getAddrInfoAddress)return proxyConfigureFailure("resolve_dns_symbols",ENOENT,started);
 if(DobbyHook(androidGetAddrInfoAddress,(void*)proxyAndroidGetAddrInfoForNet,(void**)&realAndroidGetAddrInfoForNet)!=0)
  return proxyConfigureFailure("hook_android_getaddrinfo",EIO,started);
 if(DobbyHook(getAddrInfoAddress,(void*)proxyGetAddrInfo,(void**)&realGetAddrInfo)!=0)
  return proxyConfigureFailure("hook_getaddrinfo",EIO,started);
 configured=true;
 __android_log_print(ANDROID_LOG_INFO,PROXY_LOG_TAG,
                     "event=proxy_configure result=success stage=complete transport=socks5_tcp_udp remote_dns=socks5_domain udp_observation=enabled elapsed_ms=%lld endpoint_or_credentials_logged=false",
                     elapsedMilliseconds(started));
 return JNI_TRUE;
}

static std::atomic<uint64_t> nextProxyProbeId{1};
static jint proxyProbeFailure(jint stage,int error){
 int code=error>0&&error<1000?error:EIO;
 return stage*1000+code;
}
static jint logProxyProbeFailure(uint64_t probeId,const std::chrono::steady_clock::time_point& started,
                                jint stageCode,const char* stageName,int error,long long stageElapsedMs){
 jint result=proxyProbeFailure(stageCode,error);
 __android_log_print(ANDROID_LOG_WARN,"ClonyCloneProbe",
                     "event=probe_stage probe_id=%llu result=failure stage=%s error=%d elapsed_ms=%lld",
                     (unsigned long long)probeId,stageName,error,stageElapsedMs);
 __android_log_print(ANDROID_LOG_WARN,"ClonyCloneProbe",
                     "event=probe_complete probe_id=%llu result=failure failure_code=%d stage_code=%d elapsed_ms=%lld endpoint_or_credentials_logged=false",
                     (unsigned long long)probeId,result,stageCode,elapsedMilliseconds(started));
 return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_top_niunaijun_blackbox_core_NativeCore_probeProxy(JNIEnv* env,jclass,jint type,jstring host,jint port,jbyteArray user,jbyteArray password){
 const uint64_t probeId=nextProxyProbeId.fetch_add(1);
 const auto probeStarted=std::chrono::steady_clock::now();
 __android_log_print(ANDROID_LOG_INFO,"ClonyCloneProbe",
                     "event=probe_begin probe_id=%llu type=%d destination=ipv4_tls_endpoint port=443 tcp_timeout_ms=4000 udp_timeout_ms=4000 endpoint_or_credentials_logged=false",
                     (unsigned long long)probeId,type);
 CloneProxy::Config c;c.type=type;c.port=port;
 const char* text=env->GetStringUTFChars(host,nullptr);c.host=text;env->ReleaseStringUTFChars(host,text);
 c.user.resize(env->GetArrayLength(user));c.password.resize(env->GetArrayLength(password));
 if(!c.user.empty())env->GetByteArrayRegion(user,0,c.user.size(),(jbyte*)&c.user[0]);
 if(!c.password.empty())env->GetByteArrayRegion(password,0,c.password.size(),(jbyte*)&c.password[0]);
 if((type!=1&&type!=2)||port<1||port>65535)
  return logProxyProbeFailure(probeId,probeStarted,1,"input_validation",EINVAL,elapsedMilliseconds(probeStarted));
 sockaddr_in endpoint{};endpoint.sin_family=AF_INET;endpoint.sin_port=htons(port);
 const auto dnsStarted=std::chrono::steady_clock::now();
 if(!resolveProxyIPv4(c.host,&endpoint.sin_addr))
  return logProxyProbeFailure(probeId,probeStarted,1,"proxy_endpoint_dns",errno,elapsedMilliseconds(dnsStarted));
 __android_log_print(ANDROID_LOG_INFO,"ClonyCloneProbe",
                     "event=probe_stage probe_id=%llu result=success stage=proxy_endpoint_dns elapsed_ms=%lld",
                     (unsigned long long)probeId,elapsedMilliseconds(dnsStarted));
 char numericHost[INET_ADDRSTRLEN];
 if(inet_ntop(AF_INET,&endpoint.sin_addr,numericHost,sizeof(numericHost))==nullptr)
  return logProxyProbeFailure(probeId,probeStarted,1,"proxy_endpoint_address",errno,elapsedMilliseconds(dnsStarted));
 c.host=numericHost;
 const auto connectStarted=std::chrono::steady_clock::now();
 int fd=socket(AF_INET,SOCK_STREAM|SOCK_CLOEXEC|SOCK_NONBLOCK,0);
 if(fd<0)return logProxyProbeFailure(probeId,probeStarted,1,"create_tcp_socket",errno,elapsedMilliseconds(connectStarted));
 int result=connect(fd,(sockaddr*)&endpoint,sizeof(endpoint));bool ok=result==0;
 if(result<0&&errno==EINPROGRESS){
  pollfd p{fd,POLLOUT,0};int ready=poll(&p,1,4000);int error=0;socklen_t size=sizeof(error);
  if(ready>0){ok=getsockopt(fd,SOL_SOCKET,SO_ERROR,&error,&size)==0&&error==0;if(!ok)errno=error?error:ECONNREFUSED;}
  else {ok=false;errno=ready==0?ETIMEDOUT:errno;}
 }
 long long connectElapsedMs=elapsedMilliseconds(connectStarted);
 if(!ok){int error=errno;close(fd);return logProxyProbeFailure(probeId,probeStarted,1,"proxy_endpoint_connect",error,connectElapsedMs);}
 __android_log_print(ANDROID_LOG_INFO,"ClonyCloneProbe",
                     "event=probe_stage probe_id=%llu result=success stage=proxy_endpoint_connect elapsed_ms=%lld",
                     (unsigned long long)probeId,connectElapsedMs);
 sockaddr_in target{};target.sin_family=AF_INET;target.sin_port=htons(443);inet_pton(AF_INET,"1.1.1.1",&target.sin_addr);
 int httpStatus=0;std::string handshakeStage;
 const auto handshakeStarted=std::chrono::steady_clock::now();
 ok=CloneProxy::handshake(fd,(sockaddr*)&target,c,4000,&httpStatus,nullptr,&handshakeStage);
 long long handshakeElapsedMs=elapsedMilliseconds(handshakeStarted);
 int error=ok?0:(httpStatus>=100&&httpStatus<=599?httpStatus:errno);close(fd);
 if(error!=0)return logProxyProbeFailure(probeId,probeStarted,2,handshakeStage.c_str(),error,handshakeElapsedMs);
 __android_log_print(ANDROID_LOG_INFO,"ClonyCloneProbe",
                     "event=probe_stage probe_id=%llu result=success stage=proxy_tcp_tunnel elapsed_ms=%lld",
                     (unsigned long long)probeId,handshakeElapsedMs);
 if(type==1){
  const auto udpConnectStarted=std::chrono::steady_clock::now();
  int udpFd=socket(AF_INET,SOCK_STREAM|SOCK_CLOEXEC|SOCK_NONBLOCK,0);
  if(udpFd<0)return logProxyProbeFailure(probeId,probeStarted,3,"create_udp_control_socket",errno,elapsedMilliseconds(udpConnectStarted));
  int connected=connect(udpFd,(sockaddr*)&endpoint,sizeof(endpoint))==0;
  if(!connected&&errno==EINPROGRESS){
   pollfd item{udpFd,POLLOUT,0};int ready=poll(&item,1,4000);int connectError=0;socklen_t size=sizeof(connectError);
   if(ready>0){connected=getsockopt(udpFd,SOL_SOCKET,SO_ERROR,&connectError,&size)==0&&connectError==0;if(!connected)errno=connectError?connectError:ECONNREFUSED;}
   else {errno=ready==0?ETIMEDOUT:errno;}
  }
  long long udpConnectElapsedMs=elapsedMilliseconds(udpConnectStarted);
  if(!connected){int connectError=errno;close(udpFd);return logProxyProbeFailure(probeId,probeStarted,3,"udp_control_proxy_connect",connectError,udpConnectElapsedMs);}
  __android_log_print(ANDROID_LOG_INFO,"ClonyCloneProbe",
                      "event=probe_stage probe_id=%llu result=success stage=udp_control_proxy_connect elapsed_ms=%lld",
                      (unsigned long long)probeId,udpConnectElapsedMs);
  sockaddr_storage relay{};socklen_t relayLength=0;
  std::string udpFailedStage;
  const auto udpAssociateStarted=std::chrono::steady_clock::now();
  bool associated=CloneProxy::udpAssociate(udpFd,c,&relay,&relayLength,4000,&udpFailedStage);
  error=associated?0:errno;close(udpFd);
  long long udpAssociateElapsedMs=elapsedMilliseconds(udpAssociateStarted);
  if(error!=0)return logProxyProbeFailure(probeId,probeStarted,4,udpFailedStage.c_str(),error,udpAssociateElapsedMs);
  __android_log_print(ANDROID_LOG_INFO,"ClonyCloneProbe",
                      "event=probe_stage probe_id=%llu result=success stage=socks5_udp_associate elapsed_ms=%lld",
                      (unsigned long long)probeId,udpAssociateElapsedMs);
 }
 __android_log_print(ANDROID_LOG_INFO,"ClonyCloneProbe",
                     "event=probe_complete probe_id=%llu result=success elapsed_ms=%lld endpoint_or_credentials_logged=false",
                     (unsigned long long)probeId,elapsedMilliseconds(probeStarted));
 return 0;
}
