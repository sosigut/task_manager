package org.example.service;

import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LiveKitService {

    @Value("${livekit.api.key}")
    private String apiKey;

    @Value("${livekit.api.secret}")
    private String apiSecret;

    public String createToken(String roomName, String identity, String name){

        AccessToken token = new AccessToken(apiKey, apiSecret);

        token.setIdentity(identity);
        token.setName(name);

        token.addGrants(new RoomJoin(true), new RoomName(roomName));

        return token.toJwt();

    }

}
