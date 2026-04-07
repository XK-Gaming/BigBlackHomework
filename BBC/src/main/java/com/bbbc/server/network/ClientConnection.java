package com.bbbc.server.network;

import com.bbbc.common.message.ApiMessage;

public interface ClientConnection {
    void send(ApiMessage message);
}
