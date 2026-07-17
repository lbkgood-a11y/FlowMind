package com.triobase.service.openapi.service;
import org.springframework.stereotype.Component;
import java.net.InetAddress;
@Component
public class NetworkPolicyMatcher {
 public boolean matches(String address,String cidr){try{if(cidr==null||!cidr.contains("/"))return InetAddress.getByName(cidr).equals(InetAddress.getByName(address));String[] parts=cidr.split("/",2);byte[] network=InetAddress.getByName(parts[0]).getAddress();byte[] candidate=InetAddress.getByName(address).getAddress();if(network.length!=candidate.length)return false;int prefix=Integer.parseInt(parts[1]);if(prefix<0||prefix>network.length*8)return false;for(int i=0;i<network.length;i++){int bits=Math.min(8,prefix-i*8);if(bits<=0)break;int mask=0xff<<(8-bits)&0xff;if((network[i]&mask)!=(candidate[i]&mask))return false;}return true;}catch(Exception e){return false;}}
}
