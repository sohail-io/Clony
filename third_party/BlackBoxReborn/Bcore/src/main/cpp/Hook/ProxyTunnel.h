#pragma once
#include <string>
#include <sys/socket.h>
namespace CloneProxy {
struct Config { int type=0; std::string host,user,password; int port=0; };
bool handshake(int fd,const sockaddr* destination,const Config& config,int timeoutMs,int* httpStatus=nullptr);
bool udpAssociate(int fd,const Config& config,sockaddr_storage* relay,socklen_t* relaySize,int timeoutMs);
}
