package org.apache.guacamole.rest.user;


import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserTokenAndTunnel {

    private String tunnel;
    private String token;

    public UserTokenAndTunnel(String token, String tunnel) {
        this.token = token;
        this.tunnel = tunnel;
    }

    public UserTokenAndTunnel() {
        this.tunnel = "";
        this.token = "";
    }

    public String getToken() {
        return token;
    }

    public String getTunnel() {
        return tunnel;
    }
}
