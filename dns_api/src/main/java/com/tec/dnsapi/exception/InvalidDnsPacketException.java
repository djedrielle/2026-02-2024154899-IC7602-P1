package com.tec.dnsapi.exception;

public class InvalidDnsPacketException extends RuntimeException {
  public InvalidDnsPacketException(String message) {
    super(message);
  }
  public InvalidDnsPacketException(String message, Throwable cause) {
    super(message, cause);
  }
}