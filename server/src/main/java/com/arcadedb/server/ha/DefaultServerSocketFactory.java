/*
 * Copyright (c) 2018 - Arcade Analytics LTD (https://arcadeanalytics.com)
 */

package com.arcadedb.server.ha;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

class DefaultServerSocketFactory extends OServerSocketFactory {

  DefaultServerSocketFactory() {
  }

  public ServerSocket createServerSocket() throws IOException {
    return new ServerSocket();
  }

  @Override
  public ServerSocket createServerSocket(int port) throws IOException {
    return new ServerSocket(port);
  }

  @Override
  public ServerSocket createServerSocket(int port, int backlog) throws IOException {
    return new ServerSocket(port, backlog);
  }

  @Override
  public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
    return new ServerSocket(port, backlog, ifAddress);
  }
}
